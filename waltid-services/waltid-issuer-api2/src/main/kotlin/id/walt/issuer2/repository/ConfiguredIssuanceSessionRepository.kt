package id.walt.issuer2.repository

import id.walt.commons.persistence.ConfiguredPersistence
import id.walt.issuer2.domain.IssuanceSession
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class ConfiguredIssuanceSessionRepository : IssuanceSessionRepository {
    private val sessions = ConfiguredPersistence(
        "issuer2_issuance_sessions", defaultExpiration = 5.minutes,
        encoding = { Json.encodeToString(IssuanceSession.serializer(), it) },
        decoding = { Json.decodeFromString(IssuanceSession.serializer(), it) },
    )

    override suspend fun save(session: IssuanceSession): IssuanceSession {
        sessions.set(session.sessionId, session, ttlUntil(session.expiresAt))
        return session
    }

    override suspend fun get(sessionId: String): IssuanceSession? = sessions[sessionId]

    override suspend fun list(): List<IssuanceSession> = sessions.getAll().toList()

    override suspend fun remove(sessionId: String) {
        sessions.remove(sessionId)
    }
}

private fun ttlUntil(expiresAt: Instant): Duration =
    (expiresAt - Clock.System.now()).coerceAtLeast(Duration.ZERO)
