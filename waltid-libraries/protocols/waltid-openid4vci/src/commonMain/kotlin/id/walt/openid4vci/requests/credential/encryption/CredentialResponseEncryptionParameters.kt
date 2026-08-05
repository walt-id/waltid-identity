package id.walt.openid4vci.requests.credential.encryption

import id.walt.openid4vci.requests.credential.encryption.CredentialEncryptionProfile.ALG_ECDH_ES
import id.walt.openid4vci.requests.credential.encryption.CredentialEncryptionProfile.ENC_A128GCM
import id.walt.openid4vci.requests.credential.encryption.CredentialEncryptionProfile.requireSupportedEncryptionJwk
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class CredentialResponseEncryptionParameters(
    val jwk: JsonObject,
    val enc: String,
    val zip: String? = null,
) {
    val alg: String
        get() = jwk["alg"]?.jsonPrimitive?.contentOrNull.orEmpty()

    init {
        requireSupportedEncryptionJwk(
            jwk = jwk,
            fieldName = "credential_response_encryption",
            requireKid = false,
        )
        require(alg == ALG_ECDH_ES) {
            "credential_response_encryption.jwk.alg must be $ALG_ECDH_ES"
        }
        require(enc == ENC_A128GCM) {
            "credential_response_encryption.enc must be $ENC_A128GCM"
        }
    }

    companion object {
        fun fromJsonObject(value: JsonObject): CredentialResponseEncryptionParameters {
            val jwk = value["jwk"]?.jsonObject
                ?: throw IllegalArgumentException("credential_response_encryption.jwk is required")
            val enc = value["enc"]?.jsonPrimitive?.contentOrNull
                ?: throw IllegalArgumentException("credential_response_encryption.enc is required")
            val zip = value["zip"]?.jsonPrimitive?.contentOrNull
            return CredentialResponseEncryptionParameters(jwk = jwk, enc = enc, zip = zip)
        }
    }
}
