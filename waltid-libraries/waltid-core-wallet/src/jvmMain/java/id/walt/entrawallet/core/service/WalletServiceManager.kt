package id.walt.entrawallet.core.service

import id.walt.entrawallet.core.service.exchange.IssuanceServiceExternalSignatures
import id.walt.webwallet.usecase.claim.ExternalSignatureClaimStrategy
import id.walt.webwallet.usecase.exchange.MatchPresentationDefinitionCredentialsUseCase
import id.walt.webwallet.usecase.exchange.NoMatchPresentationDefinitionCredentialsUseCase
import id.walt.webwallet.usecase.exchange.PresentationDefinitionFilterParser
import id.walt.webwallet.usecase.exchange.strategies.DescriptorNoMatchPresentationDefinitionMatchStrategy
import id.walt.webwallet.usecase.exchange.strategies.DescriptorPresentationDefinitionMatchStrategy
import id.walt.webwallet.usecase.exchange.strategies.FilterNoMatchPresentationDefinitionMatchStrategy
import id.walt.webwallet.usecase.exchange.strategies.FilterPresentationDefinitionMatchStrategy
import id.walt.webwallet.utils.WalletHttpClients.getHttpClient
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
object WalletServiceManager {

    private val logger = KotlinLogging.logger { }

    private val walletServices = ConcurrentHashMap<Pair<Uuid, Uuid>, WalletService>()
    val httpClient = getHttpClient()
//    private val credentialTypeSeeker = DefaultCredentialTypeSeeker()
    private val filterParser = PresentationDefinitionFilterParser()
    /*private val statusListCredentialFetchFactory = StatusListCredentialFetchFactory(
        defaultStrategy = DefaultStatusListCredentialFetchStrategy(httpClient),
        entraStrategy = EntraStatusListCredentialFetchStrategy(
            serviceEndpointProvider = EntraServiceEndpointProvider(httpClient),
            didResolverService = DidResolverService(),
            jwsDecoder = JwsDecoder()
        )
    )*/
  /*  private val credentialStatusServiceFactory = CredentialStatusServiceFactory(
        statusListService = StatusListCredentialStatusService(
            credentialFetchFactory = statusListCredentialFetchFactory,
            credentialValidator = CredentialValidator(),
            bitStringValueParser = BitStringValueParser(),
        ),
    )
    private val issuerNameResolutionService by lazy { DefaultNameResolutionService(httpClient, trustConfig.issuersRecord) }
    private val verifierNameResolutionService by lazy { DefaultNameResolutionService(httpClient, trustConfig.verifiersRecord) }*/
    /*private val issuerNameResolutionUseCase by lazy {
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
    }*/
   /* val issuerUseCase by lazy { IssuerUseCaseImpl(service = IssuersService, http = httpClient) }
    val oidcConfig by lazy { ConfigManager.getConfig<OidcConfiguration>() }
    val issuerTrustValidationService by lazy { DefaultTrustValidationService(httpClient, trustConfig.issuersRecord) }
    val verifierTrustValidationService by lazy { DefaultTrustValidationService(httpClient, trustConfig.verifiersRecord) }*/
    val matchPresentationDefinitionCredentialsUseCase = MatchPresentationDefinitionCredentialsUseCase(
        FilterPresentationDefinitionMatchStrategy(filterParser),
        DescriptorPresentationDefinitionMatchStrategy()
    )
    val unmatchedPresentationDefinitionCredentialsUseCase = NoMatchPresentationDefinitionCredentialsUseCase(
        FilterNoMatchPresentationDefinitionMatchStrategy(filterParser),
        DescriptorNoMatchPresentationDefinitionMatchStrategy(),
    )
    val externalSignatureClaimStrategy by lazy {
        ExternalSignatureClaimStrategy(
            issuanceServiceExternalSignatures = IssuanceServiceExternalSignatures,
        )
    }
    /*val credentialStatusUseCase = CredentialStatusUseCase(
        credentialStatusServiceFactory = credentialStatusServiceFactory,
    )*/

    fun getWalletService() = SSIKit2WalletService(
        http = httpClient
    )
}
