package id.walt.commons.persistence

import io.github.reactivecircus.cache4k.Cache
import kotlin.time.Duration

class InMemoryPersistence<V : Any>(discriminator: String, defaultExpiration: Duration) : Persistence<V>(discriminator, defaultExpiration) {

    private val store = Cache.Builder<String, V>()
        .expireAfterWrite(defaultExpiration)
        .build()

    override operator fun get(id: String): V {
        return store.get(id) ?: error("No such id")
    }

    override operator fun set(id: String, value: V) {
        store.put(id, value)
    }

    override fun remove(id: String) {
        store.invalidate(id)
    }

    override fun contains(id: String): Boolean = store.get(id) != null
    override fun getAll(): Sequence<V> = store.asMap().values.asSequence()
}
