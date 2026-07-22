package id.walt.verifier2

import id.walt.commons.web.WebException
import id.walt.verifier2.data.Verification2Session
import kotlinx.serialization.json.Json
import kotlin.time.Clock

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

class InMemoryVerificationSessionRepository : VerificationSessionRepository {
    private val sessions = mutableMapOf<String, VerificationSessionSnapshot>()

    override suspend fun create(session: Verification2Session): VerificationSessionSnapshot = synchronized(sessions) {
        if (sessions.containsKey(session.id)) throw DuplicateVerificationSessionException(session.id)
        VerificationSessionSnapshot(session.copyForStorage(), 0).also { sessions[session.id] = it }
            .copyForCaller()
    }

    override suspend fun get(sessionId: String): VerificationSessionSnapshot? = synchronized(sessions) {
        sessions[sessionId]?.let { snapshot ->
            val expiresAt = snapshot.session.persistenceExpirationDate()
            if (expiresAt < Clock.System.now()) {
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

private val repositoryJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

fun Verification2Session.copyForStorage(): Verification2Session =
    repositoryJson.decodeFromString(repositoryJson.encodeToString(Verification2Session.serializer(), this))

private fun VerificationSessionSnapshot.copyForCaller() = copy(session = session.copyForStorage())
