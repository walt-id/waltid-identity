package id.walt.ktorauthnz.sessions

interface SessionStore {

    val name: String

    suspend fun store(session: AuthSession)

    suspend fun resolveSessionId(sessionId: String): AuthSession
    suspend fun dropSession(id: String)

}
