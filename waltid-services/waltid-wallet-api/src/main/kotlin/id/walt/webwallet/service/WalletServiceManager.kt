@file:OptIn(ExperimentalTime::class)

package id.walt.webwallet.service

import id.walt.commons.config.ConfigManager
import id.walt.commons.featureflag.FeatureManager.whenFeature
import id.walt.definitionparser.PresentationDefinitionParser
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.webwallet.FeatureCatalog
import id.walt.webwallet.config.OidcConfiguration
import id.walt.webwallet.config.TrustConfig
import id.walt.webwallet.db.models.AccountWalletMappings
import id.walt.webwallet.db.models.AccountWalletPermissions
import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.db.models.Wallets
import id.walt.webwallet.seeker.DefaultCredentialTypeSeeker
import id.walt.webwallet.service.account.AccountsService
import id.walt.webwallet.service.cache.EntityNameResolutionCacheService
import id.walt.webwallet.service.category.CategoryServiceImpl
import id.walt.webwallet.service.credentials.CredentialFilterObject
import id.walt.webwallet.service.credentials.CredentialStatusServiceFactory
import id.walt.webwallet.service.credentials.CredentialValidator
import id.walt.webwallet.service.credentials.CredentialsService
import id.walt.webwallet.service.credentials.status.StatusListCredentialStatusService
import id.walt.webwallet.service.credentials.status.fetch.DefaultStatusListCredentialFetchStrategy
import id.walt.webwallet.service.credentials.status.fetch.EntraStatusListCredentialFetchStrategy
import id.walt.webwallet.service.credentials.status.fetch.StatusListCredentialFetchFactory
import id.walt.webwallet.service.dids.DidResolverService
import id.walt.webwallet.service.dids.DidsService
import id.walt.webwallet.service.endpoint.EntraServiceEndpointProvider
import id.walt.webwallet.service.entity.DefaultNameResolutionService
import id.walt.webwallet.service.events.EventService
import id.walt.webwallet.service.exchange.IssuanceService
import id.walt.webwallet.service.exchange.IssuanceServiceExternalSignatures
import id.walt.webwallet.service.issuers.IssuersService
import id.walt.webwallet.service.notifications.NotificationService
import id.walt.webwallet.service.settings.SettingsService
import id.walt.webwallet.service.trust.DefaultTrustValidationService
import id.walt.webwallet.usecase.claim.ExplicitClaimStrategy
import id.walt.webwallet.usecase.claim.ExternalSignatureClaimStrategy
import id.walt.webwallet.usecase.claim.SilentClaimStrategy
import id.walt.webwallet.usecase.credential.CredentialStatusUseCase
import id.walt.webwallet.usecase.entity.EntityNameResolutionUseCase
import id.walt.webwallet.usecase.event.EventFilterUseCase
import id.walt.webwallet.usecase.event.EventLogUseCase
import id.walt.webwallet.usecase.exchange.MatchPresentationDefinitionCredentialsUseCase
import id.walt.webwallet.usecase.exchange.NoMatchPresentationDefinitionCredentialsUseCase
import id.walt.webwallet.usecase.exchange.PresentationDefinitionFilterParser
import id.walt.webwallet.usecase.exchange.strategies.DescriptorNoMatchPresentationDefinitionMatchStrategy
import id.walt.webwallet.usecase.exchange.strategies.DescriptorPresentationDefinitionMatchStrategy
import id.walt.webwallet.usecase.exchange.strategies.FilterNoMatchPresentationDefinitionMatchStrategy
import id.walt.webwallet.usecase.exchange.strategies.FilterPresentationDefinitionMatchStrategy
import id.walt.webwallet.usecase.issuer.IssuerUseCaseImpl
import id.walt.webwallet.usecase.notification.NotificationDataFormatter
import id.walt.webwallet.usecase.notification.NotificationDispatchUseCase
import id.walt.webwallet.usecase.notification.NotificationFilterUseCase
import id.walt.webwallet.usecase.notification.NotificationUseCase
import id.walt.webwallet.utils.WalletHttpClients.getHttpClient
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaInstant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid
import id.walt.webwallet.performance.Stopwatch

