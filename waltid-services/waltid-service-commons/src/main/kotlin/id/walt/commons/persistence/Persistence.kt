package id.walt.commons.persistence

import kotlin.time.Duration


abstract class Persistence<V>(
    val discriminator: String,
    val defaultExpiration: Duration? = null,
) {

    abstract operator fun get(id: String): V?

    /**
     * Store a value with default expiration.
     * @param id The key to store the value under
     * @param value The value to store
     */
    abstract operator fun set(id: String, value: V)

    /**
     * Store a value with a specified expiration.
     * @param id The key to store the value under
     * @param value The value to store
     * @param ttl Expiration duration. If null, defaultExpiration will be used
     */
    abstract fun set(id: String, value: V, ttl: Duration?)

    fun put(id: String, value: V, ttl: Duration? = null) = set(id, value, ttl)
    abstract fun remove(id: String)
    abstract fun getAndRemove(id: String): V?

    /** Atomic compare-and-replace; a null expected value means insert only if absent. */
    open fun compareAndSet(id: String, expected: V?, replacement: V, ttl: Duration? = null): Boolean =
        throw UnsupportedOperationException("Atomic compareAndSet is not implemented")
    /** Extend an existing record's retention atomically, without shortening a longer TTL. */
    open fun retainForAtLeast(id: String, ttl: Duration): Boolean =
        throw UnsupportedOperationException("Atomic retention is not implemented")

    fun mutate(id: String, mutation: (V) -> V, ttl: Duration? = null) {
        set(id, mutation.invoke(get(id) ?: error("Not found in $discriminator: $id")), ttl)
    }

    abstract operator fun contains(id: String): Boolean

    abstract fun listAllKeys(): Set<String>
    abstract fun getAll(): Sequence<V>

    /**
     * Add a value to a list with a specified or default expiration.
     * @param id The key of the list
     * @param value The value to add
     * @param ttl Optional expiration duration. If null, defaultExpiration will be used
     */
    abstract fun listAdd(id: String, value: V, ttl: Duration? = null)
    abstract fun listSize(id: String): Int
}