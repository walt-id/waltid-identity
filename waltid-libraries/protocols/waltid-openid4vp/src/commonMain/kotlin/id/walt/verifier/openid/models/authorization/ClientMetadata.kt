package id.walt.verifier.openid.models.authorization

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Represents the 'client_metadata' parameter.
 * See: Section 5.1
 */
@Serializable
data class ClientMetadata(
    /**
     * OPTIONAL. A JSON Web Key Set [RFC7517] that contains one or more public keys,
     * such as those used by the Wallet for response encryption.
     */
    val jwks: Jwks? = null, // Representing a JWKSet as a JsonObject for flexibility

    /**
     * REQUIRED when not available to the Wallet via another mechanism.
     * An object defining the formats and proof types of Presentations and Credentials
     * that a Verifier supports. (Corresponds to vp_formats_supported in Verifier Metadata - Section 11.1)
     */
    /*
    * Keys are format strings (ideally matching CredentialFormat.serialName);
     * values are JsonObjects describing capabilities for that format.
     * Using Map<String, JsonObject> for flexibility as CredentialFormat enum might not cover all custom formats.
     * If strict enum usage is desired for keys, a custom serializer for the map would be needed.
     */
    @SerialName("vp_formats_supported")
    val vpFormatsSupported: Map<String, JsonObject>? = null, // Key: format string, Value: format-specific capabilities object

    /**
     * OPTIONAL. Array of strings, where each string is a JWE `enc` algorithm
     * that can be used as the content encryption algorithm for encrypting the Response.
     * See Section 5.1
     */
    @SerialName("encrypted_response_enc_values_supported")
    val encryptedResponseEncValuesSupported: List<String>? = null,

    // Other potential client metadata fields as defined by profiles or extensions
    @SerialName("client_name")
    val clientName: String? = null,
    @SerialName("logo_uri")
    val logoUri: String? = null,
    // ... etc.
) {
    /** keys are JWK */
    @Serializable
    data class Jwks(val keys: List<JsonObject>)

    companion object {
        private val jsonParser = Json { ignoreUnknownKeys = true }
        fun fromJson(jsonString: String): Result<ClientMetadata> {
            return runCatching {
                jsonParser.decodeFromString<ClientMetadata>(jsonString)
            }
        }
    }

}
