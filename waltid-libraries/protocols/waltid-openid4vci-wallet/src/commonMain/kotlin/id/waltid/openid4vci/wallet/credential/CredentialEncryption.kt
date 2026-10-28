package id.waltid.openid4vci.wallet.credential

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJwe
import id.walt.crypto2.jose.JweContentEncryption
import id.walt.crypto2.jose.exportPublicJwkObject
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.crypto2.serialization.BinaryData
import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadata
import id.walt.openid4vci.metadata.issuer.CredentialResponseEncryption
import id.walt.openid4vci.requests.credential.encryption.CredentialEncryptionProfile
import io.ktor.http.ContentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.uuid.Uuid

/**
 * Wallet-side OpenID4VCI credential encryption (section 8.3).
 *
 * A Credential Response can be encrypted only when the Credential Request itself is an encrypted
 * JWE. When issuer metadata advertises the wallet profile (EC P-256, ECDH-ES, A128GCM), the wallet
 * encrypts the request and asks for an encrypted response. Required encryption that the wallet
 * cannot perform fails before a plaintext request is sent.
 */
object CredentialEncryption {
    private val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun prepare(
        payload: JsonObject,
        metadata: CredentialIssuerMetadata?,
    ): PreparedCredentialRequest {
        val recipient = metadata?.credentialRequestEncryption?.jwks?.supportedRecipient()
        val response = metadata?.credentialResponseEncryption
        val responseSupported = response?.supportsWalletProfile() == true
        val requestRequired = metadata?.credentialRequestEncryption?.encryptionRequired == true
        val responseRequired = response?.encryptionRequired == true

        if (responseRequired && !responseSupported) {
            throw IllegalArgumentException(
                "Issuer requires credential response encryption, but the advertised algorithms are not ECDH-ES and A128GCM",
            )
        }
        if ((requestRequired || responseRequired) && recipient == null) {
            throw IllegalArgumentException(
                "Issuer requires credential encryption, but credential_request_encryption.jwks contains no supported EC P-256 key",
            )
        }
        if (recipient == null || (!requestRequired && !responseSupported)) {
            return PreparedCredentialRequest(payload.toString(), ContentType.Application.Json)
        }

        val responseKey = if (responseSupported) responseEncryptionKey() else null
        val requestPayload = if (responseKey == null) {
            payload
        } else {
            buildJsonObject {
                payload.forEach { (name, value) -> put(name, value) }
                put("credential_response_encryption", buildJsonObject {
                    put("jwk", responseKey.publicJwk())
                    put("enc", JsonPrimitive(CredentialEncryptionProfile.ENC_A128GCM))
                })
            }
        }
        val compactJwe = CompactJwe.encrypt(
            plaintext = requestPayload.toString().encodeToByteArray(),
            recipientPublicKey = EncodedKey.Jwk(
                data = BinaryData(recipient.toString().encodeToByteArray()),
                privateMaterial = false,
            ),
            contentEncryption = JweContentEncryption.A128GCM,
            protectedHeader = buildJsonObject {
                put("kid", JsonPrimitive(recipient.requiredString("kid")))
            },
        )
        return PreparedCredentialRequest(
            body = compactJwe,
            contentType = ContentType.parse(CredentialEncryptionProfile.MEDIA_TYPE_JWT),
            responseDecryptionKey = responseKey,
        )
    }

    suspend fun decryptResponse(compactJwe: String, responseKey: Key): String {
        val decrypted = CompactJwe.decrypt(
            compactJwe = compactJwe,
            recipientKey = responseKey,
            allowedContentEncryptions = setOf(JweContentEncryption.A128GCM),
        )
        val header = decrypted.protectedHeader
        require(header.string("alg") == CredentialEncryptionProfile.ALG_ECDH_ES) {
            "Credential response JWE alg must be ${CredentialEncryptionProfile.ALG_ECDH_ES}"
        }
        require(header.string("enc") == CredentialEncryptionProfile.ENC_A128GCM) {
            "Credential response JWE enc must be ${CredentialEncryptionProfile.ENC_A128GCM}"
        }
        require("zip" !in header) { "Credential response compression is not supported" }
        header.string("kid")?.let { kid ->
            require(kid == responseKey.id.value) {
                "Credential response JWE kid does not match the wallet encryption key"
            }
        }
        val payload = decrypted.plaintext.decodeToString(throwOnInvalidSequence = true)
        require(json.parseToJsonElement(payload) is JsonObject) {
            "Credential response payload must be a JSON object"
        }
        return payload
    }

    private suspend fun responseEncryptionKey(): Key = runtime.generateSoftwareKey(
        GenerateSoftwareKeyRequest(
            id = KeyId("credential-response-${Uuid.random()}"),
            spec = KeySpec.Ec(EcCurve.P256),
            usages = setOf(KeyUsage.KEY_AGREEMENT),
        ),
    )

    private suspend fun Key.publicJwk(): JsonObject = buildJsonObject {
        exportPublicJwkObject().forEach { (name, value) ->
            if (name != "d") put(name, value)
        }
        put("kid", JsonPrimitive(id.value))
        put("alg", JsonPrimitive(CredentialEncryptionProfile.ALG_ECDH_ES))
        put("use", JsonPrimitive(CredentialEncryptionProfile.KEY_USE_ENC))
    }
}

/** Outbound Credential Request body. [responseDecryptionKey] is set only when the response must be a JWE. */
class PreparedCredentialRequest(
    val body: String,
    val contentType: ContentType,
    val responseDecryptionKey: Key? = null,
) {
    override fun toString(): String =
        "PreparedCredentialRequest(contentType=$contentType, encryptedResponse=${responseDecryptionKey != null})"
}

private fun CredentialResponseEncryption.supportsWalletProfile(): Boolean =
    CredentialEncryptionProfile.ALG_ECDH_ES in algValuesSupported &&
        CredentialEncryptionProfile.ENC_A128GCM in encValuesSupported

private fun JsonObject.supportedRecipient(): JsonObject? {
    val keys = this["keys"] as? JsonArray ?: return null
    return keys.firstNotNullOfOrNull { element ->
        val key = element as? JsonObject ?: return@firstNotNullOfOrNull null
        key.takeIf(CredentialEncryptionProfile::isSupportedCredentialRequestEncryptionJwk)
    }
}

private fun JsonObject.string(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull

private fun JsonObject.requiredString(name: String): String =
    string(name)?.takeIf { it.isNotBlank() } ?: error("Missing $name")
