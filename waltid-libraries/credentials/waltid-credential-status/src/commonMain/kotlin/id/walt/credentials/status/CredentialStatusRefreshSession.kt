package id.walt.credentials.status

import id.walt.policies2.vc.policies.status.StatusListContent
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Instant

/**
 * Bounded sharing for one explicit refresh batch, including its immediate no-store responses.
 * Close in the batch's finally block. Closing rejects new resolutions and clears bytes after any
 * already-admitted/coalesced resolution finishes; it does not cancel another caller's work.
 */
class CredentialStatusRefreshSession internal constructor(
    internal val owner: Any,
    private val now: () -> Instant,
    private val limits: CredentialStatusLimits,
) {
    private val deadline = now() + limits.resolutionTimeout
    private val mutex = Mutex()
    private val resources = linkedMapOf<String, Result<StatusListResource>>()
    private var closing = false
    private var active = 0
    private var bytes = 0L

    internal suspend fun enter(expectedOwner: Any) = mutex.withLock {
        require(owner === expectedOwner) { "Refresh session belongs to another resolver" }
        if (closing || now() >= deadline || now() < deadline - limits.resolutionTimeout) {
            closing = true
            if (active == 0) clear()
            error("Refresh session is closed or expired")
        }
        active++
    }

    internal suspend fun leave() = mutex.withLock {
        check(active > 0)
        active--
        if (closing && active == 0) clear()
    }

    internal suspend fun cached(uri: String): Result<StatusListResource>? = mutex.withLock { resources[uri] }

    internal suspend fun remember(uri: String, result: Result<StatusListResource>) = mutex.withLock {
        val size = result.getOrNull()?.content?.let { when (it) {
            is StatusListContent.Text -> it.content.length.toLong() * 2
            is StatusListContent.Binary -> it.content.size.toLong()
        } } ?: 0L
        if (uri !in resources && resources.size < limits.maxCachedLists && size <= limits.maxCachedBytes - bytes) {
            resources[uri] = result
            bytes += size
        }
    }

    /** Ends the batch without retaining its response bodies as a reusable cache. */
    suspend fun close() = mutex.withLock { closing = true; if (active == 0) clear() }

    private fun clear() { resources.clear(); bytes = 0 }
}
