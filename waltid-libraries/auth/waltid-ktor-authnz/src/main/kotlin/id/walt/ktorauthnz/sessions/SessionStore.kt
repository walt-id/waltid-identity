package id.walt.ktorauthnz.sessions

import id.walt.errors.StatusException

interface SessionStore {

    val name: String

    /** Store the provided AuthSession */
    suspend fun storeSession(session: AuthSession)

    /** Resolve an AuthSession by its id; throws if there is none */
    suspend fun resolveSessionById(sessionId: String): AuthSession

    /** The AuthSession with this id, or null if there is none (never created, expired, dropped) */
    suspend fun findSessionById(sessionId: String): AuthSession? =
        runCatching { resolveSessionById(sessionId) }.getOrNull()

    /** Delete an AuthSession by its id */
    suspend fun dropSession(id: String)

    /** Drop all AuthSessions of an account id */
    suspend fun invalidateAllSessionsForAccount(accountId: String)

    /**
     * The live sessions of the account (finished and unfinished), e.g. for a "where am I logged in" page. Optional: a
     * store that cannot list them answers 501 there.
     */
    suspend fun listSessionsForAccount(accountId: String): List<AuthSession> =
        throw StatusException(501, "This session store ($name) cannot list the sessions of an account")

    suspend fun storeExternalIdMapping(namespace: String, externalId: String, internalSessionId: String)
    suspend fun resolveExternalIdMapping(namespace: String, externalId: String): String?
    suspend fun dropExternalIdMappingByExternal(namespace: String, externalId: String)
    suspend fun dropExternalIdMappingByInternal(namespace: String, internalSessionId: String)

}
