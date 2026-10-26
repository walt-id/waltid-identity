package id.walt.ktorauthnz.enrollment

import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.auth.KtorAuthnzPrincipal
import id.walt.ktorauthnz.auth.authnzPrincipal
import id.walt.ktorauthnz.auth.getAuthenticatedAccount
import id.walt.ktorauthnz.events.AuthnzEvent
import id.walt.ktorauthnz.events.AuthnzEvents
import id.walt.ktorauthnz.exceptions.AuthSessionNotFoundException
import id.walt.ktorauthnz.exceptions.InvalidTokenException
import id.walt.ktorauthnz.exceptions.ReauthenticationRequiredException
import id.walt.ktorauthnz.methods.authenticationMethodRoutes
import id.walt.ktorauthnz.sessions.SessionManager
import id.walt.ktorauthnz.sessions.SessionTokenCookieHandler
import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/** A session of the account, as listed by `GET sessions`. */
@Serializable
data class AccountSession(
    @SerialName("session_id") val sessionId: String,
    /** Whether this is the session of the request. */
    val current: Boolean,
    val status: String,
    @SerialName("created_at") val createdAt: Instant? = null,
    @SerialName("authenticated_at") val authenticatedAt: Instant? = null,
    val methods: List<String> = emptyList(),
    @SerialName("expires_at") val expiresAt: Instant? = null,
)

/**
 * Routes of the authenticated account - place inside `authenticate { }`:
 *
 * - `POST logout`: ends this session (and its token) and deletes the session cookie
 * - `GET sessions`: the account's sessions, e.g. for a "where you are logged in" page
 * - `DELETE sessions/{sessionId}`: ends one of them (only the account's own)
 * - `DELETE sessions`: ends all of them, this one included
 *
 * JWT login tokens are stateless: ending their session ends them only with `requireActiveSession` set on the
 * `JwtTokenHandler`, otherwise they stay valid until they expire.
 */
fun Route.accountRoutes() = authenticationMethodRoutes {
    post("logout", {
        tags("Authentication")
        summary = "Log out"
        response { HttpStatusCode.NoContent to { } }
    }) {
        val sessionId = call.authnzPrincipal()?.sessionId
        sessionId?.let { SessionManager.findSessionById(it) }?.let { session ->
            if (session.token != null) session.logout() else SessionManager.invalidateSession(session)
        }
        SessionTokenCookieHandler.run { call.deleteCookie() }
        call.respond(HttpStatusCode.NoContent)
    }

    get("sessions", {
        tags("Authentication")
        summary = "List the account's sessions"
        response { HttpStatusCode.OK to { body<List<AccountSession>>() } }
    }) {
        val accountId = call.getAuthenticatedAccount()
        val current = call.authnzPrincipal()?.sessionId
        val sessions = KtorAuthnzManager.sessionStore.listSessionsForAccount(accountId)
            .sortedByDescending { it.authenticatedAt ?: it.createdAt }
            .map {
                AccountSession(
                    sessionId = it.id, current = it.id == current, status = it.status.value,
                    createdAt = it.createdAt, authenticatedAt = it.authenticatedAt, methods = it.completedMethods,
                    expiresAt = it.expiration,
                )
            }
        call.respond(sessions)
    }

    delete("sessions/{sessionId}", {
        tags("Authentication")
        summary = "End one of the account's sessions"
        request { pathParameter<String>("sessionId") }
        response { HttpStatusCode.NoContent to { } }
    }) {
        val accountId = call.getAuthenticatedAccount()
        val sessionId = call.parameters["sessionId"]!!
        // Another account's session is not ended, and not revealed either.
        val session = SessionManager.findSessionById(sessionId)?.takeIf { it.accountId == accountId }
            ?: throw AuthSessionNotFoundException(sessionId)
        session.token?.let { KtorAuthnzManager.tokenHandler.dropToken(it) }
        SessionManager.invalidateSession(session)
        AuthnzEvents.emit(AuthnzEvent.LoggedOut(session.id, accountId))
        if (sessionId == call.authnzPrincipal()?.sessionId) SessionTokenCookieHandler.run { call.deleteCookie() }
        call.respond(HttpStatusCode.NoContent)
    }

    delete("sessions", {
        tags("Authentication")
        summary = "End all of the account's sessions"
        response { HttpStatusCode.NoContent to { } }
    }) {
        val accountId = call.getAuthenticatedAccount()
        KtorAuthnzManager.sessionStore.listSessionsForAccount(accountId).forEach { session ->
            session.token?.let { KtorAuthnzManager.tokenHandler.dropToken(it) }
        }
        SessionManager.invalidateAllSessionsForAccount(accountId)
        SessionTokenCookieHandler.run { call.deleteCookie() }
        call.respond(HttpStatusCode.NoContent)
    }
}

/**
 * Refuses (401, [ReauthenticationRequiredException]) unless the caller logged in within [maxAge] - and, if
 * [anyOfMethods] is given, with one of those methods (e.g. `totp`, `passkey`). For sensitive actions like changing the
 * email address or adding a login method; the client logs in again and retries.
 */
fun ApplicationCall.requireRecentLogin(maxAge: Duration, anyOfMethods: Set<String> = emptySet()) =
    checkRecentLogin(authnzPrincipal() ?: throw InvalidTokenException("Not authenticated"), maxAge, anyOfMethods, Clock.System.now())

internal fun checkRecentLogin(principal: KtorAuthnzPrincipal, maxAge: Duration, anyOfMethods: Set<String>, now: Instant) {
    val authenticatedAt = principal.authenticatedAt
        ?: throw ReauthenticationRequiredException("When this login happened is unknown; log in again")
    if (now - authenticatedAt > maxAge) throw ReauthenticationRequiredException("This needs a login within the last $maxAge; log in again")
    if (anyOfMethods.isNotEmpty() && principal.methods.none { it in anyOfMethods }) {
        throw ReauthenticationRequiredException("This needs a login with one of $anyOfMethods")
    }
}
