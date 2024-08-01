package id.walt.commons.persistence

import io.github.reactivecircus.cache4k.Cache
import kotlin.time.Duration

class InMemoryPersistence<V : Any>(discriminator: String, defaultExpiration: Duration) : Persistence<V>(discriminator, defaultExpiration) {

    private val store = Cache.Builder<String, V>()
        .expireAfterWrite(defaultExpiration)
        .build()

    private val listStore by lazy { Cache.Builder<String, ArrayList<V>>()
        .expireAfterWrite(defaultExpiration)
        .build() }

    override fun listAdd(id: String, value: V) {
        if (!listStore.asMap().containsKey(id)) {
            listStore.put(id, arrayListOf(value))
        } else {
            listStore.put(id, listStore.get(id)!!.apply { add(value) })
        }
    }
    override fun listSize(id: String): Int = listStore.get(id)?.size ?: 0

    override operator fun get(id: String): V? {
        return store.get(id)
    }

    override operator fun set(id: String, value: V) {
        store.put(id, value)
    }

    override fun remove(id: String) {
        store.invalidate(id)
    }

    override fun contains(id: String): Boolean = store.get(id) != null
    @Suppress("UNCHECKED_CAST") // see line 8
    override fun listAllKeys(): Set<String> = store.asMap().keys as Set<String>

    override fun getAll(): Sequence<V> = store.asMap().values.asSequence()
}
