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

/**
 * Budget for what the store may hold, in bytes of estimated retained size.
 *
 * A session count cannot bound memory, because a session's cost is set by the credential presented to it. A
 * heap dump of six presentations of an mdoc carrying a 230 KB portrait measured **1.64 MiB retained per
 * session**, with this store named as the dominator of 73% of the heap - so the 2,000 session ceiling alone
 * permits about 3.3 GB. Bytes are the unit that actually bounds the store; the count remains as a second guard
 * for deployments whose sessions are small.
 */
const val DEFAULT_MAX_IN_MEMORY_BYTES: Long = 256L * 1024 * 1024

/**
 * Ratio between the presented evidence's text length and what holding the session costs.
 *
 * Calibrated from that dump: a device response of ~307,000 base64 characters retained ~1.64 MiB, so the parsed
 * form costs roughly six times its encoded text. An estimate is enough - the budget only has to be right to
 * within a small factor to turn "unbounded" into "bounded", and measuring the real retained size would mean
 * serialising the session, which is exactly the cost that made storing one expensive in the first place.
 */
private const val RETAINED_BYTES_PER_EVIDENCE_CHAR = 6L

/** What an empty session costs before any credential is presented to it. */
private const val BASE_SESSION_BYTES = 8L * 1024

class InMemoryVerificationSessionRepository(
    private val maxSessions: Int = DEFAULT_MAX_IN_MEMORY_SESSIONS,
    private val maxRetainedBytes: Long = DEFAULT_MAX_IN_MEMORY_BYTES,
) : VerificationSessionRepository {

    /**
     * Bounded and swept, because nothing else ever removed a session.
     *
     * [Verification2Session.persistenceExpirationDate] gives a used session [DEFAULT_RETENTION_YEARS] years,
     * and a finished session is never read again - while the only eviction was the lazy one in [get], for the
     * single id being looked up. So every completed verification stayed in this map for a decade: at 15
     * requests per minute that is ~900 sessions an hour, each carrying the credentials presented to it, until
     * the heap ran out.
     *
     * Access-ordered so that reaching a limit discards the least recently touched session rather than one that
     * is mid-flow. An in-memory store cannot honour a ten-year retention promise; a deployment that needs
     * retention needs a persistent repository, and [unexpiredEvictions] is the signal that it does.
     */
    private val sessions = LinkedHashMap<String, VerificationSessionSnapshot>(64, 0.75f, true)

    /** Estimated retained bytes per stored session, so the budget can be enforced without re-measuring. */
    private val weights = HashMap<String, Long>()
    private var totalWeight = 0L

    /** Sessions dropped because a limit was reached rather than because they expired. */
    var unexpiredEvictions: Long = 0L
        private set

    /** Visible so the sweep and the limits can be asserted; the map itself stays private. */
    val size: Int get() = synchronized(sessions) { sessions.size }

    /** Estimated retained bytes currently held. */
    val retainedBytes: Long get() = synchronized(sessions) { totalWeight }

    /**
     * Amortised sweep: expired sessions are the ones that should go first, and finding them is O(size), so it
     * runs on a fraction of creations rather than on every one.
     */
    private var createsSinceSweep = 0

    private fun Verification2Session.estimatedRetainedBytes(): Long {
        val evidenceChars = presentedRawData?.vpToken?.values
            ?.sumOf { tokens -> tokens.sumOf { it.length.toLong() } }
            ?: 0L
        return BASE_SESSION_BYTES + evidenceChars * RETAINED_BYTES_PER_EVIDENCE_CHAR
    }

    private fun removeLocked(id: String) {
        sessions.remove(id)
        weights.remove(id)?.let { totalWeight -= it }
    }

    private fun putLocked(id: String, snapshot: VerificationSessionSnapshot) {
        weights.put(id, snapshot.session.estimatedRetainedBytes())?.let { totalWeight -= it }
        totalWeight += weights.getValue(id)
        sessions[id] = snapshot
        enforceLimitsLocked(keep = id)
    }

    /** Evicts least-recently-used sessions until both limits hold, never the session just written. */
    private fun enforceLimitsLocked(keep: String) {
        while (sessions.size > maxSessions || totalWeight > maxRetainedBytes) {
            val eldest = sessions.keys.firstOrNull { it != keep } ?: break
            unexpiredEvictions++
            log.warn {
                "In-memory verification session store is over its limits " +
                        "(${sessions.size} sessions, ${totalWeight / 1024} KiB estimated, " +
                        "ceilings $maxSessions and ${maxRetainedBytes / 1024} KiB); discarding '$eldest'. " +
                        "Configure a persistent repository if sessions must be retained."
            }
            removeLocked(eldest)
        }
    }

    private fun dropExpiredLocked() {
        val now = Clock.System.now()
        sessions.entries
            .filter { entry -> entry.value.session.persistenceExpirationDate()?.let { it < now } == true }
            .map { it.key }
            .forEach(::removeLocked)
    }

    override suspend fun create(session: Verification2Session): VerificationSessionSnapshot = synchronized(sessions) {
        if (++createsSinceSweep >= SWEEP_EVERY || sessions.size >= maxSessions) {
            createsSinceSweep = 0
            dropExpiredLocked()
        }
        if (sessions.containsKey(session.id)) throw DuplicateVerificationSessionException(session.id)
        VerificationSessionSnapshot(session.copyForStorage(), 0).also { putLocked(session.id, it) }
            .copyForCaller()
    }

    override suspend fun get(sessionId: String): VerificationSessionSnapshot? = synchronized(sessions) {
        sessions[sessionId]?.let { snapshot ->
            // No expiry date means the verifier is configured to retain indefinitely, so the session stays.
            val expiresAt = snapshot.session.persistenceExpirationDate()
            if (expiresAt != null && expiresAt < Clock.System.now()) {
                removeLocked(sessionId)
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
        VerificationSessionSnapshot(session.copyForStorage(), current.version + 1)
            .also { putLocked(sessionId, it) }
            .copyForCaller()
    }

    override suspend fun delete(sessionId: String): Boolean = synchronized(sessions) {
        (sessionId in sessions).also { if (it) removeLocked(sessionId) }
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
