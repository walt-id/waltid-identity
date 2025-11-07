package id.walt.ktorauthnz.sessions

import id.walt.ktorauthnz.tokens.ktorauthnztoken.ValkeyAuthnzTokenStore
import io.github.domgew.kedis.KedisClient
import io.github.domgew.kedis.arguments.value.SetOptions
import io.github.domgew.kedis.commands.KedisHashCommands
import io.github.domgew.kedis.commands.KedisServerCommands
import io.github.domgew.kedis.commands.KedisValueCommands
import io.github.domgew.kedis.commands.KedisValueCommands.del
import io.github.domgew.kedis.commands.KedisValueCommands.get
import io.klogging.logger
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

class ValkeySessionStore(
    val unixsocket: String?,
    val host: String? = "127.0.0.1",
    val port: Int? = 6379,
    val username: String?,
    val password: String?,
    val expiration: Duration = 7.days
) : SessionStore {

    val logger = logger<ValkeyAuthnzTokenStore>()

    override val name = "valkey"

    val redis = KedisClient.builder {
        if (unixsocket != null) {
            unixSocket(unixsocket)
        } else if (host != null) {
            hostAndPort(
                host = host,
                port = port ?: 6379
            )
        }

        if (password != null) {
            autoAuth(
                password = password,
                username = username, // optional
            )
        } else {
            noAutoAuth()
        }

        connectTimeout = 250.milliseconds
    }

    val option = SetOptions(expire = SetOptions.ExpireOption.ExpiresInSeconds(expiration.inWholeSeconds))

    override suspend fun resolveSessionById(sessionId: String): AuthSession =
        Json.decodeFromString<AuthSession>(
            redis.execute(get("session:$sessionId"))
                ?: throw IllegalArgumentException("Unknown session id: $sessionId")
        )

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
        redis.execute(del("session:$id"))
        removeSessionIdFromAccountSessions(id)
    }


    // TODO: Contains workaround using HSET instead of SADD + pipelining instead of transaction, due to library support
    override suspend fun storeSession(session: AuthSession) {
        logger.debug("saving session $session")

        val accountId = session.accountId

        if (accountId != null) {
            redis.pipelined().apply {
                enqueue(KedisHashCommands.hashSet("account-sessions:${accountId}", mapOf(session.id to "x")))
                enqueue(KedisValueCommands.set("session:${session.id}", Json.encodeToString(session), option))
            }.execute()
        } else {
            redis.execute(KedisValueCommands.set("session:${session.id}", Json.encodeToString(session), option))
        }
    }

    override suspend fun invalidateAllSessionsForAccount(accountId: String) {
        redis.execute(KedisHashCommands.hashDel("account-sessions:${accountId}"))
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


    suspend fun tryConnect() {
        val pong = runCatching { redis.execute(KedisServerCommands.ping()) }.getOrElse {
            throw IllegalArgumentException(
                "Could not connect to valkey session store: ${it.message}",
                it
            )
        }
        require(pong.isNotBlank()) { "Valkey ping invalid" }
        logger.info { "Connected to valkey session store at: $host:$port" }
    }
}
