package id.walt.ktorauthnz.ephemeral

import kotlin.time.Duration

/**
 * Short-lived key/value data of the authentication flows - attempt counters, one-time challenges, pending
 * enrolments, reset and refresh tokens. Every entry expires; nothing here has to survive longer than its lifetime.
 */
interface ExpiringStore {

    val name: String

    suspend fun put(key: String, value: String, ttl: Duration)

    /** Stores [value] only if [key] holds nothing; returns whether it was stored. For single-use values. */
    suspend fun putIfAbsent(key: String, value: String, ttl: Duration): Boolean

    suspend fun get(key: String): String?

    suspend fun remove(key: String)

    /** Increments the counter at [key] and returns the new count; the first increment starts its [ttl]. */
    suspend fun increment(key: String, ttl: Duration): Long
}
