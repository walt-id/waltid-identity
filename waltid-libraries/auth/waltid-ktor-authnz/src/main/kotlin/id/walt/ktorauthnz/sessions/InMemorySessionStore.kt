package id.walt.ktorauthnz.sessions

import io.klogging.logger

class InMemorySessionStore : SessionStore {

    override val name = "in_memory"

    private val log = logger("InMemorySessionStore")

    /** Session ID -> Session */
    val sessions = HashMap<String, AuthSession>()

    /** Account ID -> [Session ID, ...] */
    val accountSessions = HashMap<String, List<String>>()

    /** External ID -> internal ID */
    val externalIdMappingForward = HashMap<String, String>()

    /** Internal ID -> external ID */
    val externalIdMappingBackward = HashMap<String, String>()

    override suspend fun resolveSessionById(sessionId: String): AuthSession =
        sessions[sessionId] ?: error("Unknown session id: $sessionId")

    private fun removeSessionIdFromAccountSessions(sessionId: String, accountId: String) {
        if (sessions.containsKey(sessionId)) {
            if (accountSessions.containsKey(accountId)) {
                val sessionsForAccount = accountSessions[accountId]?.toMutableList()
                if (sessionsForAccount != null) {
                    sessionsForAccount.removeIf { it == sessionId }
                    accountSessions[accountId] = sessionsForAccount
                }
            }
        }
    }

    private suspend fun removeSessionIdFromAccountSessions(sessionId: String) {
        if (sessions.containsKey(sessionId)) {
            resolveSessionById(sessionId).accountId?.let { removeSessionIdFromAccountSessions(sessionId, it) }
        }
    }

    override suspend fun dropSession(id: String) {
        log.debug { "Dropping session: $id" }

        runCatching {
            removeSessionIdFromAccountSessions(id)
        }

        sessions.remove(id)
    }

    override suspend fun storeSession(session: AuthSession) {
        log.debug { "Saving session: $session" }
        sessions[session.id] = session
    }

    override suspend fun invalidateAllSessionsForAccount(accountId: String) {
        val sessionIds = accountSessions.remove(accountId)
        sessionIds?.forEach {
            sessions.remove(it)
        }
    }

    override suspend fun storeExternalIdMapping(namespace: String, externalId: String, internalSessionId: String) {
        externalIdMappingForward["externalid-forward:$namespace:$externalId"] = internalSessionId
        externalIdMappingBackward["externalid-backward:$namespace:$internalSessionId"] = externalId
    }

    override suspend fun resolveExternalIdMapping(namespace: String, externalId: String): String? =
        externalIdMappingForward["externalid-forward:$namespace:$externalId"]

    private fun removeExternalIdMapping(namespace: String, externalId: String?, internalSessionId: String?) {
        if (externalId != null) externalIdMappingForward.remove("externalid-forward:$namespace:$externalId")
        if (internalSessionId != null) externalIdMappingBackward.remove("externalid-backward:$namespace:$internalSessionId")
    }

    override suspend fun dropExternalIdMappingByExternal(namespace: String, externalId: String) {
        val internalSessionId = externalIdMappingForward["externalid-forward:$namespace:$externalId"]
        removeExternalIdMapping(namespace, externalId, internalSessionId)
    }

    override suspend fun dropExternalIdMappingByInternal(namespace: String, internalSessionId: String) {
        val externalId = externalIdMappingBackward["externalid-backward:$namespace:$internalSessionId"]
        removeExternalIdMapping(namespace, externalId, internalSessionId)
    }
}
