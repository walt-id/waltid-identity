package id.walt.ktorauthnz.tokens

import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.exceptions.InvalidTokenException
import id.walt.ktorauthnz.sessions.AuthSession

interface TokenHandler {

    val name: String

    suspend fun generateToken(session: AuthSession): String
    suspend fun validateToken(token: String): Boolean
    suspend fun getTokenSessionId(token: String): String
    suspend fun getTokenAccountId(token: String): String
    suspend fun dropToken(token: String)

    /** When and with which methods the token's login completed, or null if unknown. */
    suspend fun getTokenLogin(token: String): TokenLogin? =
        runCatching { resolveTokenToSession(token) }.getOrNull()?.let { TokenLogin(it.authenticatedAt, it.completedMethods) }

    /** The tenant the token's session was opened for, or null. */
    suspend fun getTokenTenant(token: String): String? = resolveTokenToSession(token).tenant


    suspend fun resolveTokenToSession(token: String): AuthSession {
        val session = KtorAuthnzManager.sessionStore.findSessionById(getTokenSessionId(token))
        if (session == null || (session.token != null && session.token != token)) throw InvalidTokenException("Token has no active session")
        return session
    }

}

/** When a login completed ([authenticatedAt]), and the methods of its steps ([methods], e.g. `email`, `totp`). */
data class TokenLogin(val authenticatedAt: kotlin.time.Instant?, val methods: List<String>)
