package id.walt.verifier2

import id.walt.commons.web.WebException
import io.klogging.noCoLogger
import id.walt.verifier2.data.Verification2Session
import kotlin.time.Clock

private val log = noCoLogger("InMemoryVerificationSessionRepository")

/** Creations between expiry sweeps: the sweep is O(size), so it must not run on every create. */
private const val SWEEP_EVERY = 128

data class VerificationSessionSnapshot(
    val session: Verification2Session,
    val version: Long,
)

data class VerificationSessionProcessingClaim(
    val original: VerificationSessionSnapshot,
    val claimed: VerificationSessionSnapshot,
)

interface VerificationSessionRepository {
    suspend fun create(session: Verification2Session): VerificationSessionSnapshot
    suspend fun get(sessionId: String): VerificationSessionSnapshot?
    suspend fun compareAndSet(
        sessionId: String,
        expectedVersion: Long,
        session: Verification2Session,
    ): VerificationSessionSnapshot
    suspend fun delete(sessionId: String): Boolean

    suspend fun claimForProcessing(sessionId: String): VerificationSessionSnapshot =
        claimForProcessingWithOriginal(sessionId).claimed

    suspend fun claimForProcessingWithOriginal(sessionId: String): VerificationSessionProcessingClaim {
        val current = get(sessionId) ?: throw VerificationSessionNotFoundException(sessionId)
        val status = current.session.status
        if (current.session.attempted || status.successful != null || status in setOf(
                Verification2Session.VerificationSessionStatus.VALIDATING_RECEIVED_REQUEST,
                Verification2Session.VerificationSessionStatus.PROCESSING_FLOW,
            )) {
            throw VerificationSessionAlreadyUsedException(sessionId)
        }
        val claimed = current.session.copyForStorage().apply {
            this.status = Verification2Session.VerificationSessionStatus.VALIDATING_RECEIVED_REQUEST
        }
        return VerificationSessionProcessingClaim(
            original = current,
            claimed = compareAndSet(sessionId, current.version, claimed),
        )
    }

    suspend fun restoreProcessingClaim(claim: VerificationSessionProcessingClaim) {
        val current = get(claim.claimed.session.id) ?: return
        if (current.version != claim.claimed.version ||
            current.session.status != Verification2Session.VerificationSessionStatus.VALIDATING_RECEIVED_REQUEST ||
            current.session.attempted
        ) {
            return
        }
        try {
            compareAndSet(current.session.id, current.version, claim.original.session)
        } catch (_: StaleVerificationSessionException) {
            // A concurrent persisted update supersedes rollback.
        }
    }

    suspend fun update(
        sessionId: String,
        transform: Verification2Session.() -> Unit,
    ): VerificationSessionSnapshot {
        repeat(5) {
            val current = get(sessionId) ?: throw VerificationSessionNotFoundException(sessionId)
            val updated = current.session.copyForStorage().apply(transform)
            try {
                return compareAndSet(sessionId, current.version, updated)
            } catch (_: StaleVerificationSessionException) {
                // Merge-safe field mutations retry against the latest snapshot. Replay-sensitive
                // processing uses claimForProcessing(), which deliberately never retries.
            }
        }
        throw VerificationSessionRepositoryUnavailableException(
            "Verification session '$sessionId' could not be updated after repeated concurrent changes"
        )
    }
}

/**
 * Default ceiling on retained sessions.
 *
 * Deliberately a count rather than a byte budget, because a session carries its policy results and the
 * credentials that were presented - roughly tens of kilobytes each - so a few thousand is already a
 * substantial slice of a default heap. Sessions are normally consumed within minutes, so this is far more
 * history than a flow needs; it exists to stop unbounded growth, not to serve as storage.
 */
const val DEFAULT_MAX_IN_MEMORY_SESSIONS: Int = 2_000

