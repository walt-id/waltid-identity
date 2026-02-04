package id.walt.openid4vci.metadata.issuer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Credential Request encryption metadata (OpenID4VCI 1.0).
 */
@Serializable
data class CredentialRequestEncryption(
    val jwks: JsonObject,
    @SerialName("enc_values_supported")
    val encValuesSupported: Set<String>,
    @SerialName("zip_values_supported")
    val zipValuesSupported: Set<String>? = null,
    @SerialName("encryption_required")
    val encryptionRequired: Boolean,
) {
    init {
        require(encValuesSupported.isNotEmpty()) {
            "credential_request_encryption.enc_values_supported must not be empty"
        }
        require(encValuesSupported.none { it.isBlank() }) {
            "credential_request_encryption.enc_values_supported must not contain blank entries"
        }
        zipValuesSupported?.let { values ->
            require(values.isNotEmpty()) {
                "credential_request_encryption.zip_values_supported must not be empty"
            }
            require(values.none { it.isBlank() }) {
                "credential_request_encryption.zip_values_supported must not contain blank entries"
            }
        }
        require(jwksHasKids(jwks)) {
            "credential_request_encryption.jwks must contain keys with kid"
        }
    }

    private fun jwksHasKids(jwks: JsonObject): Boolean {
        val keys = jwks["keys"] as? JsonArray ?: return false
        return keys.all { element ->
            val key = element.jsonObject
            val kid = key["kid"] as? JsonPrimitive
            kid?.content?.isNotBlank() == true
        }
    }
}
