package id.walt.authkit.tokens

import id.walt.authkit.AuthKitManager
import id.walt.authkit.sessions.AuthSession

interface TokenHandler {

    suspend fun generateToken(session: AuthSession): String
    suspend fun validateToken(token: String): Boolean
    suspend fun getTokenSessionId(token: String): String
    suspend fun getTokenAccountId(token: String): String
    suspend fun dropToken(token: String)


    suspend fun resolveTokenToSession(token: String) = getTokenSessionId(token)
        .let { sessionId -> AuthKitManager.sessionStore.resolveSessionId(sessionId) }
        .also { session -> check(token == session.token) { "Token was not mapped to correct session" } }

}
