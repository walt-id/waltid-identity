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
                crypto2Keys.remove(session.sessionId)
            } else {
                crypto2Keys.set(session.sessionId, existingSidecar, ttl)
            }
            throw cause
        }
        return IssuanceSessionCrypto2Keys.attachStoredKeys(session, sidecars)
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
            val storedKeys = Json.decodeFromString<Map<String, String>>(persisted)
            val attached = IssuanceSessionCrypto2Keys.attachStoredKeys(session, storedKeys)
            if (backfill) {
                val normalizedKeys = IssuanceSessionCrypto2Keys.migrateLegacyKeys(attached)
                if (normalizedKeys != storedKeys) {
                    persistSidecars(session.sessionId, normalizedKeys, ttlUntil(session.expiresAt))
                }
            }
            return attached
        }
        val migrated = IssuanceSessionCrypto2Keys.migrateLegacyKeys(session)
        if (backfill) persistSidecars(session.sessionId, migrated, ttlUntil(session.expiresAt))
        return IssuanceSessionCrypto2Keys.attachStoredKeys(session, migrated)
    }

    private suspend fun persistSidecars(sessionId: String, sidecars: Map<String, String>, ttl: Duration) {
        if (sidecars.isEmpty()) {
            crypto2Keys.remove(sessionId)
        } else {
            crypto2Keys.set(sessionId, Json.encodeToString(sidecars), ttl)
        }
    }
}

private fun ttlUntil(expiresAt: Instant): Duration =
    (expiresAt - Clock.System.now()).coerceAtLeast(Duration.ZERO)
