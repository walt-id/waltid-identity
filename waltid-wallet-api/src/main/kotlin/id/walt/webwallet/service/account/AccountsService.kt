package id.walt.webwallet.service.account

import id.walt.webwallet.config.ConfigManager
import id.walt.webwallet.config.LoginMethodsConfig
import id.walt.webwallet.db.models.*
import id.walt.webwallet.service.WalletServiceManager
import id.walt.webwallet.service.events.AccountEventData
import id.walt.webwallet.service.events.Event
import id.walt.webwallet.service.events.EventService
import id.walt.webwallet.service.events.EventType
import id.walt.webwallet.service.settings.SettingsService
import id.walt.webwallet.service.settings.WalletSetting
import id.walt.webwallet.web.controllers.generateToken
import id.walt.webwallet.web.model.AccountRequest
import id.walt.webwallet.web.model.AddressAccountRequest
import id.walt.webwallet.web.model.EmailAccountRequest
import id.walt.webwallet.web.model.OidcAccountRequest
import kotlinx.datetime.toKotlinInstant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.uuid.UUID
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

object AccountsService {

    fun registerAuthenticationMethods() {
        val loginMethods = ConfigManager.getConfig<LoginMethodsConfig>().enabledLoginMethods

    }

    suspend fun register(tenant: String = "", request: AccountRequest): Result<RegistrationResult> = when (request) {
        is EmailAccountRequest -> EmailAccountStrategy.register(tenant, request)
        is AddressAccountRequest -> Web3WalletAccountStrategy.register(tenant, request)
        is OidcAccountRequest -> OidcAccountStrategy.register(tenant, request)
    }.onSuccess { registrationResult ->
        val registeredUserId = registrationResult.id

        val createdInitialWalletId = transaction {
            WalletServiceManager.createWallet(tenant, registeredUserId)
        }.also { walletId ->
            transaction {
                queryDefaultIssuer("walt.id")?.let { defaultIssuer ->
                    WalletIssuers.insert {
//                    it[WalletIssuers.tenant] = tenant
//                    it[accountId] = registeredUserId
                        it[issuer] = defaultIssuer
                        it[wallet] = walletId
                    }
                }
                SettingsService.set(walletId, WalletSetting.default)
            }
        }

        val walletService = WalletServiceManager.getWalletService(tenant, registeredUserId, createdInitialWalletId)
        EventService.add(
            Event(
                action = EventType.Account.Create,
                tenant = tenant,
                originator = "wallet",
                account = registeredUserId,
                wallet = createdInitialWalletId,
                data = AccountEventData(accountId = request.name)
            )
        )

        // Add default data:
        val createdDid = walletService.createDid("key", mapOf("alias" to JsonPrimitive("Onboarding")))
        walletService.setDefault(createdDid)
    }.onFailure {
        throw IllegalStateException("Could not register user: ${it.message}", it)
    }

    private fun queryDefaultIssuer(name: String) =
        Issuers.select(Issuers.name eq name).singleOrNull()?.let {
            it[Issuers.id]
        }?.value

    suspend fun authenticate(tenant: String, request: AccountRequest): Result<AuthenticationResult> = runCatching {
        when (request) {
            is EmailAccountRequest -> EmailAccountStrategy.authenticate(tenant, request)
            is AddressAccountRequest -> Web3WalletAccountStrategy.authenticate(tenant, request)
            is OidcAccountRequest -> OidcAccountStrategy.authenticate(tenant, request)
        }
    }.fold(onSuccess = {
        EventService.add(
            Event(
                action = EventType.Account.Login,
                tenant = tenant,
                originator = "wallet",
                account = it.id,
                wallet = null,
                data = AccountEventData(accountId = it.username)
            )
        )
        Result.success(
            AuthenticationResult(
                id = it.id,
                username = it.username,
                token = generateToken()
            )
        )
    },
        onFailure = { Result.failure(it) })

    fun getAccountWalletMappings(tenant: String, account: UUID) =
        AccountWalletListing(account, wallets =
        transaction {
            AccountWalletMappings.innerJoin(Wallets)
                .select { (AccountWalletMappings.tenant eq tenant) and (AccountWalletMappings.accountId eq account) }
                .map {
                    AccountWalletListing.WalletListing(
                        id = it[AccountWalletMappings.wallet].value,
                        name = it[Wallets.name],
                        createdOn = it[Wallets.createdOn].toKotlinInstant(),
                        addedOn = it[AccountWalletMappings.addedOn].toKotlinInstant(),
                        permission = it[AccountWalletMappings.permissions]
                    )
                }
        }
        )


    fun hasAccountEmail(tenant: String, email: String) =
        transaction { Accounts.select { (Accounts.tenant eq tenant) and (Accounts.email eq email) }.count() > 0 }

    fun hasAccountWeb3WalletAddress(address: String) = transaction {
        Accounts.innerJoin(Web3Wallets)
            .select { Web3Wallets.address eq address }
            .count() > 0
    }


    fun hasAccountOidcId(oidcId: String): Boolean = transaction {
        Accounts.crossJoin(OidcLogins) // TODO crossJoin
            .select { (Accounts.tenant eq OidcLogins.tenant) and (Accounts.id eq OidcLogins.accountId) and (OidcLogins.oidcId eq oidcId) }
            .count() > 0
    }


    fun getAccountByWeb3WalletAddress(address: String) =
        transaction {
            Accounts.innerJoin(Web3Wallets)
                .select { Web3Wallets.address eq address }
                .map { Account(it) }
        }

    fun getAccountByOidcId(oidcId: String) =
        transaction {
            Accounts.crossJoin(OidcLogins) // TODO crossJoin
                .select { (Accounts.tenant eq OidcLogins.tenant) and (Accounts.id eq OidcLogins.accountId) and (OidcLogins.oidcId eq oidcId) }
                .map { Account(it) }
                .firstOrNull()
        }

    fun getNameFor(account: UUID) = Accounts.select { Accounts.id eq account }.single()[Accounts.email]

}

@Serializable
data class AuthenticationResult(
    val id: UUID,
    val username: String,
    val token: String,
)

@Serializable
data class RegistrationResult(
    val id: UUID,
)

data class AuthenticatedUser(
    val id: UUID,
    val username: String
)

abstract class AccountStrategy<in T : AccountRequest>(id: String) {
    abstract suspend fun register(tenant: String, request: T): Result<RegistrationResult>
    abstract suspend fun authenticate(tenant: String, request: T): AuthenticatedUser
}
