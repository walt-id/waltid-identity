package id.walt.ktorauthnz.ephemeral

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/** [ExpiringStore] in process memory; expired entries are dropped when read and swept on writes. */
class InMemoryExpiringStore(
    private val clock: Clock = Clock.System,
) : ExpiringStore {

    override val name = "in_memory"

    private data class Entry(val value: String, val expiresAt: Instant)

    private val lock = Any()
    private val entries = HashMap<String, Entry>()
    private var writesSinceSweep = 0

    private fun live(key: String, now: Instant): Entry? =
        entries[key]?.takeIf { it.expiresAt > now } ?: run { entries.remove(key); null }

    private fun write(key: String, entry: Entry) {
        entries[key] = entry
        if (++writesSinceSweep >= SWEEP_EVERY) {
            writesSinceSweep = 0
            val now = clock.now()
            entries.values.removeAll { it.expiresAt <= now }
        }
    }

    override suspend fun put(key: String, value: String, ttl: Duration) = synchronized(lock) {
        write(key, Entry(value, clock.now() + ttl))
    }

    override suspend fun putIfAbsent(key: String, value: String, ttl: Duration): Boolean = synchronized(lock) {
        val now = clock.now()
        if (live(key, now) != null) false else {
            write(key, Entry(value, now + ttl)); true
        }
    }

    override suspend fun get(key: String): String? = synchronized(lock) { live(key, clock.now())?.value }

    override suspend fun remove(key: String) {
        synchronized(lock) { entries.remove(key) }
    }

    override suspend fun increment(key: String, ttl: Duration): Long = synchronized(lock) {
        val now = clock.now()
        val current = live(key, now)
        val count = (current?.value?.toLong() ?: 0) + 1
        write(key, Entry(count.toString(), current?.expiresAt ?: (now + ttl)))
        count
    }

    /** Number of stored entries, including expired ones not yet swept. */
    val size: Int get() = synchronized(lock) { entries.size }

    private companion object {
        const val SWEEP_EVERY = 256
    }
}
