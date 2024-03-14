package id.walt.webwallet.service.account

import id.walt.webwallet.config.ConfigManager
import id.walt.webwallet.config.LoginMethodsConfig
import id.walt.webwallet.db.models.*
import id.walt.webwallet.service.WalletServiceManager
import id.walt.webwallet.service.events.AccountEventData
import id.walt.webwallet.service.events.EventService
import id.walt.webwallet.service.events.EventType
import id.walt.webwallet.service.issuers.IssuersService
import id.walt.webwallet.usecase.event.EventUseCase
import id.walt.webwallet.web.controllers.generateToken
import id.walt.webwallet.web.model.*
import kotlinx.datetime.toKotlinInstant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.uuid.UUID
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

object AccountsService {

    private val eventUseCase = EventUseCase(EventService())
    fun registerAuthenticationMethods() {
        val loginMethods = ConfigManager.getConfig<LoginMethodsConfig>().enabledLoginMethods

    }

    suspend fun register(tenant: String = "", request: AccountRequest): Result<RegistrationResult> = when (request) {
        is EmailAccountRequest -> EmailAccountStrategy.register(tenant, request)
        is AddressAccountRequest -> Web3WalletAccountStrategy.register(tenant, request)
        is OidcAccountRequest -> OidcAccountStrategy.register(tenant, request)
        is KeycloakAccountRequest -> KeycloakAccountStrategy.register(tenant, request)
        is OidcUniqueSubjectRequest -> OidcUniqueSubjectStrategy.register(tenant, request)

    }.onSuccess { registrationResult ->
        val registeredUserId = registrationResult.id

        val createdInitialWalletId = transaction {
            WalletServiceManager.createWallet(tenant, registeredUserId)
        }.also { walletId ->
            //TODO: inject
            IssuersService.add(
                wallet = walletId,
                name = "walt.id",
                description = "walt.id issuer portal",
                uiEndpoint = "https://portal.walt.id/credentials?ids=",
                configurationEndpoint = "https://issuer.portal.walt.id/.well-known/openid-credential-issuer"
            )
        }

        val walletService = WalletServiceManager.getWalletService(tenant, registeredUserId, createdInitialWalletId)
        eventUseCase.log(
            action = EventType.Account.Create,
            originator = "wallet",
            tenant = tenant,
            accountId = registeredUserId,
            walletId = createdInitialWalletId,
            data = AccountEventData(accountId = request.name)
        )

            // Add default data:
            val createdDid =
                walletService.createDid("key", mapOf("alias" to JsonPrimitive("Onboarding")))
            walletService.setDefault(createdDid)
          }
          .onFailure { throw IllegalStateException("Could not register user: ${it.message}", it) }

    suspend fun authenticate(tenant: String, request: AccountRequest): Result<AuthenticationResult> = runCatching {
        when (request) {
            is EmailAccountRequest -> EmailAccountStrategy.authenticate(tenant, request)
            is AddressAccountRequest -> Web3WalletAccountStrategy.authenticate(tenant, request)
            is OidcAccountRequest -> OidcAccountStrategy.authenticate(tenant, request)
            is OidcUniqueSubjectRequest -> OidcUniqueSubjectStrategy.authenticate(tenant, request)
            is KeycloakAccountRequest -> KeycloakAccountStrategy.authenticate(tenant, request)

        }
    }.fold(onSuccess = {
        eventUseCase.log(
            action = EventType.Account.Login,
            tenant = tenant,
            originator = "wallet",
            accountId = it.id,
            walletId = UUID.NIL,
            data = AccountEventData(accountId = it.username)
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
                          id = it[AccountWalletMappings.wallet].value,
                          name = it[Wallets.name],
                          createdOn = it[Wallets.createdOn].toKotlinInstant(),
                          addedOn = it[AccountWalletMappings.addedOn].toKotlinInstant(),
                          permission = it[AccountWalletMappings.permissions])
                    }
              })

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

  fun getNameFor(account: UUID) =
      Accounts.selectAll().where { Accounts.id eq account }.single()[Accounts.email]
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

data class AuthenticatedUser(val id: UUID, val username: String)