@OptIn(ExperimentalUuidApi::class)
object WalletServiceManager {

    private val logger = KotlinLogging.logger { }

    private val walletServices = ConcurrentHashMap<Pair<Uuid, Uuid>, WalletService>()
    private val categoryService = CategoryServiceImpl
    private val settingsService = SettingsService
    private val httpClient = getHttpClient()
    private val trustConfig by lazy {
        { ConfigManager.getConfig<TrustConfig>() } whenFeature FeatureCatalog.silentExchange
    }
    private val credentialService = CredentialsService()
    private val credentialTypeSeeker = DefaultCredentialTypeSeeker()
    private val eventService = EventService()
    private val filterParser = PresentationDefinitionFilterParser()
    private val jwsDecoder = JwsDecoder()
    private val statusListCredentialFetchFactory = StatusListCredentialFetchFactory(
        defaultStrategy = DefaultStatusListCredentialFetchStrategy(httpClient, jwsDecoder),
        entraStrategy = EntraStatusListCredentialFetchStrategy(
            serviceEndpointProvider = EntraServiceEndpointProvider(httpClient),
            didResolverService = DidResolverService(),
            jwsDecoder = jwsDecoder
        )
    )
    private val credentialStatusServiceFactory = CredentialStatusServiceFactory(
        statusListService = StatusListCredentialStatusService(
            credentialFetchFactory = statusListCredentialFetchFactory,
            credentialValidator = CredentialValidator(),
            bitStringValueParser = BitStringValueParser(),
        ),
    )
    private val issuerNameResolutionService by lazy { DefaultNameResolutionService(httpClient, trustConfig?.issuersRecord) }
    private val verifierNameResolutionService by lazy { DefaultNameResolutionService(httpClient, trustConfig?.verifiersRecord) }
    private val issuerNameResolutionUseCase by lazy {
        EntityNameResolutionUseCase(
            EntityNameResolutionCacheService,
            issuerNameResolutionService
        )
    }
    private val verifierNameResolutionUseCase by lazy {
        EntityNameResolutionUseCase(
            EntityNameResolutionCacheService,
            verifierNameResolutionService
        )
    }
    private val notificationDataFormatter by lazy { NotificationDataFormatter(issuerNameResolutionUseCase) }
    private val notificationDispatchUseCase by lazy { NotificationDispatchUseCase(httpClient, notificationDataFormatter) }
    val issuerUseCase by lazy { IssuerUseCaseImpl(service = IssuersService, http = httpClient) }
    val eventUseCase by lazy { EventLogUseCase(eventService) }
    val eventFilterUseCase by lazy { EventFilterUseCase(eventService, issuerNameResolutionUseCase, verifierNameResolutionUseCase) }
    val oidcConfig by lazy { ConfigManager.getConfig<OidcConfiguration>() }
    val issuerTrustValidationService by lazy { DefaultTrustValidationService(httpClient, trustConfig?.issuersRecord) }
    val verifierTrustValidationService by lazy { DefaultTrustValidationService(httpClient, trustConfig?.verifiersRecord) }
    val notificationUseCase by lazy { NotificationUseCase(NotificationService, notificationDataFormatter) }
    val notificationFilterUseCase by lazy { NotificationFilterUseCase(NotificationService, credentialService, notificationDataFormatter) }
    val matchPresentationDefinitionCredentialsUseCase = MatchPresentationDefinitionCredentialsUseCase(
        credentialService,
        FilterPresentationDefinitionMatchStrategy(filterParser),
        DescriptorPresentationDefinitionMatchStrategy()
    )
    val unmatchedPresentationDefinitionCredentialsUseCase = NoMatchPresentationDefinitionCredentialsUseCase(
        credentialService,
        FilterNoMatchPresentationDefinitionMatchStrategy(filterParser),
        DescriptorNoMatchPresentationDefinitionMatchStrategy(),
    )
    val silentClaimStrategy by lazy {
        SilentClaimStrategy(
            walletProvider = SSIKit2WalletService::getCredentialWallet,
            issuanceService = IssuanceService,
            credentialService = credentialService,
            issuerTrustValidationService = issuerTrustValidationService,
            accountService = AccountsService,
            didService = DidsService,
            issuerUseCase = issuerUseCase,
            eventUseCase = eventUseCase,
            notificationUseCase = notificationUseCase,
            notificationDispatchUseCase = notificationDispatchUseCase,
            credentialTypeSeeker = credentialTypeSeeker,
        )
    }
    val explicitClaimStrategy = ExplicitClaimStrategy(
        issuanceService = IssuanceService,
        credentialService = credentialService,
        eventUseCase = eventUseCase,
    )
    val externalSignatureClaimStrategy by lazy {
        ExternalSignatureClaimStrategy(
            issuanceServiceExternalSignatures = IssuanceServiceExternalSignatures,
            credentialService = credentialService,
            eventUseCase = eventUseCase,
        )
    }
    val credentialStatusUseCase = CredentialStatusUseCase(
        credentialService = credentialService,
        credentialStatusServiceFactory = credentialStatusServiceFactory,
    )

