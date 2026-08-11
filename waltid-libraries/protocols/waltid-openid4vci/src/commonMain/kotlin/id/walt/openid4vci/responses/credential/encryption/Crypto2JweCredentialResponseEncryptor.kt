package id.walt.openid4vci.responses.credential.encryption

import id.walt.crypto2.jose.CompactJwe
import id.walt.crypto2.jose.JweContentEncryption
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.serialization.BinaryData
import id.walt.openid4vci.requests.credential.encryption.CredentialEncryptionProfile
import id.walt.openid4vci.requests.credential.encryption.CredentialResponseEncryptionParameters
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Encrypts a Credential Response as the compact JWE required by OpenID4VCI 1.0 section 8.3 (Encrypted Credential
 * Requests and Responses), using crypto2/waltid-jose instead of the JVM-only crypto1 Nimbus bridge.
 *
 * The spec requires that the JWE `alg` equal the `alg` member of the chosen recipient JWK, and that the JWE carry the
 * recipient's `kid` whenever the JWK has one so the wallet can identify the key it must decrypt with.
 * [CredentialResponseEncryptionParameters] already pins `alg` to ECDH-ES and `enc` to A128GCM, so the only thing left
 * to carry over here is `kid`.
 */
object Crypto2JweCredentialResponseEncryptor : CredentialResponseEncryptor {
    override suspend fun encrypt(
        payload: JsonObject,
        encryption: CredentialResponseEncryptionParameters,
    ): String = CompactJwe.encrypt(
        plaintext = payload.toString().encodeToByteArray(),
        recipientPublicKey = EncodedKey.Jwk(
            data = BinaryData(encryption.jwk.toString().encodeToByteArray()),
            // requireSupportedEncryptionJwk() rejects any JWK carrying "d", so this flag cannot misdescribe it.
            privateMaterial = false,
        ),
        contentEncryption = encryption.enc.toJweContentEncryption(),
        protectedHeader = buildJsonObject {
            encryption.jwk["kid"]?.jsonPrimitive?.contentOrNull?.let { kid ->
                put("kid", JsonPrimitive(kid))
            }
        },
    )

    private fun String.toJweContentEncryption(): JweContentEncryption {
        require(this in CredentialEncryptionProfile.encValuesSupported) {
            "credential_response_encryption.enc must be one of ${CredentialEncryptionProfile.encValuesSupported}"
        }
        return JweContentEncryption.parse(this)
    }
}
