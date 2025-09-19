@file:OptIn(ExperimentalTime::class)

package id.walt.webwallet.service.account

import id.walt.webwallet.db.models.Accounts
import id.walt.webwallet.db.models.OidcLogins
import id.walt.webwallet.utils.JwkUtils.verifyToken
import id.walt.webwallet.web.model.OidcAccountRequest
import kotlin.time.Clock
import kotlin.time.toJavaInstant


import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
object OidcAccountStrategy : PasswordlessAccountStrategy<OidcAccountRequest>() {
    override suspend fun register(tenant: String, request: OidcAccountRequest): Result<RegistrationResult> {
        val jwt = verifyToken(request.token)

        require(!AccountsService.hasAccountOidcId(jwt.subject)) { "Account already exists with OIDC id: ${request.token}" }

        val createdAccountId = transaction {
            val accountId = Accounts.insert {
                it[Accounts.tenant] = tenant
                it[id] = Uuid.random()
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
        val jwt = verifyToken(request.token)

        val registeredUserId = if (AccountsService.hasAccountOidcId(jwt.subject)) {
            AccountsService.getAccountByOidcId(jwt.subject)!!.id
        } else {
            AccountsService.register(tenant, request).getOrThrow().id
        }
        // TODO: change id to wallet-id (also in the frontend)
        return UsernameAuthenticatedUser(registeredUserId, jwt.subject)
    }
}
