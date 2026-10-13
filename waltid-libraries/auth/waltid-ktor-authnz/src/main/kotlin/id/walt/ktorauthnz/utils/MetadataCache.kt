package id.walt.ktorauthnz.utils

import io.klogging.logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * A cache of remote metadata (OIDC discovery documents, JWKS), safe for concurrent use:
 *
 * - a value is fetched again once it is older than [lifetime]; concurrent requests for a key share one fetch
 * - [refresh] fetches again before that (e.g. for a key id the cached JWKS lacks) at most once per [minRefreshInterval],
 *   so requests naming unknown key ids cannot make every call reach the remote server
 * - when fetching fails, the last value is used for up to [staleIfError] after it expired, so an outage of the remote
 *   server does not end logins at once
 */
class MetadataCache<K : Any, V : Any>(
    private val name: String,
    var lifetime: Duration = 1.hours,
    var minRefreshInterval: Duration = 1.minutes,
    var staleIfError: Duration = 24.hours,
    internal var clock: Clock = Clock.System,
) {
    private class Entry<V>(val value: V, val fetchedAt: Instant)

    private val log = logger("MetadataCache")
    private val entries = ConcurrentHashMap<K, Entry<V>>()
    private val locks = ConcurrentHashMap<K, Mutex>()

    /** The value of [key]: cached while younger than [lifetime], otherwise from [fetch]. */
    suspend fun get(key: K, fetch: suspend () -> V): V {
        entries[key]?.takeIf { clock.now() - it.fetchedAt < lifetime }?.let { return it.value }
        return lock(key).withLock {
            // Fetched by another caller while this one waited.
            entries[key]?.takeIf { clock.now() - it.fetchedAt < lifetime }?.value ?: load(key, fetch)
        }
    }

    /** The value of [key] fetched again now - unless it was fetched less than [minRefreshInterval] ago. */
    suspend fun refresh(key: K, fetch: suspend () -> V): V = lock(key).withLock {
        entries[key]?.takeIf { clock.now() - it.fetchedAt < minRefreshInterval }?.value ?: load(key, fetch)
    }

    fun clear() = entries.clear()

    private fun lock(key: K) = locks.computeIfAbsent(key) { Mutex() }

    private suspend fun load(key: K, fetch: suspend () -> V): V {
        val value = try {
            fetch()
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            val stale = entries[key]?.takeIf { clock.now() - it.fetchedAt < lifetime + staleIfError }
                ?: throw cause
            log.warn { "Fetching $name for $key failed, using the copy from ${stale.fetchedAt}: ${cause.message}" }
            return stale.value
        }
        entries[key] = Entry(value, clock.now())
        return value
    }
}
