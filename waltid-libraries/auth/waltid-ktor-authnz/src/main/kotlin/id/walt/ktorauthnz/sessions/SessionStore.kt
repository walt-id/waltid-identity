package id.walt.ktorauthnz.sessions

interface SessionStore {

    val name: String

    /** Store the provided AuthSession */
    suspend fun storeSession(session: AuthSession)

    /** Resolve an AuthSession by its id */
    suspend fun resolveSessionById(sessionId: String): AuthSession

    /** Delete an AuthSession by its id */
    suspend fun dropSession(id: String)

    /** Drop all AuthSessions of an account id */
    suspend fun invalidateAllSessionsForAccount(accountId: String)

    suspend fun storeExternalIdMapping(namespace: String, externalId: String, internalSessionId: String)
    suspend fun resolveExternalIdMapping(namespace: String, externalId: String): String?
    suspend fun dropExternalIdMappingByExternal(namespace: String, externalId: String)
    suspend fun dropExternalIdMappingByInternal(namespace: String, internalSessionId: String)

}
