package id.walt.ktorauthnz.sessions

import id.walt.ktorauthnz.tokens.ktorauthnztoken.ValkeyAuthnzTokenStore
import io.github.domgew.kedis.KedisClient
import io.github.domgew.kedis.arguments.value.SetOptions
import io.github.domgew.kedis.commands.KedisServerCommands
import io.github.domgew.kedis.commands.KedisValueCommands
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
        keepAlive = true // optional, true is the default
        databaseIndex = 1 // optional, 0 is the default
    }

    val option = SetOptions(expire = SetOptions.ExpireOption.ExpiresInSeconds(expiration.inWholeSeconds))

    override suspend fun resolveSessionId(sessionId: String): AuthSession =
        Json.decodeFromString<AuthSession>(
            redis.execute(KedisValueCommands.get("session:$sessionId"))
                ?: throw IllegalArgumentException("Unknown session id: $sessionId")
        )

    override suspend fun dropSession(id: String) {
        redis.execute(KedisValueCommands.del("session:$id"))
    }

    override suspend fun store(session: AuthSession) {
        logger.debug("saving session $session")
        redis.execute(KedisValueCommands.set("session:${session.id}", Json.encodeToString(session), option))
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
