package id.walt.issuer2.repository

import id.walt.crypto.keys.KeyManager
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.toPublicJwk
import id.walt.crypto2.migration.v1.V1KeyMigration
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.crypto2.serialization.BinaryData
import id.walt.crypto2.serialization.StoredKeyCodec
import id.walt.issuer2.domain.IssuanceSession
import id.walt.issuer2.domain.IssuanceRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive

/**
 * Rebuilds the per-request in-memory crypto2 issuer keys that cannot be carried through
 * issuance-session JSON persistence (`@Transient`).
 *
 * Issuer-api2 keeps that material in a sidecar store. Durable
 * documents only have the legacy JWK, so they reconstruct the crypto2 key on load.
 */
object IssuanceSessionCrypto2Keys {
    private val crypto2Runtime = CryptoRuntime(defaultSoftwareKeyProviders())
    private val migration = V1KeyMigration()

    suspend fun attachFromIssuerKey(session: IssuanceSession): IssuanceSession {
        session.crypto2IssuerStoredKey?.let { encoded ->
            if (sidecarMatchesSession(session, encoded)) return session
        }
        val migrated = migrateLegacyKey(session) ?: return session
        return session.copy(crypto2IssuerStoredKey = migrated)
    }

    suspend fun migrateLegacyKey(session: IssuanceSession): String? {
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

    suspend fun sidecarMatchesSession(session: IssuanceSession, encoded: String): Boolean {
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
