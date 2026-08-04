package id.walt.wallet2.handlers

import dev.whyoleg.cryptography.random.CryptographyRandom
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.time.Duration.Companion.minutes

/** Why a reviewed preview session can no longer be used. */
public enum class PreviewSessionFailureReason {
    UNKNOWN,
    EXPIRED,
    DISCARDED,
    CONSUMED,
    EVICTED,
    WRONG_WALLET,
    IN_USE,
    CAPACITY_EXCEEDED,
}

/** Raised when a reviewed preview handle is not valid for the requested action. */
public class PreviewSessionException(
    public val reason: PreviewSessionFailureReason,
    message: String,
) : IllegalStateException(message)

/**
 * Small process-local storage primitive for resolved previews.
 *
 * Entries are wallet-bound, expire after [timeToLive], and are evicted in insertion order. A
 * bounded tombstone set preserves useful failure reasons without retaining resolved protocol data.
 */
internal class PreviewSessionStore<T>(
    private val sessionName: String,
    private val capacity: Int = DEFAULT_CAPACITY,
    private val timeToLive: Duration = DEFAULT_TIME_TO_LIVE,
    private val tombstoneCapacity: Int = capacity * DEFAULT_TOMBSTONE_MULTIPLIER,
    private val now: () -> Instant = { Clock.System.now() },
    private val idGenerator: () -> String = ::securePreviewSessionId,
) {
    private class Entry<T>(
        val walletId: String,
        val value: T,
        val expiresAt: Instant,
        var inUse: Boolean = false,
        var discardRequested: Boolean = false,
    )

    private data class Tombstone(
        val walletId: String,
        val reason: PreviewSessionFailureReason,
        val expiresAt: Instant,
    )

    private val mutex = Mutex()
    private val entries = LinkedHashMap<String, Entry<T>>()
    private val tombstones = LinkedHashMap<String, Tombstone>()

    init {
        require(capacity > 0) { "Preview session capacity must be positive" }
        require(timeToLive.isPositive()) { "Preview session time-to-live must be positive" }
        require(tombstoneCapacity > 0) { "Preview session tombstone capacity must be positive" }
    }

    suspend fun create(walletId: String, value: T): String = mutex.withLock {
        require(walletId.isNotBlank()) { "Preview session wallet ID must not be blank" }
        val currentTime = now()
        cleanupExpired(currentTime)
        evictForCapacity(currentTime)

        val id = generateUniqueId()
        entries[id] = Entry(
            walletId = walletId,
            value = value,
            expiresAt = currentTime + timeToLive,
        )
        id
    }

    /**
     * Runs [block] with exclusive access to a preview. Success consumes it; failure retains it for
     * retry unless it expired while the attempt was running.
     */
    suspend fun <R> useRetainingOnFailure(
        walletId: String,
        id: String,
        block: suspend (T) -> R,
    ): R {
        val entry = mutex.withLock {
            val currentTime = now()
            cleanupExpired(currentTime)
            lookup(walletId, id, currentTime).also {
                if (it.inUse) fail(PreviewSessionFailureReason.IN_USE)
                it.inUse = true
            }
        }

        return try {
            val result = block(entry.value)
            withContext(NonCancellable) {
                mutex.withLock {
                    if (entries[id] === entry) {
                        entries.remove(id)
                        rememberTermination(id, entry.walletId, PreviewSessionFailureReason.CONSUMED, now())
                    }
                }
            }
            result
        } catch (throwable: Throwable) {
            withContext(NonCancellable) {
                mutex.withLock {
                    if (entries[id] === entry) {
                        val currentTime = now()
                        if (entry.discardRequested) {
                            entries.remove(id)
                            rememberTermination(id, entry.walletId, PreviewSessionFailureReason.DISCARDED, currentTime)
                        } else if (currentTime >= entry.expiresAt) {
                            entries.remove(id)
                            rememberTermination(id, entry.walletId, PreviewSessionFailureReason.EXPIRED, currentTime)
                        } else {
                            entry.inUse = false
                        }
                    }
                }
            }
            throw throwable
        }
    }

    /**
     * Atomically validates, consumes, and returns the selected preview before a one-shot action
     * begins. A failed validation leaves the preview available for the action that can use it.
     */
    suspend fun consume(
        walletId: String,
        id: String,
        validate: (T) -> Unit = {},
    ): T = mutex.withLock {
        val currentTime = now()
        cleanupExpired(currentTime)
        val entry = lookup(walletId, id, currentTime)
        if (entry.inUse) fail(PreviewSessionFailureReason.IN_USE)
        validate(entry.value)
        entries.remove(id)
        rememberTermination(id, entry.walletId, PreviewSessionFailureReason.CONSUMED, currentTime)
        entry.value
    }

    /** Explicitly discards a preview after local dismissal. */
    suspend fun discard(walletId: String, id: String) = mutex.withLock {
        val currentTime = now()
        cleanupExpired(currentTime)
        val entry = lookup(walletId, id, currentTime)
        if (entry.inUse) {
            entry.discardRequested = true
            return@withLock
        }
        entries.remove(id)
        rememberTermination(id, entry.walletId, PreviewSessionFailureReason.DISCARDED, currentTime)
    }

    /** Removes every preview and tombstone owned by [walletId] when deleting that wallet. */
    suspend fun clearWallet(walletId: String) = mutex.withLock {
        val currentTime = now()
        cleanupExpired(currentTime)
        val ownedIds = entries
            .filterValues { entry -> entry.walletId == walletId }
        if (ownedIds.values.any { entry -> entry.inUse }) {
            fail(PreviewSessionFailureReason.IN_USE)
        }
        val removableIds = ownedIds.keys.toList()
        removableIds.forEach(entries::remove)
        tombstones.entries.removeAll { (_, tombstone) -> tombstone.walletId == walletId }
    }

    internal suspend fun activeIds(): List<String> = mutex.withLock {
        cleanupExpired(now())
        entries.keys.toList()
    }

    private fun lookup(walletId: String, id: String, currentTime: Instant): Entry<T> {
        if (id.isBlank()) fail(PreviewSessionFailureReason.UNKNOWN)
        entries[id]?.let { entry ->
            if (entry.walletId != walletId) fail(PreviewSessionFailureReason.WRONG_WALLET)
            if (currentTime >= entry.expiresAt) {
                if (!entry.inUse) {
                    entries.remove(id)
                    rememberTermination(id, entry.walletId, PreviewSessionFailureReason.EXPIRED, currentTime)
                }
                fail(PreviewSessionFailureReason.EXPIRED)
            }
            return entry
        }

        tombstones[id]?.let { tombstone ->
            if (tombstone.walletId != walletId) fail(PreviewSessionFailureReason.WRONG_WALLET)
            fail(tombstone.reason)
        }
        fail(PreviewSessionFailureReason.UNKNOWN)
    }

    private fun cleanupExpired(currentTime: Instant) {
        val expiredEntryIds = entries
            .filterValues { entry -> !entry.inUse && currentTime >= entry.expiresAt }
            .keys
            .toList()
        expiredEntryIds.forEach { id ->
            entries.remove(id)?.let { entry ->
                rememberTermination(id, entry.walletId, PreviewSessionFailureReason.EXPIRED, currentTime)
            }
        }

        val expiredTombstoneIds = tombstones
            .filterValues { tombstone -> currentTime >= tombstone.expiresAt }
            .keys
            .toList()
        expiredTombstoneIds.forEach(tombstones::remove)
    }

    private fun evictForCapacity(currentTime: Instant) {
        while (entries.size >= capacity) {
            val oldestAvailableId = entries.entries.firstOrNull { (_, entry) -> !entry.inUse }?.key
                ?: fail(PreviewSessionFailureReason.CAPACITY_EXCEEDED)
            val evicted = entries.remove(oldestAvailableId) ?: continue
            rememberTermination(
                oldestAvailableId,
                evicted.walletId,
                PreviewSessionFailureReason.EVICTED,
                currentTime,
            )
        }
    }

    private fun rememberTermination(
        id: String,
        walletId: String,
        reason: PreviewSessionFailureReason,
        currentTime: Instant,
    ) {
        tombstones.remove(id)
        while (tombstones.size >= tombstoneCapacity) {
            tombstones.remove(tombstones.keys.first())
        }
        tombstones[id] = Tombstone(
            walletId = walletId,
            reason = reason,
            expiresAt = currentTime + timeToLive,
        )
    }

    private fun generateUniqueId(): String {
        repeat(MAX_ID_GENERATION_ATTEMPTS) {
            val candidate = idGenerator()
            if (candidate.isNotBlank() && candidate !in entries && candidate !in tombstones) {
                return candidate
            }
        }
        error("Could not generate a unique $sessionName preview session ID")
    }

    private fun fail(reason: PreviewSessionFailureReason): Nothing =
        throw PreviewSessionException(reason, reason.message(sessionName))

    private companion object {
        const val DEFAULT_CAPACITY = 16
        const val DEFAULT_TOMBSTONE_MULTIPLIER = 4
        const val MAX_ID_GENERATION_ATTEMPTS = 32
        val DEFAULT_TIME_TO_LIVE: Duration = 10.minutes
    }
}

private fun securePreviewSessionId(): String =
    CryptographyRandom.nextBytes(32).encodeToBase64Url()

private fun PreviewSessionFailureReason.message(sessionName: String): String = when (this) {
    PreviewSessionFailureReason.UNKNOWN ->
        "$sessionName preview session is unknown; preview again before continuing."
    PreviewSessionFailureReason.EXPIRED ->
        "$sessionName preview session expired; preview again before continuing."
    PreviewSessionFailureReason.DISCARDED ->
        "$sessionName preview session was discarded; preview again before continuing."
    PreviewSessionFailureReason.CONSUMED ->
        "$sessionName preview session was already consumed; preview again before continuing."
    PreviewSessionFailureReason.EVICTED ->
        "$sessionName preview session was evicted; preview again before continuing."
    PreviewSessionFailureReason.WRONG_WALLET ->
        "$sessionName preview session belongs to a different wallet."
    PreviewSessionFailureReason.IN_USE ->
        "$sessionName preview session is already being used by another action."
    PreviewSessionFailureReason.CAPACITY_EXCEEDED ->
        "$sessionName preview session capacity is temporarily exhausted."
}
