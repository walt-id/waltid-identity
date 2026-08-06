package id.walt.issuer2.config

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.keys.*
import id.walt.crypto2.migration.v1.V1KeyMigration
import id.walt.crypto2.migration.v1.legacyKeyId
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.crypto2.serialization.StoredKeyCodec
import id.walt.openid4vci.requests.credential.encryption.CredentialEncryptionProfile
import id.walt.openid4vci.requests.credential.encryption.Crypto2JweCredentialRequestDecryptor
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.coroutines.cancellation.CancellationException

/**
 * Resolves the Credential Request decryption key from configuration.
 *
 * The configured value is either an encoded crypto2 [StoredKey] or a legacy v1 serialized key, which is migrated
 * in-memory. The key is only ever used for ECDH-ES key agreement, so it is restored with [KeyUsage.KEY_AGREEMENT]
 * alone - the crypto2 capability model then makes it structurally impossible to sign or decrypt anything else with it.
 */
object CredentialEncryptionKeyConfig {
    private const val VALIDATION_ERROR =
        "credentialEncryptionKey must contain an EC P-256 private key for ECDH-ES/A128GCM credential request encryption"

    /** ECDH-ES is a key-agreement algorithm, so the only usage this key ever needs. */
    private val encryptionKeyUsages = setOf(KeyUsage.KEY_AGREEMENT)

    fun validate(serializedKey: String) {
        publicMetadataJwk(serializedKey)
    }

    fun requestDecryptor(serializedKey: String): Crypto2JweCredentialRequestDecryptor =
        Crypto2JweCredentialRequestDecryptor(resolve { resolveKey(serializedKey).key })

    fun publicMetadataJwk(serializedKey: String): JsonObject = resolve {
        resolveKey(serializedKey).toCredentialRequestEncryptionJwk()
    }

    private data class ResolvedEncryptionKey(val key: Key, val keyId: String)

    private suspend fun resolveKey(serializedKey: String): ResolvedEncryptionKey {
        require(serializedKey.isNotBlank()) { "credentialEncryptionKey must not be blank when provided" }
        val stored = decodeStoredKey(serializedKey)
        require(stored.spec == KeySpec.Ec(EcCurve.P256)) { VALIDATION_ERROR }
        require(stored.usages == encryptionKeyUsages) {
            "credentialEncryptionKey usages must be exactly $encryptionKeyUsages"
        }
        val key = CryptoRuntime(defaultSoftwareKeyProviders()).restore(stored)
        // KEY_AGREEMENT is a private-key usage, so a public-only JWK cannot reach this point; assert it anyway
        // because a missing private half would otherwise only surface on the first decryption attempt.
        requireNotNull(key.capabilities.keyAgreement) { VALIDATION_ERROR }
        return ResolvedEncryptionKey(key, stored.publishedKeyId(key))
    }

    private suspend fun decodeStoredKey(serializedKey: String): StoredKey =
        if (serializedKey.looksLikeStoredKey()) {
            StoredKeyCodec.decodeFromString(serializedKey)
        } else {
            V1KeyMigration().migrate(
                // A v1 key's own identity is its published kid, which publishedKeyId() recovers from the migrated
                // metadata or the RFC 7638 thumbprint; the record id only has to be stable and non-blank.
                recordId = KeyId(CREDENTIAL_ENCRYPTION_RECORD_ID),
                serialized = serializedKey,
                usages = encryptionKeyUsages,
            )
        }

    /**
     * A crypto2 StoredKey always carries a `version` member; a v1 serialized key always carries `type`. Discriminating
     * on the presence of `version` avoids guessing and gives a clear error for anything that is neither.
     */
    private fun String.looksLikeStoredKey(): Boolean =
        (Json.parseToJsonElement(this) as? JsonObject)?.containsKey("version") == true

    /**
     * OpenID4VCI 1.0 section 8.3 requires the JWE to repeat the recipient JWK's `kid`, so this value ends up in
     * already-published Credential Issuer Metadata. Keep the v1 `_keyId` when the key was migrated from one, otherwise
     * use the RFC 7638 thumbprint - which is what v1 published for keys without an explicit `_keyId`.
     */
    private suspend fun StoredKey.publishedKeyId(key: Key): String =
        legacyKeyId() ?: Jwk.sha256Thumbprint(key.publicJwk())

    private suspend fun Key.publicJwk(): EncodedKey.Jwk =
        requireNotNull(capabilities.publicKeyExporter) { VALIDATION_ERROR }
            .exportPublicKey()
            .toPublicJwk(spec)

    private suspend fun ResolvedEncryptionKey.toCredentialRequestEncryptionJwk(): JsonObject {
        val publicJwk = Jwk.parse(key.publicJwk())
        require(!Jwk.containsPrivateMaterial(publicJwk)) { VALIDATION_ERROR }

        val encryptionJwk = buildJsonObject {
            publicJwk.forEach { (name, value) -> put(name, value) }
            put("kid", JsonPrimitive(keyId))
            put("alg", CredentialEncryptionProfile.ALG_ECDH_ES)
            put("use", CredentialEncryptionProfile.KEY_USE_ENC)
        }

        require(CredentialEncryptionProfile.isSupportedCredentialRequestEncryptionJwk(encryptionJwk)) {
            VALIDATION_ERROR
        }
        return encryptionJwk
    }

    private fun <T> resolve(block: suspend () -> T): T =
        try {
            // Startup-time configuration resolution on the JVM only; the service has no coroutine scope yet.
            runBlocking { block() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw IllegalArgumentException(VALIDATION_ERROR, e)
        }

    private const val CREDENTIAL_ENCRYPTION_RECORD_ID = "credential-request-encryption"
}
