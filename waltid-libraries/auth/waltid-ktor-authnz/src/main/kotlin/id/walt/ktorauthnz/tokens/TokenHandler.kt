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


    suspend fun resolveTokenToSession(token: String): AuthSession {
        val session = KtorAuthnzManager.sessionStore.findSessionById(getTokenSessionId(token))
        if (session == null || session.token != token) throw InvalidTokenException("Token has no active session")
        return session
    }

}
