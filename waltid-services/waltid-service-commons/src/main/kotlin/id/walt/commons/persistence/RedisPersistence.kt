package id.walt.commons.persistence

import redis.clients.jedis.UnifiedJedis
import redis.clients.jedis.JedisCluster
import redis.clients.jedis.Jedis
import redis.clients.jedis.params.ScanParams
import redis.clients.jedis.resps.ScanResult
import redis.clients.jedis.exceptions.JedisConnectionException
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
        if (defaultExpiration != null) {
            if (defaultExpiration <= Duration.ZERO) {
                remove(id)
                return
            }
            pool.setex("$discriminator:$id", defaultExpiration.inWholeSeconds.coerceAtLeast(1), encoding.invoke(value))
        } else
            pool["$discriminator:$id"] = encoding.invoke(value)
    }

    override fun set(id: String, value: V, ttl: Duration?) {
        val effectiveTtl = ttl ?: defaultExpiration

        if (effectiveTtl != null) {
            if (effectiveTtl <= Duration.ZERO) {
                remove(id)
                return
            }
            pool.setex("$discriminator:$id", effectiveTtl.inWholeSeconds.coerceAtLeast(1), encoding.invoke(value))
        } else
            pool["$discriminator:$id"] = encoding.invoke(value)
    }

    override fun remove(id: String) {
        pool.del("$discriminator:$id")
    }

    override fun getAndRemove(id: String): V? =
        pool.getDel("$discriminator:$id")?.let { decoding.invoke(it) }

    override fun contains(id: String): Boolean = pool.exists("$discriminator:$id")

    override fun listAllKeys(): Set<String> = scanKeys()
        .map { it.removePrefix("$discriminator:") }
        .toSet()

    override fun getAll(): Sequence<V> {
        return scanKeys().asSequence().mapNotNull { key -> pool.get(key)?.let(decoding) }
    }

    override fun listAdd(id: String, value: V, ttl: Duration?) {
        pool.sadd("$discriminator:$id", value.toString())

        val effectiveTtl = ttl ?: defaultExpiration
        if (effectiveTtl != null) {
            if (effectiveTtl <= Duration.ZERO) {
                remove(id)
                return
            }
            pool.expire("$discriminator:$id", effectiveTtl.inWholeSeconds.coerceAtLeast(1))
        }
    }

    override fun listSize(id: String): Int = pool.scard("$discriminator:$id").toInt()

    private fun scanKeys(): Set<String> = when (pool) {
        is JedisCluster -> {
            val masterAddresses = redisClusterMasterAddresses(clusterTopology(pool))
            pool.clusterNodes
                .filterKeys { node -> masterAddresses.any(node::contains) }
                .values
                .flatMapTo(mutableSetOf()) { nodePool ->
                    Jedis(nodePool.resource).use { client ->
                        scanNode { cursor, params -> client.scan(cursor, params) }
                    }
                }
        }
        else -> scanNode { cursor, params -> pool.scan(cursor, params) }
    }

    private fun clusterTopology(cluster: JedisCluster): String {
        var lastFailure: JedisConnectionException? = null
        for (nodePool in cluster.clusterNodes.values) {
            try {
                return Jedis(nodePool.resource).use(Jedis::clusterNodes)
            } catch (cause: JedisConnectionException) {
                lastFailure = cause
            }
        }
        throw lastFailure ?: JedisConnectionException("Redis cluster has no nodes")
    }

    private fun scanNode(scanCall: (String, ScanParams) -> ScanResult<String>): Set<String> {
        val keys = mutableSetOf<String>()
        var cursor = ScanParams.SCAN_POINTER_START
        do {
            val scan = scanCall(cursor, ScanParams().match("$discriminator:*"))
            keys += scan.result
            cursor = scan.cursor
        } while (cursor != ScanParams.SCAN_POINTER_START)
        return keys
    }
}

internal fun redisClusterMasterAddresses(topology: String): Set<String> =
    topology.lineSequence().mapNotNull { line ->
        val fields = line.split(' ')
        val flags = fields.getOrNull(2)?.split(',').orEmpty()
        fields.getOrNull(1)?.substringBefore('@')?.takeIf { "master" in flags }
    }.toSet()
