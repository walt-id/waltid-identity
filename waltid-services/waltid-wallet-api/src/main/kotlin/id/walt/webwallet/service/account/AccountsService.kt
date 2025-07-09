@file:OptIn(ExperimentalUuidApi::class)

package id.walt.webwallet.service.account


import id.walt.commons.config.ConfigManager
import id.walt.commons.featureflag.FeatureManager.whenFeatureSuspend
import id.walt.webwallet.FeatureCatalog
import id.walt.webwallet.config.RegistrationDefaultsConfig
import id.walt.webwallet.db.models.*
import id.walt.webwallet.service.WalletService
import id.walt.webwallet.service.WalletServiceManager
import id.walt.webwallet.service.account.x5c.X5CAccountStrategy
import id.walt.webwallet.service.events.AccountEventData
import id.walt.webwallet.service.events.EventType
import id.walt.webwallet.service.issuers.IssuerDataTransferObject
import id.walt.webwallet.web.model.*
import kotlinx.datetime.toKotlinInstant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

@OptIn(ExperimentalUuidApi::class)
object AccountsService {

    internal suspend fun initializeUserAccount(tenant: String = "", name: String?, registeredUserId: Uuid) {
        val createdInitialWalletId = transaction {
            WalletServiceManager.createWallet(tenant, registeredUserId)
        }

        val walletService = WalletServiceManager.getWalletService(tenant, registeredUserId, createdInitialWalletId)
        (suspend {
            ConfigManager.getConfig<RegistrationDefaultsConfig>()
        } whenFeatureSuspend (FeatureCatalog.registrationDefaultsFeature))?.run {
            tryAddDefaultData(walletService, this)
        }
        WalletServiceManager.eventUseCase.log(
            action = EventType.Account.Create,
            originator = "wallet",
            tenant = tenant,
            accountId = registeredUserId,
            walletId = createdInitialWalletId,
            data = AccountEventData(accountId = name)
        )
    }

    suspend fun register(tenant: String = "", request: AccountRequest): Result<RegistrationResult> {
        val result = when (request) {
            is EmailAccountRequest -> EmailAccountStrategy.register(tenant, request)
            // is AddressAccountRequest -> Web3WalletAccountStrategy.register(tenant, request)
            is OidcAccountRequest -> OidcAccountStrategy.register(tenant, request)
            is KeycloakAccountRequest -> KeycloakAccountStrategy.register(tenant, request)
            is OidcUniqueSubjectRequest -> OidcUniqueSubjectStrategy.register(tenant, request)
            is X5CAccountRequest -> X5CAccountStrategy.register(tenant, request)
            else -> throw NotImplementedError("unknown auth method")
        }
        return result.also { initializeUserAccount(tenant, request.name, it.getOrThrow().id) }
    }


    suspend fun authenticate(tenant: String, request: AccountRequest): Result<AuthenticatedUser> = runCatching {
        when (request) {
            is EmailAccountRequest -> EmailAccountStrategy.authenticate(tenant, request)
//            is AddressAccountRequest -> Web3WalletAccountStrategy.authenticate(tenant, request)
            is OidcAccountRequest -> OidcAccountStrategy.authenticate(tenant, request)
            is OidcUniqueSubjectRequest -> OidcUniqueSubjectStrategy.authenticate(tenant, request)
            is KeycloakAccountRequest -> KeycloakAccountStrategy.authenticate(tenant, request)
            is X5CAccountRequest -> X5CAccountStrategy.authenticate(tenant, request)
            else -> throw NotImplementedError("unknown auth method")

        }
    }.fold(onSuccess = {
        WalletServiceManager.eventUseCase.log(
            action = EventType.Account.Login,
            tenant = tenant,
            originator = "wallet",
            accountId = it.id,
            walletId = Uuid.NIL,
            data = AccountEventData(accountId = it.id.toString())
        )
        Result.success(it)
    }, onFailure = { Result.failure(it) })

    fun getAccountWalletMappings(tenant: String, account: Uuid) =
        AccountWalletListing(
            account,
            wallets =
                transaction {
                    AccountWalletMappings.innerJoin(Wallets)
                        .selectAll()
                        .where {
                            (AccountWalletMappings.tenant eq tenant) and
                                    (AccountWalletMappings.accountId eq account)
                        }
                        .map {
                            AccountWalletListing.WalletListing(
                                id = it[AccountWalletMappings.wallet].value.toKotlinUuid(),
                                name = it[Wallets.name],
                                createdOn = it[Wallets.createdOn].toKotlinInstant(),
                                addedOn = it[AccountWalletMappings.addedOn].toKotlinInstant(),
                                permission = it[AccountWalletMappings.permissions]
                            )
                        }
                })

