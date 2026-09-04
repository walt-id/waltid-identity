package id.walt.issuer2.repository

import id.walt.commons.persistence.ConfiguredPersistence
import id.walt.commons.persistence.Persistence
import id.walt.issuer2.domain.IssuanceSession
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class ConfiguredIssuanceSessionRepository(
    private val sessions: Persistence<IssuanceSession> = ConfiguredPersistence(
        "issuer2_issuance_sessions",
        defaultExpiration = 5.minutes,
        encoding = { Json.encodeToString(IssuanceSession.serializer(), it) },
        decoding = { Json.decodeFromString(IssuanceSession.serializer(), it) },
    ),
    private val crypto2Keys: Persistence<String> = ConfiguredPersistence(
        "issuer2_issuance_session_crypto2_keys",
        defaultExpiration = 5.minutes,
        encoding = { it },
        decoding = { it },
    ),
) : IssuanceSessionRepository {
    override suspend fun save(session: IssuanceSession): IssuanceSession {
        val ttl = ttlUntil(session.expiresAt)
        val existingSidecar = crypto2Keys[session.sessionId]
        val sidecars = IssuanceSessionCrypto2Keys.migrateLegacyKeys(session)
        IssuanceSessionCrypto2Keys.validateStoredKeys(session, sidecars)
        persistSidecars(session.sessionId, sidecars, ttl)
        try {
            sessions.set(session.sessionId, session, ttl)
        } catch (cause: Exception) {
            if (existingSidecar == null) {
                if (sidecar != null) crypto2Keys.remove(session.sessionId)
            } else {
                crypto2Keys.set(session.sessionId, existingSidecar, ttl)
            }
            throw cause
        }
        return session.copy(crypto2IssuerStoredKey = sidecar)
    }

    override suspend fun get(sessionId: String): IssuanceSession? = sessions[sessionId]?.let { attachCrypto2Key(it, backfill = true) }

    // Claiming hands the crypto2 key material to the caller and drops the sidecar with the session,
    // the way remove() does. Backfilling would write a sidecar for a session that no longer exists,
    // and callers that restore a claimed session save it again, which rewrites the sidecar.
    override suspend fun take(sessionId: String): IssuanceSession? =
        sessions.getAndRemove(sessionId)?.let { session ->
            attachCrypto2Key(session, backfill = false).also { crypto2Keys.remove(sessionId) }
        }

    override suspend fun list(): List<IssuanceSession> {
        val result = mutableListOf<IssuanceSession>()
        for (session in sessions.getAll()) result += attachCrypto2Key(session, backfill = true)
        return result
    }

    override suspend fun remove(sessionId: String) {
        sessions.remove(sessionId)
        crypto2Keys.remove(sessionId)
    }

    private suspend fun attachCrypto2Key(session: IssuanceSession, backfill: Boolean): IssuanceSession {
        val persisted = crypto2Keys[session.sessionId]
        if (persisted != null) {
            if (IssuanceSessionCrypto2Keys.sidecarMatchesSession(session, persisted)) {
                return session.copy(crypto2IssuerStoredKey = persisted)
            }
            val repaired = IssuanceSessionCrypto2Keys.migrateLegacyKey(session)
            if (repaired != null) {
                if (backfill) crypto2Keys.set(session.sessionId, repaired, ttlUntil(session.expiresAt))
                return session.copy(crypto2IssuerStoredKey = repaired)
            }
            if (backfill) crypto2Keys.remove(session.sessionId)
            return session
        }
        val migrated = IssuanceSessionCrypto2Keys.migrateLegacyKey(session) ?: return session
        if (backfill) crypto2Keys.set(session.sessionId, migrated, ttlUntil(session.expiresAt))
        return session.copy(crypto2IssuerStoredKey = migrated)
    }
}

private fun ttlUntil(expiresAt: Instant): Duration =
    (expiresAt - Clock.System.now()).coerceAtLeast(Duration.ZERO)
