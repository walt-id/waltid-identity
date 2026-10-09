package id.walt.ktorauthnz.tokens

import id.walt.ktorauthnz.tenants.authnzTenant
import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.events.AuthnzEvent
import id.walt.ktorauthnz.events.AuthnzEvents
import id.walt.ktorauthnz.exceptions.InvalidTokenException
import id.walt.ktorauthnz.sessions.AuthSession
import id.walt.ktorauthnz.sessions.AuthSessionInformation
import id.walt.ktorauthnz.sessions.SessionManager
import id.walt.ktorauthnz.sessions.SessionTokenCookieHandler
import io.github.smiley4.ktoropenapi.post
import io.klogging.logger
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.kotlincrypto.hash.sha2.SHA256
import java.security.SecureRandom
import kotlin.io.encoding.Base64
import kotlin.time.Clock

@Serializable
data class TokenRefreshRequest(@SerialName("refresh_token") val refreshToken: String)

object RefreshTokens {

    private val log = logger("RefreshTokens")
    private val store get() = KtorAuthnzManager.expiringStore

    private fun key(refreshToken: String) = "refresh-token:${SHA256().digest(refreshToken.toByteArray()).toHexString()}"

    private suspend fun issue(session: AuthSession, settings: RefreshTokenSettings) {
        val refreshToken = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(ByteArray(32).also { SecureRandom().nextBytes(it) })
        store.put(key(refreshToken), session.id, settings.refreshTokenLifetime)
        session.refreshToken = refreshToken
    }

    /** At the end of a login: the session lives as long as refreshing, its login token [RefreshTokenSettings.accessTokenLifetime]. */
    internal suspend fun startRefreshableSession(session: AuthSession, settings: RefreshTokenSettings) {
        val now = Clock.System.now()
        session.expiration = now + settings.refreshTokenLifetime
        session.tokenExpiration = now + settings.accessTokenLifetime
        issue(session, settings)
    }

    /**
     * Exchanges [refreshToken] for a new login token and refresh token (the old ones end). A refresh token presented a
     * second time ends its session: one of the two callers holds a stolen token. With [tenant] set, a refresh token of
     * another tenant's session is refused before it is used up.
     */
    suspend fun refresh(refreshToken: String, tenant: String? = null): AuthSession {
        val settings = KtorAuthnzManager.refreshTokens ?: throw InvalidTokenException("Refresh tokens are not enabled")
        val key = key(refreshToken)
        suspend fun reused(sessionId: String): Nothing {
            log.warn { "Refresh token reused for session $sessionId - ending the session" }
            SessionManager.findSessionById(sessionId)?.let { SessionManager.invalidateSession(it) }
            throw InvalidTokenException("Refresh token was already used")
        }
        store.get("$key:used")?.let { reused(it) }
        val sessionId = store.get(key) ?: throw InvalidTokenException("Invalid or expired refresh token")
        if (tenant != null && SessionManager.findSessionById(sessionId)?.tenant != tenant) {
            throw InvalidTokenException("Invalid or expired refresh token")
        }
        if (!store.putIfAbsent("$key:used", sessionId, settings.refreshTokenLifetime)) reused(sessionId)
        store.remove(key)

        val session = SessionManager.findSessionById(sessionId)?.takeIf { it.status.isSuccess() }
            ?: throw InvalidTokenException("The session of this refresh token ended")

        session.token?.let { KtorAuthnzManager.tokenHandler.dropToken(it) }
        session.tokenExpiration = Clock.System.now() + settings.accessTokenLifetime
        issue(session, settings)
        SessionManager.updateSession(session)
        session.token = KtorAuthnzManager.tokenHandler.generateToken(session)
        SessionManager.updateSession(session)

        AuthnzEvents.emit(AuthnzEvent.TokenRefreshed(session.id, session.accountId))
        return session
    }
}

/** `POST token/refresh {refresh_token}`: a new login token and refresh token, also set as the session cookie. */
fun Route.tokenRefresh(revealTokenToClient: Boolean = true) {
    post("token/refresh", {
        tags("Authentication")
        summary = "Refresh the login token"
        request { body<TokenRefreshRequest>() }
        response { HttpStatusCode.OK to { body<AuthSessionInformation>() } }
    }) {
        val session = RefreshTokens.refresh(call.receive<TokenRefreshRequest>().refreshToken, call.authnzTenant)
        SessionTokenCookieHandler.run { call.setCookie(session.token!!) }
        call.respond(session.toInformation(revealTokenToClient = revealTokenToClient).copy(refreshToken = session.refreshToken))
    }
}
