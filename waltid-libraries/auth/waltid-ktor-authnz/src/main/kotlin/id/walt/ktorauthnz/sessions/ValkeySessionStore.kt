package id.walt.ktorauthnz.sessions

import id.walt.ktorauthnz.exceptions.AuthSessionNotFoundException
import id.walt.ktorauthnz.valkey.ValkeyConnection
import io.github.domgew.kedis.arguments.value.SetOptions
import io.github.domgew.kedis.commands.KedisHashCommands
import io.github.domgew.kedis.commands.KedisValueCommands
import io.github.domgew.kedis.commands.KedisValueCommands.del
import io.github.domgew.kedis.commands.KedisValueCommands.get
import io.klogging.logger
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

class ValkeySessionStore(
    val unixsocket: String?,
    val host: String? = "127.0.0.1",
    val port: Int? = 6379,
    val username: String?,
    val password: String?,
    val expiration: Duration = 7.days,
    /** Lifetime of a session that has not finished its flow yet. */
    val pendingSessionLifetime: Duration = 15.minutes,
) : SessionStore {

    val logger = logger<ValkeySessionStore>()

    override val name = "valkey"

    private val connection = ValkeyConnection(unixsocket, host, port, username, password, expiration)

    val redis = connection.client
    val option = connection.expiringWrites

    override suspend fun findSessionById(sessionId: String): AuthSession? =
        redis.execute(get("session:$sessionId"))?.let { Json.decodeFromString<AuthSession>(it) }

    override suspend fun resolveSessionById(sessionId: String): AuthSession =
        findSessionById(sessionId) ?: throw AuthSessionNotFoundException(sessionId)

    private suspend fun removeSessionIdFromAccountSessions(sessionId: String, accountId: String) {
        redis.execute(KedisHashCommands.hashDel("account-sessions:${accountId}", sessionId))
    }

    private suspend fun removeSessionIdFromAccountSessions(sessionId: String) {
        val sessionJson = redis.execute(get("session:$sessionId"))

        if (sessionJson != null) {
            val session = Json.decodeFromString<AuthSession>(sessionJson)
            val accountId = session.accountId

            if (accountId != null) {
                removeSessionIdFromAccountSessions(sessionId, accountId)
            }
        }
    }

    override suspend fun dropSession(id: String) {
        removeSessionIdFromAccountSessions(id)
        redis.execute(del("session:$id"))
    }

    /** Unfinished sessions live [pendingSessionLifetime]; finished ones until their expiration, at most [expiration]. */
    private fun writeOptionFor(session: AuthSession): SetOptions {
        val lifetime = when {
            !session.status.isSuccess() -> pendingSessionLifetime
            else -> session.expiration?.let { minOf(it - Clock.System.now(), expiration) } ?: expiration
        }
        return SetOptions(expire = SetOptions.ExpireOption.ExpiresInMilliseconds(lifetime.inWholeMilliseconds.coerceAtLeast(1)))
    }


    // TODO: Contains workaround using HSET instead of SADD + pipelining instead of transaction, due to library support
    override suspend fun storeSession(session: AuthSession) {
        logger.debug("saving session $session")

        val accountId = session.accountId

        if (accountId != null) {
            redis.pipelined().apply {
                enqueue(KedisHashCommands.hashSet("account-sessions:${accountId}", mapOf(session.id to "x")))
                enqueue(KedisValueCommands.set("session:${session.id}", Json.encodeToString(session), writeOptionFor(session)))
            }.execute()
        } else {
            redis.execute(KedisValueCommands.set("session:${session.id}", Json.encodeToString(session), writeOptionFor(session)))
        }
    }

    override suspend fun listSessionsForAccount(accountId: String): List<AuthSession> {
        val sessionIds = redis.execute(KedisHashCommands.hashKeys("account-sessions:${accountId}")).orEmpty()
        return sessionIds.mapNotNull { id ->
            // Expired sessions are gone; their index entries are cleaned up here.
            findSessionById(id) ?: run { removeSessionIdFromAccountSessions(id, accountId); null }
        }
    }

    override suspend fun invalidateAllSessionsForAccount(accountId: String) {
        val sessionIds = redis.execute(KedisHashCommands.hashKeys("account-sessions:${accountId}")).orEmpty()
        if (sessionIds.isNotEmpty()) redis.execute(del(*sessionIds.map { "session:$it" }.toTypedArray()))
        redis.execute(del("account-sessions:${accountId}"))
    }

    // -- External id --

    override suspend fun storeExternalIdMapping(namespace: String, externalId: String, internalSessionId: String) {
        redis.pipelined().apply {
            enqueue(KedisValueCommands.set("externalid-forward:$namespace:$externalId", internalSessionId))
            enqueue(KedisValueCommands.set("externalid-backward:$namespace:$internalSessionId", externalId))
        }.execute()
    }

    /** Returns internal session id */
    override suspend fun resolveExternalIdMapping(namespace: String, externalId: String): String? =
        redis.execute(get("externalid-forward:$namespace:$externalId"))

    /** Returns external id */
    suspend fun resolveExternalIdMappingBackward(namespace: String, internalSessionId: String): String? =
        redis.execute(get("externalid-backward:$namespace:$internalSessionId"))

    private suspend fun removeExternalIdMapping(namespace: String, externalId: String?, internalSessionId: String?) {
        if (externalId != null) {
            redis.execute(del("externalid-forward:$namespace:$externalId"))
        }
        if (internalSessionId != null) {
            redis.execute(del("externalid-backward:$namespace:$internalSessionId"))
        }
    }

    override suspend fun dropExternalIdMappingByExternal(namespace: String, externalId: String) {
        val internalSessionId = resolveExternalIdMapping(namespace, externalId)
        removeExternalIdMapping(namespace, externalId, internalSessionId)
    }

    override suspend fun dropExternalIdMappingByInternal(namespace: String, internalSessionId: String) {
        val externalId = resolveExternalIdMappingBackward(namespace, internalSessionId)
        removeExternalIdMapping(namespace, externalId, internalSessionId)
    }


    suspend fun tryConnect() = connection.tryConnect("session store")
}
