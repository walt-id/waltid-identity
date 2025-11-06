package id.walt.ktorauthnz.tokens.ktorauthnztoken

import io.github.domgew.kedis.KedisClient
import io.github.domgew.kedis.arguments.value.SetOptions
import io.github.domgew.kedis.commands.KedisServerCommands
import io.github.domgew.kedis.commands.KedisValueCommands
import io.klogging.logger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

/**
 * Redis/Valkey/Redict/KeyDB Token Store
 */
class ValkeyAuthnzTokenStore(
    val unixsocket: String?,
    val host: String? = "127.0.0.1",
    val port: Int? = 6379,
    val username: String?,
    val password: String?,
    val expiration: Duration = 7.days
) : KtorAuthnzTokenStore {

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

    override suspend fun mapToken(token: String, sessionId: String) {
        redis.execute(
            KedisValueCommands.set("authnz-token:$token", sessionId, option)
        )
    }

    override suspend fun getTokenSessionId(token: String): String {
        return redis.execute(
            KedisValueCommands.get("authnz-token:$token"),
        ) ?: throw IllegalArgumentException("Unknown token: $token")
    }

    override suspend fun validateToken(token: String): Boolean {
        return (redis.execute(
            KedisValueCommands.get("authnz-token:$token"),
        ) != null)
    }

    override suspend fun dropToken(token: String) {
        redis.execute(
            KedisValueCommands.del("authnz-token:$token")
        )
    }

    suspend fun tryConnect() {
        val pong = runCatching { redis.execute(KedisServerCommands.ping()) }.getOrElse {
            throw IllegalArgumentException(
                "Could not connect to valkey token store: ${it.message}",
                it
            )
        }
        require(pong.isNotBlank()) { "Valkey ping invalid" }
        logger.info { "Connected to valkey token store at: $host:$port" }
    }

}

