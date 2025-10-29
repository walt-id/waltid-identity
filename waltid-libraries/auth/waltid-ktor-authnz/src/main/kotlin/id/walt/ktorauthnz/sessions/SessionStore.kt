package id.walt.ktorauthnz.sessions

interface SessionStore {

    val name: String

    suspend fun store(session: AuthSession)

    /** Resolve an AuthSession by its id */
    suspend fun resolveSessionById(sessionId: String): AuthSession

    /** Delete an AuthSession by its id */
    suspend fun dropSession(id: String)

}
