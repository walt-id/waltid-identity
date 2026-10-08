package id.walt.ktorauthnz.enrollment

import id.walt.errors.StatusException
import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.accounts.identifiers.AccountIdentifierManager
import id.walt.ktorauthnz.auth.getAuthenticatedAccount
import id.walt.ktorauthnz.events.AuthnzEvent
import id.walt.ktorauthnz.events.AuthnzEvents
import id.walt.ktorauthnz.exceptions.AuthSessionStateException
import id.walt.ktorauthnz.flows.AuthFlow
import id.walt.ktorauthnz.methods.authenticationMethodRoutes
import id.walt.ktorauthnz.sessions.AuthSessionInformation
import id.walt.ktorauthnz.sessions.SessionManager
import id.walt.ktorauthnz.tenants.authnzTenant
import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlin.time.Duration

/** An identity that logs into the account, as listed by `GET identities`. */
@Serializable
data class AccountIdentity(val type: String, val value: String)

/**
 * Adding and removing identities of the authenticated account - place inside `authenticate { }`:
 *
 * - `POST link/{method}`: starts linking with one of [flows] (each a flow of one method, e.g. the company OIDC
 *   provider or the directory). The answer is a session, continued at the login routes as usual
 *   (`{sessionId}/{method}`, e.g. `{sessionId}/oidc/auth`); once the method authenticated, its identity is the
 *   account's, and no new login is issued. An identity of another account is refused (409).
 * - `GET identities`: the account's identities (needs `lookupAccountIdentifiers` in the account store)
 * - `DELETE identities?type=...&value=...`: removes one - not the last one, so the account can still log in
 *
 * With [maxLoginAge], both changes need a login at most that old (see [requireRecentLogin]).
 */
fun Route.identityLinking(flows: List<AuthFlow>, maxLoginAge: Duration? = null) = authenticationMethodRoutes {
    require(flows.map { it.method }.toSet().size == flows.size) { "One linking flow per method" }

    post("link/{method}", {
        tags("Authentication")
        summary = "Start linking an identity to the account"
        request { pathParameter<String>("method") }
        response { HttpStatusCode.OK to { body<AuthSessionInformation>() } }
    }) {
        maxLoginAge?.let { call.requireRecentLogin(it) }
        val method = call.parameters["method"]!!
        val flow = flows.firstOrNull { it.method == method } ?: throw AuthSessionStateException("Identities of $method cannot be linked")
        val session = SessionManager.newSession(flow, tenant = call.authnzTenant, linkToAccount = call.getAuthenticatedAccount())
        call.respond(session.toInformation(revealTokenToClient = false))
    }

    get("identities", {
        tags("Authentication")
        summary = "List the account's identities"
        response { HttpStatusCode.OK to { body<List<AccountIdentity>>() } }
    }) {
        call.respond(identitiesOf(call.getAuthenticatedAccount()).map { AccountIdentity(it.accountIdentifierName, it.toDataString()) })
    }

    delete("identities", {
        tags("Authentication")
        summary = "Remove an identity of the account"
        request { queryParameter<String>("type"); queryParameter<String>("value") }
        response { HttpStatusCode.NoContent to { } }
    }) {
        maxLoginAge?.let { call.requireRecentLogin(it) }
        val accountId = call.getAuthenticatedAccount()
        val type = requireNotNull(call.request.queryParameters["type"]) { "Missing type" }
        val value = requireNotNull(call.request.queryParameters["value"]) { "Missing value" }
        val identifier = AccountIdentifierManager.getAccountIdentifier(type, value)
        val identities = identitiesOf(accountId)
        if (identifier !in identities) throw StatusException(404, "The account has no such identity")
        if (identities.size == 1) throw AuthSessionStateException("The last identity of an account cannot be removed")

        KtorAuthnzManager.accountStore.removeAccountIdentifierFromAccount(identifier)
        AuthnzEvents.emit(AuthnzEvent.MethodRemoved(accountId, identifier.accountIdentifierName))
        call.respond(HttpStatusCode.NoContent)
    }
}

private suspend fun identitiesOf(accountId: String) = KtorAuthnzManager.accountStore.lookupAccountIdentifiers(accountId)
    ?: throw StatusException(501, "This account store cannot list the identities of an account")
