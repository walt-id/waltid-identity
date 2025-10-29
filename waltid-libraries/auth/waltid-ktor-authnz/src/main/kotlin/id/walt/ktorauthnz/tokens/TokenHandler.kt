package id.walt.ktorauthnz.tokens

import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.sessions.AuthSession

interface TokenHandler {

    val name: String

    suspend fun generateToken(session: AuthSession): String
    suspend fun validateToken(token: String): Boolean
    suspend fun getTokenSessionId(token: String): String
    suspend fun getTokenAccountId(token: String): String
    suspend fun dropToken(token: String)


    suspend fun resolveTokenToSession(token: String) = getTokenSessionId(token)
        .let { sessionId -> KtorAuthnzManager.sessionStore.resolveSessionById(sessionId) }
        .also { session -> check(token == session.token) { "Token was not mapped to correct session" } }

}
