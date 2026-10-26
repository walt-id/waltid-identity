package id.walt.ktorauthnz.ephemeral

import id.walt.ktorauthnz.valkey.ValkeyConnection
import io.github.domgew.kedis.arguments.value.SetOptions
import io.github.domgew.kedis.commands.KedisScriptingCommands
import io.github.domgew.kedis.commands.KedisValueCommands
import io.github.domgew.kedis.results.scripting.DynamicResult
import io.github.domgew.kedis.results.value.SetResult
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/** [ExpiringStore] in Valkey (Redis, Redict, KeyDB), shared by every instance of a service. */
class ValkeyExpiringStore(
    unixsocket: String?,
    host: String? = "127.0.0.1",
    port: Int? = 6379,
    username: String?,
    password: String?,
    private val keyPrefix: String = "authnz-ephemeral:",
) : ExpiringStore {

    override val name = "valkey"

    private val connection = ValkeyConnection(unixsocket, host, port, username, password, expiration = 1.days)
    private val redis = connection.client

    private fun expiring(ttl: Duration, previous: SetOptions.PreviousKeyHandling = SetOptions.PreviousKeyHandling.OVERRIDE) =
        SetOptions(previous, false, SetOptions.ExpireOption.ExpiresInMilliseconds(ttl.inWholeMilliseconds.coerceAtLeast(1)))

    override suspend fun put(key: String, value: String, ttl: Duration) {
        redis.execute(KedisValueCommands.set(keyPrefix + key, value, expiring(ttl)))
    }

    override suspend fun putIfAbsent(key: String, value: String, ttl: Duration): Boolean =
        redis.execute(
            KedisValueCommands.set(keyPrefix + key, value, expiring(ttl, SetOptions.PreviousKeyHandling.KEEP_IF_EXISTS))
        ) is SetResult.Ok

    override suspend fun get(key: String): String? = redis.execute(KedisValueCommands.get(keyPrefix + key))

    override suspend fun remove(key: String) {
        redis.execute(KedisValueCommands.del(keyPrefix + key))
    }

    override suspend fun increment(key: String, ttl: Duration): Long {
        val result = redis.execute(
            KedisScriptingCommands.eval(
                INCREMENT_WITH_TTL, listOf(keyPrefix + key), listOf(ttl.inWholeMilliseconds.coerceAtLeast(1).toString())
            )
        )
        return (result as? DynamicResult.LongResult)?.value
            ?: throw IllegalStateException("Unexpected valkey reply to the counter increment: $result")
    }

    suspend fun tryConnect() = connection.tryConnect("expiring store")

    private companion object {
        /** INCR, and start the lifetime with the first increment - one atomic step. */
        const val INCREMENT_WITH_TTL =
            "local c = redis.call('INCR', KEYS[1]) if c == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end return c"
    }
}
