package id.walt.authkit.sessions

interface SessionStore {

    suspend fun store(session: AuthSession)

    fun resolveSessionId(sessionId: String): AuthSession
    fun dropSession(id: String)

}