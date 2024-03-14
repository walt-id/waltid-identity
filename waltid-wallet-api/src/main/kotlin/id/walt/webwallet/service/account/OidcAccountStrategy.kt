package id.walt.webwallet.service.account

import id.walt.webwallet.db.models.Accounts
import id.walt.webwallet.db.models.OidcLogins
import id.walt.webwallet.utils.JwkUtils
import id.walt.webwallet.utils.JwkUtils.verifyToken
import id.walt.webwallet.web.model.OidcAccountRequest
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import kotlinx.uuid.UUID
import kotlinx.uuid.generateUUID
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction

object OidcAccountStrategy : PasswordlessAccountStrategy<OidcAccountRequest>() {
    override suspend fun register(tenant: String, request: OidcAccountRequest): Result<RegistrationResult> {
        val jwt = verifyToken(request.token)

        if (AccountsService.hasAccountOidcId(jwt.subject)) {
            throw IllegalArgumentException("Account already exists with OIDC id: ${request.token}")
        }

        val createdAccountId = transaction {
            val accountId = Accounts.insert {
                it[Accounts.tenant] = tenant
                it[id] = UUID.generateUUID()
                it[name] = jwt.getClaim("name").asString()
                it[email] = jwt.getClaim("email").asString()
                it[createdOn] = Clock.System.now().toJavaInstant()
            }[Accounts.id]

            OidcLogins.insert {
                it[OidcLogins.tenant] = tenant
                it[OidcLogins.accountId] = accountId
                it[oidcId] = jwt.subject
            }

            accountId
        }

        return Result.success(RegistrationResult(createdAccountId))
    }


    override suspend fun authenticate(tenant: String, request: OidcAccountRequest): AuthenticatedUser {
        val jwt = JwkUtils.verifyToken(request.token)

        val registeredUserId = if (AccountsService.hasAccountOidcId(jwt.subject)) {
            AccountsService.getAccountByOidcId(jwt.subject)!!.id
        } else {
            AccountsService.register(tenant, request).getOrThrow().id
        }
        return AuthenticatedUser(registeredUserId, jwt.subject)
    }
}
