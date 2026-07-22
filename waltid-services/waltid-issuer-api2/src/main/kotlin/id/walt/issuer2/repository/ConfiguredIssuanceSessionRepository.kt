package id.walt.issuer2.repository

import id.walt.commons.persistence.ConfiguredPersistence
import id.walt.commons.persistence.Persistence
import id.walt.issuer2.domain.IssuanceSession
import id.walt.crypto.keys.KeyManager
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.toPublicJwk
import id.walt.crypto2.migration.v1.V1KeyMigration
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.crypto2.serialization.BinaryData
import id.walt.crypto2.serialization.StoredKeyCodec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
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
    private val crypto2Runtime = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()))
    private val migration = V1KeyMigration()
    override suspend fun save(session: IssuanceSession): IssuanceSession {
        val ttl = ttlUntil(session.expiresAt)
        val existingSidecar = crypto2Keys[session.sessionId]
        val sidecar = session.crypto2IssuerStoredKey ?: migrateLegacyKey(session)
        sidecar?.let {
            require(sidecarMatchesSession(session, it)) {
                "Issuer2 crypto2 sidecar does not match the legacy session key"
            }
        }
        sidecar?.let { crypto2Keys.set(session.sessionId, it, ttl) }
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
            if (sidecarMatchesSession(session, persisted)) {
                return session.copy(crypto2IssuerStoredKey = persisted)
            }
            val repaired = migrateLegacyKey(session)
            if (repaired != null) {
                if (backfill) crypto2Keys.set(session.sessionId, repaired, ttlUntil(session.expiresAt))
                return session.copy(crypto2IssuerStoredKey = repaired)
            }
            if (backfill) crypto2Keys.remove(session.sessionId)
            return session
        }
        val migrated = migrateLegacyKey(session) ?: return session
        if (backfill) crypto2Keys.set(session.sessionId, migrated, ttlUntil(session.expiresAt))
        return session.copy(crypto2IssuerStoredKey = migrated)
    }

    private suspend fun migrateLegacyKey(session: IssuanceSession): String? {
        if (session.issuerKey["type"]?.jsonPrimitive?.content != "jwk") return null
        val legacyKey = KeyManager.resolveSerializedKey(session.issuerKey)
        val stored = migration.migrate(
            recordId = KeyId(legacyKey.getKeyId()),
            serialized = session.issuerKey,
            usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
        )
        crypto2Runtime.restore(stored)
        return StoredKeyCodec.encodeToString(stored)
    }

    private suspend fun sidecarMatchesSession(session: IssuanceSession, encoded: String): Boolean {
        val stored = StoredKeyCodec.decodeFromString(encoded)
        val crypto2Key = crypto2Runtime.restore(stored)
        require(KeyUsage.SIGN in stored.usages) { "Issuer2 crypto2 sidecar key does not permit signing" }
        val legacyKey = KeyManager.resolveSerializedKey(session.issuerKey)
        if (stored.id.value != legacyKey.getKeyId()) return false
        val legacyPublicJwk = EncodedKey.Jwk(
            data = BinaryData(Json.encodeToString(legacyKey.getPublicKey().exportJWKObject()).encodeToByteArray()),
            privateMaterial = false,
        )
        val crypto2PublicJwk = requireNotNull(crypto2Key.capabilities.publicKeyExporter) {
            "Issuer2 crypto2 sidecar key does not export public material"
        }.exportPublicKey().toPublicJwk(crypto2Key.spec)
        return Jwk.sha256Thumbprint(legacyPublicJwk) == Jwk.sha256Thumbprint(crypto2PublicJwk)
    }
}

private fun ttlUntil(expiresAt: Instant): Duration =
    (expiresAt - Clock.System.now()).coerceAtLeast(Duration.ZERO)
