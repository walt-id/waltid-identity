package id.walt.openid4vci.requests.credential.encryption

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object CredentialEncryptionProfile {
    const val ALG_ECDH_ES = "ECDH-ES"
    const val ENC_A128GCM = "A128GCM"
    const val CURVE_P256 = "P-256"
    const val KEY_TYPE_EC = "EC"
    const val KEY_USE_ENC = "enc"
    const val MEDIA_TYPE_JWT = "application/jwt"

    val responseAlgValuesSupported = setOf(ALG_ECDH_ES)
    val encValuesSupported = setOf(ENC_A128GCM)

    fun isSupportedCredentialRequestEncryptionJwk(jwk: JsonObject): Boolean =
        runCatching {
            requireSupportedEncryptionJwk(
                jwk = jwk,
                fieldName = "credential_request_encryption.jwks.keys[]",
                requireKid = true,
            )
        }.isSuccess

    fun isSupportedCredentialRequestDecryptionJwk(jwk: String): Boolean =
        runCatching {
            isSupportedCredentialRequestDecryptionJwk(Json.parseToJsonElement(jwk).jsonObject)
        }.getOrDefault(false)

    fun isSupportedCredentialRequestDecryptionJwk(jwk: JsonObject): Boolean =
        jwk.string("kty") == KEY_TYPE_EC &&
                jwk.string("crv") == CURVE_P256 &&
                jwk.string("x")?.isNotBlank() == true &&
                jwk.string("y")?.isNotBlank() == true &&
                jwk.string("d")?.isNotBlank() == true

    internal fun requireSupportedHeader(header: JsonObject, fieldName: String = "jwe") {
        require(header.string("alg") == ALG_ECDH_ES) {
            "$fieldName alg must be $ALG_ECDH_ES"
        }
        require(header.string("enc") == ENC_A128GCM) {
            "$fieldName enc must be $ENC_A128GCM"
        }
        require("zip" !in header) {
            "$fieldName zip is not supported"
        }
    }

    internal fun requireSupportedEncryptionJwk(
        jwk: JsonObject,
        fieldName: String,
        requireKid: Boolean,
    ) {
        require("d" !in jwk) {
            "$fieldName.jwk must be a public key"
        }
        val kid = jwk.string("kid")
        require(!requireKid || !kid.isNullOrBlank()) {
            "$fieldName.jwk.kid is required"
        }
        require("kid" !in jwk || !kid.isNullOrBlank()) {
            "$fieldName.jwk.kid must not be blank"
        }
        require(jwk.string("kty") == KEY_TYPE_EC) {
            "$fieldName.jwk.kty must be $KEY_TYPE_EC"
        }
        require(jwk.string("crv") == CURVE_P256) {
            "$fieldName.jwk.crv must be $CURVE_P256"
        }
        require(jwk.string("x")?.isNotBlank() == true) {
            "$fieldName.jwk.x is required"
        }
        require(jwk.string("y")?.isNotBlank() == true) {
            "$fieldName.jwk.y is required"
        }
        require(jwk.string("alg") == ALG_ECDH_ES) {
            "$fieldName.jwk.alg must be $ALG_ECDH_ES"
        }
        jwk.string("use")?.let { use ->
            require(use == KEY_USE_ENC) {
                "$fieldName.jwk.use must be $KEY_USE_ENC"
            }
        }
    }

    internal fun JsonObject.string(name: String): String? =
        this[name]?.jsonPrimitive?.contentOrNull
}
