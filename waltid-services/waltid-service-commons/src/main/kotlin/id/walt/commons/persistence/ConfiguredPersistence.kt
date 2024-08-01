package id.walt.commons.persistence

import id.walt.commons.config.ConfigManager
import id.walt.commons.featureflag.CommonsFeatureCatalog
import id.walt.commons.featureflag.FeatureManager
import redis.clients.jedis.HostAndPort
import redis.clients.jedis.JedisCluster
import redis.clients.jedis.JedisPooled
import redis.clients.jedis.UnifiedJedis
import kotlin.time.Duration

class ConfiguredPersistence<V : Any>(
    discriminator: String,
    defaultExpiration: Duration,
    val encoding: (V) -> String,
    val decoding: (String) -> V,
) : Persistence<V>(discriminator, defaultExpiration) {

    companion object {
        private val config = if (FeatureManager.isFeatureEnabled(CommonsFeatureCatalog.persistenceFeature)) ConfigManager.getConfig<PersistenceConfiguration>() else PersistenceConfiguration()
    }

    val underlyingPersistence: Persistence<V> = when (config.type) {
        "memory" -> InMemoryPersistence(discriminator, defaultExpiration)
        "redis", "redis-cluster" -> {
            // check(encoding != null && decoding != null) { "Redis persistence requires encoding and decoding function!" }
            check(config.nodes != null) { "Redis persistence requires defining at least 1 node!" }

            val nodes = config.nodes.map { HostAndPort(it.host, it.port) }.toSet().toMutableSet()
            if (config.type == "redis") check(config.nodes.size == 1) { "For none clustered Redis, exactly 1 node is required (have ${config.nodes.size})!" }

            val jedis: UnifiedJedis = when (config.type) {
                "redis" -> JedisPooled(nodes.first().host, nodes.first().port, config.user, config.password)
                else -> JedisCluster(nodes, config.user, config.password)
            }

            RedisPersistence(discriminator, defaultExpiration, encoding, decoding, jedis)
        }

        else -> throw IllegalArgumentException("Unknown persistence type ${config.type}")
    }

    override fun get(id: String): V? = underlyingPersistence[id]
    override fun remove(id: String) = underlyingPersistence.remove(id)
    override fun contains(id: String): Boolean = underlyingPersistence.contains(id)
    override fun listAllKeys(): Set<String> = underlyingPersistence.listAllKeys()

    override fun getAll(): Sequence<V> = underlyingPersistence.getAll()
    override fun listSize(id: String): Int = underlyingPersistence.listSize(id)

    override fun listAdd(id: String, value: V) = underlyingPersistence.listAdd(id, value)

    override fun set(id: String, value: V) = underlyingPersistence.set(id, value)

}
