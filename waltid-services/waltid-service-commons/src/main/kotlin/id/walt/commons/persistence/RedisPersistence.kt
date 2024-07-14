package id.walt.commons.persistence

import io.klogging.config.seq
import redis.clients.jedis.JedisPooled
import redis.clients.jedis.params.ScanParams
import kotlin.time.Duration

class RedisPersistence<V>(
    discriminator: String,
    defaultExpiration: Duration,
    val encoding: (V) -> String,
    val decoding: (String) -> V,
) : Persistence<V>(discriminator, defaultExpiration) {
    val pool: JedisPooled = JedisPooled("localhost", 6379)

    override operator fun get(id: String): V {
        return decoding.invoke(pool.get("$discriminator:$id") ?: error("No such id: $id"))
    }

    override operator fun set(id: String, value: V) {
        pool.setex("$discriminator:$id", defaultExpiration.inWholeSeconds, encoding.invoke(value))
    }

    override fun remove(id: String) {
        pool.del("$discriminator:$id")
    }

    override fun contains(id: String): Boolean = pool.exists("$discriminator:$id")

    override fun getAll(): Sequence<V> {
        val keys = pool.scan("", ScanParams().match("$discriminator:")).result

        return sequence {
            keys.forEach {
                yield(get(it))
            }
        }
    }
}