    fun getWalletService(tenant: String, account: Uuid, wallet: Uuid): WalletService =
        walletServices.getOrPut(Pair(account, wallet)) {
            SSIKit2WalletService(
                tenant = tenant,
                accountId = account,
                walletId = wallet,
                categoryService = categoryService,
                settingsService = settingsService,
                eventUseCase = eventUseCase,
                http = httpClient
            )
        }

    fun createWallet(tenant: String, forAccount: Uuid): Uuid {
        val accountName = AccountsService.get(forAccount).name ?: "wallet name not defined"

        // TODO: remove testing code / lock behind dev-mode
        if (accountName.contains("multi-wallet")) {
            val second = Wallets.insert {
                it[name] = "ABC Company wallet"
                it[createdOn] = Clock.System.now().toJavaInstant()
            }[Wallets.id].value

            AccountWalletMappings.insert {
                it[AccountWalletMappings.tenant] = tenant
                it[accountId] = forAccount
                it[wallet] = second
                it[permissions] = AccountWalletPermissions.READ_ONLY
                it[addedOn] = Clock.System.now().toJavaInstant()
            }
        }

        val walletId = Wallets.insert {
            it[name] = "Wallet of $accountName"
            it[createdOn] = Clock.System.now().toJavaInstant()
        }[Wallets.id].value

        logger.debug { "Creating wallet mapping: $forAccount -> $walletId" }

        AccountWalletMappings.insert {
            it[AccountWalletMappings.tenant] = tenant
            it[accountId] = forAccount
            it[wallet] = walletId
            it[permissions] = AccountWalletPermissions.ADMINISTRATE
            it[addedOn] = Clock.System.now().toJavaInstant()
        }

        return walletId.toKotlinUuid()
    }

    @Deprecated(
        replaceWith = ReplaceWith(
            "AccountsService.getAccountWalletMappings(account)", "id.walt.service.account.AccountsService"
        ), message = "deprecated"
    )
    fun listWallets(tenant: String, account: Uuid): List<Uuid> =
        AccountWalletMappings.innerJoin(Wallets)
            .selectAll().where { (AccountWalletMappings.tenant eq tenant) and (AccountWalletMappings.accountId eq account) }.map {
                it[Wallets.id].value.toKotlinUuid()
            }

    suspend fun matchCredentialsForPresentationDefinition(
        walletId: Uuid,
        presentationDefinition: PresentationDefinition
    ): List<WalletCredential> {
        val pd = Json.decodeFromJsonElement<id.walt.definitionparser.PresentationDefinition>(presentationDefinition.toJSON())
        val matches = credentialService.list(walletId, CredentialFilterObject.default).filter { cred ->
            val timerId = Uuid.random().toString()
            Stopwatch.startTimer(timerId)
            val fullDoc = WalletCredential.parseFullDocument(cred.document, cred.disclosures, cred.id, cred.format)
            Stopwatch.addTiming(timerId, "parseCredential")
            val result = fullDoc != null &&
                    pd.inputDescriptors.any { inputDesc ->
                        PresentationDefinitionParser.matchCredentialsForInputDescriptor(flowOf(fullDoc), inputDesc).toList().isNotEmpty()
                    }
            Stopwatch.addTiming(timerId, "matched")
            result
        }
        return matches
    }
}
