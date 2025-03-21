package id.walt.commons.persistence

import redis.clients.jedis.UnifiedJedis
import redis.clients.jedis.params.ScanParams
import kotlin.time.Duration

class RedisPersistence<V>(
    discriminator: String,
    defaultExpiration: Duration? = null,
    val encoding: (V) -> String,
    val decoding: (String) -> V,
    val pool: UnifiedJedis,
) : Persistence<V>(discriminator, defaultExpiration) {

    override operator fun get(id: String): V? =
        pool.get("$discriminator:$id")?.let { decoding.invoke(it) }

    override operator fun set(id: String, value: V) {
        if (defaultExpiration != null)
            pool.setex("$discriminator:$id", defaultExpiration.inWholeSeconds, encoding.invoke(value))
        else
            pool["$discriminator:$id"] = encoding.invoke(value)
    }
    
    override fun set(id: String, value: V, ttl: Duration?) {
        val effectiveTtl = ttl ?: defaultExpiration
        
        if (effectiveTtl != null)
            pool.setex("$discriminator:$id", effectiveTtl.inWholeSeconds, encoding.invoke(value))
        else
            pool["$discriminator:$id"] = encoding.invoke(value)
    }

    override fun remove(id: String) {
        pool.del("$discriminator:$id")
    }

    override fun contains(id: String): Boolean = pool.exists("$discriminator:$id")

    override fun listAllKeys(): Set<String> = pool.keys("$discriminator:*").map { it.removePrefix("session_type:") }.toSet()

    override fun getAll(): Sequence<V> {
        val keys = pool.scan("", ScanParams().match("$discriminator:")).result

        return sequence {
            keys.forEach {
                yield(get(it)!!)
            }
        }
    }

    override fun listAdd(id: String, value: V, ttl: Duration?) {
        pool.sadd("$discriminator:$id", value.toString())

        val effectiveTtl = ttl ?: defaultExpiration
        if (effectiveTtl != null)
            pool.expire("$discriminator:$id", effectiveTtl.inWholeSeconds)
    }

    override fun listSize(id: String): Int = pool.scard(id).toInt()
}
