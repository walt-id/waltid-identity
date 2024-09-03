package id.walt.authkit.sessions

import id.walt.authkit.AuthKitManager

interface TokenStore {

    /**
     * Return session id
     */
    fun mapToken(token: String, sessionId: String)

    fun getTokenSessionId(token: String): String

    fun validateToken(token: String): Boolean

    fun dropToken(token: String)



    fun resolveTokenToSession(token: String) = getTokenSessionId(token)
        .let { sessionId -> AuthKitManager.sessionStore.resolveSessionId(sessionId) }
        .also { session -> check(token == session.token) { "Token was not mapped to correct session" } }

}