class InMemoryVerificationSessionRepository(
    private val maxSessions: Int = DEFAULT_MAX_IN_MEMORY_SESSIONS,
) : VerificationSessionRepository {

    /**
     * Bounded and swept, because nothing else ever removed a session.
     *
     * [Verification2Session.persistenceExpirationDate] gives a used session [DEFAULT_RETENTION_YEARS] years,
     * and a finished session is never read again - while the only eviction was the lazy one in [get], for the
     * single id being looked up. So every completed verification stayed in this map for a decade: at 15
     * requests per minute that is ~900 sessions an hour, each tens of kilobytes, until the heap ran out.
     *
     * Access-ordered so that reaching the ceiling discards the least recently touched session rather than one
     * that is mid-flow. An in-memory store cannot honour a ten-year retention promise; a deployment that needs
     * retention needs a persistent repository, and [unexpiredEvictions] is the signal that it does.
     */
    private val sessions = object : LinkedHashMap<String, VerificationSessionSnapshot>(64, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, VerificationSessionSnapshot>,
        ): Boolean = (size > maxSessions).also { evicting ->
            if (evicting) {
                unexpiredEvictions++
                log.warn {
                    "In-memory verification session store reached its $maxSessions session ceiling; " +
                            "discarding '${eldest.key}'. Configure a persistent repository if sessions must be retained."
                }
            }
        }
    }

    /** Sessions dropped because the ceiling was reached rather than because they expired. */
    var unexpiredEvictions: Long = 0L
        private set

    /** Visible so the sweep and the ceiling can be asserted; the map itself stays private. */
    val size: Int get() = synchronized(sessions) { sessions.size }

    /**
     * Amortised sweep: expired sessions are the ones that should go first, and finding them is O(size), so it
     * runs on a fraction of creations rather than on every one.
     */
    private var createsSinceSweep = 0

    private fun dropExpiredLocked() {
        val now = Clock.System.now()
        sessions.entries.removeIf { entry ->
            entry.value.session.persistenceExpirationDate()?.let { it < now } == true
        }
    }

    override suspend fun create(session: Verification2Session): VerificationSessionSnapshot = synchronized(sessions) {
        if (++createsSinceSweep >= SWEEP_EVERY || sessions.size >= maxSessions) {
            createsSinceSweep = 0
            dropExpiredLocked()
        }
        if (sessions.containsKey(session.id)) throw DuplicateVerificationSessionException(session.id)
        VerificationSessionSnapshot(session.copyForStorage(), 0).also { sessions[session.id] = it }
            .copyForCaller()
    }

    override suspend fun get(sessionId: String): VerificationSessionSnapshot? = synchronized(sessions) {
        sessions[sessionId]?.let { snapshot ->
            // No expiry date means the verifier is configured to retain indefinitely, so the session stays.
            val expiresAt = snapshot.session.persistenceExpirationDate()
            if (expiresAt != null && expiresAt < Clock.System.now()) {
                sessions.remove(sessionId)
                null
            } else snapshot.copyForCaller()
        }
    }

    override suspend fun compareAndSet(
        sessionId: String,
        expectedVersion: Long,
        session: Verification2Session,
    ): VerificationSessionSnapshot = synchronized(sessions) {
        val current = sessions[sessionId] ?: throw VerificationSessionNotFoundException(sessionId)
        if (current.version != expectedVersion) {
            throw StaleVerificationSessionException(sessionId, expectedVersion, current.version)
        }
        check(session.id == sessionId) { "Session id cannot be changed" }
        VerificationSessionSnapshot(session.copyForStorage(), current.version + 1).also { sessions[sessionId] = it }
            .copyForCaller()
    }

    override suspend fun delete(sessionId: String): Boolean = synchronized(sessions) {
        sessions.remove(sessionId) != null
    }
}

class DuplicateVerificationSessionException(sessionId: String) :
    WebException(409, "Verification session '$sessionId' already exists")

class VerificationSessionNotFoundException(sessionId: String) :
    WebException(404, "Verification session '$sessionId' was not found")

class StaleVerificationSessionException(sessionId: String, expected: Long, actual: Long) :
    WebException(409, "Verification session '$sessionId' version is stale: expected $expected, actual $actual")

class VerificationSessionAlreadyUsedException(sessionId: String) :
    WebException(409, "Verification session '$sessionId' is already processing or complete")

class VerificationSessionRepositoryUnavailableException(message: String, cause: Throwable? = null) :
    WebException(503, message) {
    init {
        cause?.let(::initCause)
    }
}

class VerificationSessionCorruptedException(message: String, cause: Throwable? = null) :
    WebException(500, message) {
    init {
        cause?.let(::initCause)
    }
}

/**
 * Gives a snapshot its own copy of the session's mutable fields, without re-materialising its contents.
 *
 * This used to serialise the session to JSON text and parse it back, which is what made a byte-array claim
 * ruinous. `JsonByteArray` in waltid-crypto is a flyweight - a lazy list over the bytes backed by 256 interned
 * primitives - so building the tree costs nothing per byte. Parsing that text back allocates a `JsonLiteral`
 * and a `String` per element instead. Measured on a 224 KiB portrait: sharing the flyweight is free, while a
 * parsed copy costs **16.8 MiB**, a 76x amplification - and every stored snapshot then retained it. That is
 * what exhausted the heap of a verifier doing 15 requests a minute, and it happened up to four times per
 * update because [update] retries.
 *
 * `copy()` is sufficient: everything reachable is either immutable - [JsonElement],
 * [Verification2Session.PresentedRawData], `Verifier2PolicyResults`, and the read-only collections - or a
 * top-level `var`, which `copy()` gives each snapshot its own of. Nothing mutates nested session state in place.
 */
fun Verification2Session.copyForStorage(): Verification2Session = copy()

private fun VerificationSessionSnapshot.copyForCaller() = copy(session = session.copyForStorage())
