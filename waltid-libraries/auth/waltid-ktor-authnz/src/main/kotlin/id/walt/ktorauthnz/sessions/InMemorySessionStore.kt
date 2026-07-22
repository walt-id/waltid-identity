package id.walt.ktorauthnz.sessions

import io.klogging.logger

class InMemorySessionStore : SessionStore {

    override val name = "in_memory"

    private val log = logger("InMemorySessionStore")
    private val lock = Any()

    /** Session ID -> Session */
    val sessions = HashMap<String, AuthSession>()

    /** Account ID -> [Session ID, ...] */
    val accountSessions = HashMap<String, List<String>>()

    /** External ID -> internal ID */
    val externalIdMappingForward = HashMap<String, String>()

    /** Internal ID -> external ID */
    val externalIdMappingBackward = HashMap<String, String>()

    override suspend fun resolveSessionById(sessionId: String): AuthSession = synchronized(lock) {
        sessions[sessionId]?.copy() ?: error("Unknown session id: $sessionId")
    }

    private fun removeSessionIdFromAccountSessions(sessionId: String, accountId: String) {
        val remaining = accountSessions[accountId].orEmpty() - sessionId
        if (remaining.isEmpty()) accountSessions.remove(accountId) else accountSessions[accountId] = remaining
    }

    override suspend fun dropSession(id: String) {
        log.debug { "Dropping session: $id" }
        synchronized(lock) {
            sessions[id]?.accountId?.let { removeSessionIdFromAccountSessions(id, it) }
            sessions.remove(id)
        }
    }

    override suspend fun storeSession(session: AuthSession) {
        log.debug { "Saving session: $session" }
        synchronized(lock) {
            val storedSession = session.copy()
            val previous = sessions.put(session.id, storedSession)
            previous?.accountId
                ?.takeIf { it != session.accountId }
                ?.let { removeSessionIdFromAccountSessions(session.id, it) }
            storedSession.accountId?.let { accountId ->
                accountSessions[accountId] = (accountSessions[accountId].orEmpty() + storedSession.id).distinct()
            }
        }
    }

    override suspend fun invalidateAllSessionsForAccount(accountId: String) {
        synchronized(lock) {
            accountSessions.remove(accountId)?.forEach { sessions.remove(it) }
        }
    }

    override suspend fun storeExternalIdMapping(namespace: String, externalId: String, internalSessionId: String) {
        synchronized(lock) {
            val forwardKey = "externalid-forward:$namespace:$externalId"
            val backwardKey = "externalid-backward:$namespace:$internalSessionId"
            externalIdMappingForward[forwardKey]?.let { previousInternal ->
                externalIdMappingBackward.remove("externalid-backward:$namespace:$previousInternal")
            }
            externalIdMappingBackward[backwardKey]?.let { previousExternal ->
                externalIdMappingForward.remove("externalid-forward:$namespace:$previousExternal")
            }
            externalIdMappingForward[forwardKey] = internalSessionId
            externalIdMappingBackward[backwardKey] = externalId
        }
    }

    override suspend fun resolveExternalIdMapping(namespace: String, externalId: String): String? = synchronized(lock) {
        externalIdMappingForward["externalid-forward:$namespace:$externalId"]
    }

    private fun removeExternalIdMapping(namespace: String, externalId: String?, internalSessionId: String?) {
        if (externalId != null) externalIdMappingForward.remove("externalid-forward:$namespace:$externalId")
        if (internalSessionId != null) externalIdMappingBackward.remove("externalid-backward:$namespace:$internalSessionId")
    }

    override suspend fun dropExternalIdMappingByExternal(namespace: String, externalId: String) {
        synchronized(lock) {
            val internalSessionId = externalIdMappingForward["externalid-forward:$namespace:$externalId"]
            removeExternalIdMapping(namespace, externalId, internalSessionId)
        }
    }

    override suspend fun dropExternalIdMappingByInternal(namespace: String, internalSessionId: String) {
        synchronized(lock) {
            val externalId = externalIdMappingBackward["externalid-backward:$namespace:$internalSessionId"]
            removeExternalIdMapping(namespace, externalId, internalSessionId)
        }
    }
}
