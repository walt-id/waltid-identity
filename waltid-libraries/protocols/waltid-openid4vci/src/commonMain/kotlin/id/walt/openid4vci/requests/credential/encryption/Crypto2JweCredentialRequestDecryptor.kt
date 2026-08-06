package id.walt.openid4vci.requests.credential.encryption

import id.walt.crypto2.jose.CompactJwe
import id.walt.crypto2.jose.JweContentEncryption
import id.walt.crypto2.keys.Key
import id.walt.openid4vci.requests.credential.encryption.CredentialEncryptionProfile.requireSupportedHeader
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Decrypts a Credential Request compact JWE with a crypto2 key, replacing the JVM-only crypto1 Nimbus bridge.
 *
 * The decryption key never leaves the [Key], so this works unchanged for software keys, a device keystore, or a remote
 * KMS. [CompactJwe] enforces the JOSE-level constraints (ECDH-ES only, empty encrypted-key part, 96-bit IV, 128-bit
 * tag, no `zip`/`crit`); [requireSupportedHeader] then enforces the narrower OpenID4VCI profile that the issuer
 * advertises in its metadata, so a request cannot downgrade to an `enc` the issuer never published.
 */
class Crypto2JweCredentialRequestDecryptor(
    private val decryptionKey: Key,
    private val allowedContentEncryptions: Set<JweContentEncryption> = defaultContentEncryptions,
) : CredentialRequestDecryptor {

    init {
        require(allowedContentEncryptions.isNotEmpty()) {
            "At least one credential request content-encryption algorithm is required"
        }
        require(decryptionKey.capabilities.keyAgreement != null) {
            "Credential request decryption key must permit key agreement"
        }
    }

    override suspend fun decrypt(compactJwe: String): JsonObject {
        val decrypted = CompactJwe.decrypt(
            compactJwe = compactJwe,
            recipientKey = decryptionKey,
            allowedContentEncryptions = allowedContentEncryptions,
        )
        requireSupportedHeader(decrypted.protectedHeader, "credential request")
        return Json.parseToJsonElement(decrypted.plaintext.decodeToString(throwOnInvalidSequence = true)) as? JsonObject
            ?: throw IllegalArgumentException("credential request payload must be a JSON object")
    }

    private companion object {
        val defaultContentEncryptions = CredentialEncryptionProfile.encValuesSupported
            .mapTo(mutableSetOf(), JweContentEncryption::parse)
    }
}
