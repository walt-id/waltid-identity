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

    suspend fun attachFromIssuerKeys(session: IssuanceSession): IssuanceSession =
        session.copy(
            issuanceRequests = session.issuanceRequests.map { request ->
                request.crypto2IssuerStoredKey?.let { encoded ->
                    if (sidecarMatchesRequest(request, encoded)) return@map request
                }
                request.copy(crypto2IssuerStoredKey = migrateLegacyKey(request))
            }
        )

    suspend fun migrateLegacyKeys(session: IssuanceSession): Map<String, String> =
        session.issuanceRequests.mapNotNull { request ->
            (request.crypto2IssuerStoredKey ?: migrateLegacyKey(request))
                ?.let { request.credentialIdentifier to it }
        }.toMap()

    suspend fun attachStoredKeys(
        session: IssuanceSession,
        storedKeys: Map<String, String>,
    ): IssuanceSession = session.copy(
        issuanceRequests = session.issuanceRequests.map { request ->
            val stored = storedKeys[request.credentialIdentifier]
            when {
                stored != null && sidecarMatchesRequest(request, stored) ->
                    request.copy(crypto2IssuerStoredKey = stored)
                else -> request.copy(crypto2IssuerStoredKey = migrateLegacyKey(request))
            }
        }
    )

    suspend fun validateStoredKeys(session: IssuanceSession, storedKeys: Map<String, String>) {
        storedKeys.forEach { (credentialIdentifier, encoded) ->
            val request = requireNotNull(session.issuanceRequests.find {
                it.credentialIdentifier == credentialIdentifier
            }) { "Issuer2 crypto2 sidecar references an unknown credential identifier" }
            require(sidecarMatchesRequest(request, encoded)) {
                "Issuer2 crypto2 sidecar does not match the issuance request key"
            }
        }
    }

    suspend fun migrateLegacyKey(request: IssuanceRequest): String? {
        if (request.issuerKey["type"]?.jsonPrimitive?.content != "jwk") return null
        val legacyKey = KeyManager.resolveSerializedKey(request.issuerKey)
        val stored = migration.migrate(
            recordId = KeyId(legacyKey.getKeyId()),
            serialized = request.issuerKey,
            usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
        )
        crypto2Runtime.restore(stored)
        return StoredKeyCodec.encodeToString(stored)
    }

    suspend fun sidecarMatchesRequest(request: IssuanceRequest, encoded: String): Boolean {
        val stored = StoredKeyCodec.decodeFromString(encoded)
        val crypto2Key = crypto2Runtime.restore(stored)
        require(KeyUsage.SIGN in stored.usages) { "Issuer2 crypto2 sidecar key does not permit signing" }
        val legacyKey = KeyManager.resolveSerializedKey(request.issuerKey)
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
