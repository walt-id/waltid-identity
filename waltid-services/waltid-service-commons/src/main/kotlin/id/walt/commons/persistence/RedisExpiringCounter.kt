package id.walt.commons.persistence

import redis.clients.jedis.HostAndPort
import redis.clients.jedis.JedisCluster
import redis.clients.jedis.JedisPooled
import redis.clients.jedis.UnifiedJedis

class RedisExpiringCounter(private val jedis: UnifiedJedis) {

    fun incrementAndExpire(key: String, ttlSeconds: Long): Long {
        require(ttlSeconds > 0) { "TTL must be positive" }

        val result = jedis.eval(
            INCREMENT_AND_EXPIRE_SCRIPT,
            listOf(key),
            listOf(ttlSeconds.toString()),
        )
        return (result as Number).toLong()
    }

    fun setBlock(key: String, ttlSeconds: Long) {
        require(ttlSeconds > 0) { "TTL must be positive" }
        jedis.setex(key, ttlSeconds, BLOCK_VALUE)
    }

    fun ttl(key: String): Long? =
        jedis.ttl(key).takeIf { it > 0 }

    fun delete(key: String) {
        jedis.del(key)
    }

    companion object {
        private const val BLOCK_VALUE = "1"
        private val INCREMENT_AND_EXPIRE_SCRIPT = """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
              redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return current
        """.trimIndent()

        fun fromConfiguration(config: PersistenceConfiguration): RedisExpiringCounter? {
            if (config.type != "redis" && config.type != "redis-cluster") {
                return null
            }

            val nodes = requireNotNull(config.nodes) {
                "Redis expiring counter requires at least one Redis node"
            }.map { HostAndPort(it.host, it.port) }.toSet().toMutableSet()

            if (config.type == "redis") {
                require(nodes.size == 1) { "Non-cluster Redis expiring counter requires exactly one Redis node" }
            }

            val jedis: UnifiedJedis = when (config.type) {
                "redis" -> JedisPooled(nodes.first().host, nodes.first().port, config.user, config.password)
                else -> JedisCluster(nodes, config.user, config.password)
            }

            return RedisExpiringCounter(jedis)
        }
    }
}
