package id.walt.ktorauthnz.valkey

import io.github.domgew.kedis.KedisClient
import io.github.domgew.kedis.arguments.value.SetOptions
import io.github.domgew.kedis.commands.KedisServerCommands
import io.klogging.logger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * The connection the Valkey (Redis, Redict, KeyDB) stores share: a unix socket if given, otherwise host and port;
 * authentication if a password is given. Entries written with [expiringWrites] expire after [expiration].
 */
class ValkeyConnection(
    val unixsocket: String?,
    val host: String?,
    val port: Int?,
    val username: String?,
    val password: String?,
    val expiration: Duration,
) {
    val client: KedisClient = KedisClient.builder {
        if (unixsocket != null) {
            unixSocket(unixsocket)
        } else if (host != null) {
            hostAndPort(host = host, port = port ?: 6379)
        }

        if (password != null) {
            autoAuth(password = password, username = username)
        } else {
            noAutoAuth()
        }

        connectTimeout = 250.milliseconds
    }

    val expiringWrites = SetOptions(expire = SetOptions.ExpireOption.ExpiresInSeconds(expiration.inWholeSeconds))

    /** Checks that Valkey answers, failing with an [IllegalArgumentException] that names the [store] otherwise. */
    suspend fun tryConnect(store: String) {
        val pong = runCatching { client.execute(KedisServerCommands.ping()) }.getOrElse {
            throw IllegalArgumentException("Could not connect to valkey $store: ${it.message}", it)
        }
        require(pong.isNotBlank()) { "Valkey ping invalid" }
        logger<ValkeyConnection>().info { "Connected to valkey $store at: ${unixsocket ?: "$host:$port"}" }
    }
}