    fun getAccountForWallet(wallet: Uuid) = transaction {
        AccountWalletMappings.select(AccountWalletMappings.accountId)
            .where { AccountWalletMappings.wallet eq wallet.toJavaUuid() }.firstOrNull()
            ?.let { it[AccountWalletMappings.accountId] }
    }

    fun hasAccountEmail(tenant: String, email: String) = transaction {
        Accounts.selectAll()
            .where { (Accounts.tenant eq tenant) and (Accounts.email eq email) }
            .count() > 0
    }

    fun hasAccountWeb3WalletAddress(address: String) = transaction {
        Accounts.innerJoin(Web3Wallets).selectAll().where { Web3Wallets.address eq address }.count() > 0
    }

    fun hasAccountOidcId(oidcId: String): Boolean = transaction {
        Accounts.crossJoin(OidcLogins) // TODO crossJoin
            .selectAll()
            .where {
                (Accounts.tenant eq OidcLogins.tenant) and
                        (Accounts.id eq OidcLogins.accountId) and
                        (OidcLogins.oidcId eq oidcId)
            }
            .count() > 0
    }

    fun getAccountByWeb3WalletAddress(address: String) = transaction {
        Accounts.innerJoin(Web3Wallets)
            .selectAll()
            .where { Web3Wallets.address eq address }
            .map { Account(it) }
    }

    fun getAccountByOidcId(oidcId: String) = transaction {
        Accounts.crossJoin(OidcLogins) // TODO crossJoin
            .selectAll()
            .where {
                (Accounts.tenant eq OidcLogins.tenant) and
                        (Accounts.id eq OidcLogins.accountId) and
                        (OidcLogins.oidcId eq oidcId)
            }
            .map { Account(it) }
            .firstOrNull()
    }

    //todo: unify with [getAccountByOidcId]
    fun getAccountByX5CId(tenant: String, x5cId: String) = transaction {
        Accounts.crossJoin(X5CLogins)
            .selectAll()
            .where {
                Accounts.tenant eq tenant and
                        (Accounts.tenant eq X5CLogins.tenant) and
                        (Accounts.id eq X5CLogins.accountId) and
                        (X5CLogins.x5cId eq x5cId)
            }
            .map { Account(it) }
            .firstOrNull()
    }

    fun get(account: Uuid) = transaction {
        Accounts.selectAll().where { Accounts.id eq account }.single().let {
            Account(it)
        }
    }

    private suspend fun tryAddDefaultData(
        walletService: WalletService,
        defaultGenerationConfig: RegistrationDefaultsConfig
    ) = runCatching {
        val createdKey = walletService.generateKey(defaultGenerationConfig.defaultKeyConfig)
        val createdDid =
            walletService.createDid(
                method = defaultGenerationConfig.didMethod,
                args = defaultGenerationConfig.didConfig!!.toMutableMap().apply {
                    put("keyId", JsonPrimitive(createdKey))
                    put("alias", JsonPrimitive("Onboarding"))
                })
        walletService.setDefault(createdDid)
        defaultGenerationConfig.defaultIssuerConfig?.run {
            WalletServiceManager.issuerUseCase.add(
                IssuerDataTransferObject(
                    wallet = walletService.walletId,
                    did = did,
                    description = description,
                    uiEndpoint = uiEndpoint,
                    configurationEndpoint = configurationEndpoint,
                    authorized = authorized
                )
            )
        }
    }
}

@Serializable
data class RegistrationResult(
    @Contextual
    val id: Uuid,
)

@Serializable
sealed class AuthenticatedUser {
    abstract val id: Uuid
}

@Serializable
data class UsernameAuthenticatedUser(
    @Contextual
    override val id: Uuid,
    val username: String,
) : AuthenticatedUser()

@Serializable
data class AddressAuthenticatedUser(
    @Contextual
    override val id: Uuid,
    val address: String,
) : AuthenticatedUser()

@Serializable
data class KeycloakAuthenticatedUser(
    @Contextual
    override val id: Uuid,
    val keycloakUserId: String,
) : AuthenticatedUser()

@Serializable
data class X5CAuthenticatedUser(
    @Contextual
    override val id: Uuid,
) : AuthenticatedUser()
