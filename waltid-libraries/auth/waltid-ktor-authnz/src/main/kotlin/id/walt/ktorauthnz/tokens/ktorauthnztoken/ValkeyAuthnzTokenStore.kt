package id.walt.ktorauthnz.tokens.ktorauthnztoken

import id.walt.ktorauthnz.exceptions.InvalidTokenException
import id.walt.ktorauthnz.valkey.ValkeyConnection
import io.github.domgew.kedis.commands.KedisValueCommands
import io.klogging.logger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

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

    private val connection = ValkeyConnection(unixsocket, host, port, username, password, expiration)

    val redis = connection.client
    val option = connection.expiringWrites

    override suspend fun mapToken(token: String, sessionId: String) {
        redis.execute(
            KedisValueCommands.set("authnz-token:$token", sessionId, option)
        )
    }

    override suspend fun getTokenSessionId(token: String): String {
        return redis.execute(
            KedisValueCommands.get("authnz-token:$token"),
        ) ?: throw InvalidTokenException("Unknown token")
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

    suspend fun tryConnect() = connection.tryConnect("token store")

}

