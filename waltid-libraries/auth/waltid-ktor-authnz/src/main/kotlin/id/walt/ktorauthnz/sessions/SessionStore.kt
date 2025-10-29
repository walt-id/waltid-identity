package id.walt.ktorauthnz.sessions

interface SessionStore {

    val name: String

    /** Store the provided AuthSession */
    suspend fun storeSession(session: AuthSession)

    /** Resolve an AuthSession by its id */
    suspend fun resolveSessionById(sessionId: String): AuthSession

    /** Delete an AuthSession by its id */
    suspend fun dropSession(id: String)

}
