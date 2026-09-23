package id.walt.commons.persistence

import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource

class InMemoryPersistence<V : Any> @JvmOverloads constructor(
    discriminator: String,
    defaultExpiration: Duration,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) : Persistence<V>(discriminator, defaultExpiration) {
    private val lock = Any()
    private val store = mutableMapOf<String, Entry<V>>()
    private val listStore = mutableMapOf<String, Entry<ArrayList<V>>>()

    override fun listAdd(id: String, value: V, ttl: Duration?) = synchronized(lock) {
        pruneExpired(listStore)
        val values = listStore.valid(id)?.value ?: arrayListOf()
        values += value
        listStore[id] = entry(values, ttl)
    }

    override fun listSize(id: String): Int = synchronized(lock) {
        listStore.valid(id)?.value?.size ?: 0
    }

    override operator fun get(id: String): V? = synchronized(lock) {
        store.valid(id)?.value
    }

    override operator fun set(id: String, value: V) {
        set(id, value, null)
    }

    override fun set(id: String, value: V, ttl: Duration?) = synchronized(lock) {
        pruneExpired(store)
        store[id] = entry(value, ttl)
    }

    override fun remove(id: String) = synchronized(lock) {
        store.remove(id)
        Unit
    }

    override fun getAndRemove(id: String): V? = synchronized(lock) {
        val value = store.valid(id)?.value
        store.remove(id)
        value
    }

    override fun contains(id: String): Boolean = synchronized(lock) {
        store.valid(id) != null
    }

    override fun listAllKeys(): Set<String> = synchronized(lock) {
        pruneExpired(store)
        store.keys.toSet()
    }

    override fun getAll(): Sequence<V> = synchronized(lock) {
        pruneExpired(store)
        store.values.map(Entry<V>::value).toList().asSequence()
    }

    private fun <T : Any> entry(value: T, ttl: Duration?): Entry<T> = Entry(
        value = value,
        ttl = ttl ?: defaultExpiration,
        writtenAt = timeSource.markNow(),
    )

    private fun <T : Any> MutableMap<String, Entry<T>>.valid(id: String): Entry<T>? {
        val current = this[id] ?: return null
        return if (current.isExpired()) {
            remove(id)
            null
        } else {
            current
        }
    }

    private fun Entry<*>.isExpired(): Boolean = ttl?.let { it <= Duration.ZERO || writtenAt.elapsedNow() >= it } == true

    private fun <T : Any> pruneExpired(entries: MutableMap<String, Entry<T>>) {
        entries.entries.removeIf { it.value.isExpired() }
    }

    private data class Entry<T : Any>(
        val value: T,
        val ttl: Duration?,
        val writtenAt: TimeMark,
    )
}
