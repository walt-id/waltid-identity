package id.waltid.openid4vp.wallet.request

import id.walt.crypto.utils.UuidUtils
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJwe
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JweContentEncryption
import id.walt.crypto2.jose.exportPublicJwkObject
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import kotlinx.serialization.json.*

/** One request_uri POST exchange owns this key; it is never persisted or shared. */
internal class RequestObjectEncryption private constructor(private val key: Key) {
    suspend fun walletMetadata(metadata: String): String = buildJsonObject {
        // These declarations must describe the key owned by this exchange, not caller-supplied keys.
        Json.parseToJsonElement(metadata).jsonObject.filterKeys { it != "jwks_uri" }.forEach { (name, value) -> put(name, value) }
        put("jwks", buildJsonObject {
            put("keys", buildJsonArray {
                add(buildJsonObject {
                    key.exportPublicJwkObject().forEach { (name, value) -> put(name, value) }
                    put("kid", key.id.value)
                    put("use", "enc")
                    put("alg", "ECDH-ES")
                })
            })
        })
        put("request_object_encryption_alg_values_supported", buildJsonArray { add("ECDH-ES") })
        put("request_object_encryption_enc_values_supported", buildJsonArray {
            contentEncryptions.forEach { add(it.identifier) }
        })
    }.toString()

    suspend fun decrypt(requestObject: String): String {
        val decrypted = CompactJwe.decrypt(requestObject, key, contentEncryptions)
        val header = decrypted.protectedHeader
        val contentType = header["cty"] as? JsonPrimitive
        require(contentType?.isString == true &&
            contentType.content.equals("JWT", ignoreCase = true)) {
            "Encrypted Request Object must have JWT content type"
        }
        header["kid"]?.let { kid ->
            require(kid is JsonPrimitive && kid.isString && kid.content == key.id.value) {
                "Encrypted Request Object does not identify this exchange's key"
            }
        }
        val signedRequest = decrypted.plaintext.decodeToString(throwOnInvalidSequence = true)
        // RFC 9101 section 6.1: decryption must yield a signed Request Object, even when
        // the wallet otherwise allows unsigned redirect_uri or pre-registered requests.
        val algorithm = CompactJws.decodeUnverified(signedRequest).protectedHeader["alg"] as? JsonPrimitive
        require(algorithm?.isString == true && algorithm.content.isNotBlank() &&
            !algorithm.content.equals("none", ignoreCase = true)) {
            "Encrypted Request Object must contain a signed JWT"
        }
        return signedRequest // The resolver still authenticates the signature, identity and wallet_nonce.
    }

    companion object {
        private val contentEncryptions = setOf(JweContentEncryption.A128GCM, JweContentEncryption.A256GCM)

        suspend fun create(): RequestObjectEncryption = RequestObjectEncryption(
            CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(
                GenerateSoftwareKeyRequest(
                    id = KeyId(UuidUtils.randomUUIDString()),
                    spec = KeySpec.Ec(EcCurve.P256),
                    usages = setOf(KeyUsage.KEY_AGREEMENT),
                ),
            ),
        )
    }
}
