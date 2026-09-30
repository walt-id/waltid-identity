package id.walt.ktorauthnz.tokens.ktorauthnztoken

import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.exceptions.InvalidTokenException
import id.walt.ktorauthnz.sessions.AuthSession
import id.walt.ktorauthnz.tokens.TokenHandler
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Opaque tokens, mapped to their session in [tokenStore]. A token is valid only while its session is: stored,
 * finished, not expired, and still holding this token - so logout, expiry and revoking an account's sessions end it.
 */
class KtorAuthNzTokenHandler : TokenHandler {

    override val name = "token-authnz"

    var tokenStore: KtorAuthnzTokenStore = InMemoryKtorAuthNzTokenStore()

    override suspend fun generateToken(session: AuthSession): String {
        val newToken = Uuid.random().toString()

        tokenStore.mapToken(newToken, session.id)

        return newToken
    }

    /** The live session of [token], or null; a token whose session ended is removed. */
    private suspend fun liveSession(token: String): AuthSession? {
        if (!tokenStore.validateToken(token)) return null
        val sessionId = runCatching { tokenStore.getTokenSessionId(token) }.getOrNull() ?: return null
        val session = KtorAuthnzManager.sessionStore.findSessionById(sessionId)
        val live = session != null && session.status.isSuccess() && session.token == token &&
                session.expiration?.let { Clock.System.now() < it } != false
        if (!live) {
            tokenStore.dropToken(token)
            return null
        }
        return session
    }

    override suspend fun validateToken(token: String): Boolean = liveSession(token) != null

    override suspend fun getTokenSessionId(token: String): String =
        liveSession(token)?.id ?: throw InvalidTokenException("Token is not valid")

    override suspend fun getTokenAccountId(token: String): String {
        val session = liveSession(token) ?: throw InvalidTokenException("Token is not valid")
        return session.accountId ?: throw InvalidTokenException("Token belongs to a session without account")
    }

    override suspend fun dropToken(token: String) =
        tokenStore.dropToken(token)
}
