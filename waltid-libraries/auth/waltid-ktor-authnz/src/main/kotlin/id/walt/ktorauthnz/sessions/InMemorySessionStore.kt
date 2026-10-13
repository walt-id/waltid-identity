package id.walt.ktorauthnz.sessions

import id.walt.ktorauthnz.utils.ExternalMappingList
import id.walt.ktorauthnz.exceptions.AuthSessionNotFoundException
import io.klogging.logger
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Sessions in process memory. A session is dropped once past its expiration, and an unfinished one
 * [pendingSessionLifetime] after it was opened - so abandoned logins do not pile up.
 */
class InMemorySessionStore(
    val pendingSessionLifetime: Duration = 15.minutes,
    private val clock: Clock = Clock.System,
) : SessionStore {

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

    private fun AuthSession.isExpired(): Boolean {
        val now = clock.now()
        return expiration?.let { now > it } == true ||
                (!status.isSuccess() && createdAt?.let { now > it + pendingSessionLifetime } == true)
    }

    /** Drops [sessionId] if it expired; returns the live session. Call with [lock] held. */
    private fun live(sessionId: String): AuthSession? {
        val session = sessions[sessionId] ?: return null
        if (!session.isExpired()) return session
        session.accountId?.let { removeSessionIdFromAccountSessions(sessionId, it) }
        sessions.remove(sessionId)
        dropExternalIdMappings(sessionId)
        return null
    }

    /** The external ids (e.g. the OIDC `sid`) of a session that is gone. Call with [lock] held. */
    private fun dropExternalIdMappings(sessionId: String) {
        for (namespace in ExternalMappingList.ALL_EXTERNAL_MAPPINGS) {
            externalIdMappingBackward.remove("externalid-backward:$namespace:$sessionId")?.let { externalId ->
                externalIdMappingForward.remove("externalid-forward:$namespace:$externalId")
            }
        }
    }

    private var storesSinceSweep = 0

    private fun sweep() {
        if (++storesSinceSweep < SWEEP_EVERY) return
        storesSinceSweep = 0
        sessions.keys.toList().forEach { live(it) }
    }

    override suspend fun findSessionById(sessionId: String): AuthSession? = synchronized(lock) {
        live(sessionId)?.copy()
    }

    override suspend fun resolveSessionById(sessionId: String): AuthSession =
        findSessionById(sessionId) ?: throw AuthSessionNotFoundException(sessionId)

    private fun removeSessionIdFromAccountSessions(sessionId: String, accountId: String) {
        val remaining = accountSessions[accountId].orEmpty() - sessionId
        if (remaining.isEmpty()) accountSessions.remove(accountId) else accountSessions[accountId] = remaining
    }

    override suspend fun dropSession(id: String) {
        log.debug { "Dropping session: $id" }
        synchronized(lock) {
            sessions[id]?.accountId?.let { removeSessionIdFromAccountSessions(id, it) }
            sessions.remove(id)
            dropExternalIdMappings(id)
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
            sweep()
        }
    }

    override suspend fun listSessionsForAccount(accountId: String): List<AuthSession> = synchronized(lock) {
        accountSessions[accountId].orEmpty().mapNotNull { live(it)?.copy() }
    }

    override suspend fun invalidateAllSessionsForAccount(accountId: String) {
        synchronized(lock) {
            accountSessions.remove(accountId)?.forEach { sessions.remove(it); dropExternalIdMappings(it) }
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

    private companion object {
        const val SWEEP_EVERY = 256
    }
}
