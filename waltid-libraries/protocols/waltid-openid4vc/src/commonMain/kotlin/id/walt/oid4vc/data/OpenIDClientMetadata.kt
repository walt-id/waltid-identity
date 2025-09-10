package id.walt.oid4vc.data

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
@Serializable(with = OpenIDClientMetadataSerializer::class)
data class OpenIDClientMetadata(
    @SerialName("redirect_uris") val redirectUris: List<String>? = null,
    @SerialName("response_types") val responseTypes: List<String>? = null,
    @SerialName("grant_types") val grantTypes: List<GrantType>? = null,
    @SerialName("application_type") val applicationType: String? = null,
    @SerialName("contacts") val contacts: List<String>? = null,
    @SerialName("client_name") val clientName: String? = null,
    @SerialName("logo_uri") val logoUri: String? = null,
    @SerialName("client_uri") val clientUri: String? = null,
    @SerialName("policy_uri") val policyUri: String? = null,
    @SerialName("tos_uri") val tosUri: String? = null,
    @SerialName("jwks_uri") val jwksUri: String? = null,
    @SerialName("jwks") val jwks: JsonObject? = null,
    @SerialName("sector_identifier_uri") val sectorIdentifierUri: String? = null,
    @SerialName("subject_type") val subjectType: SubjectType? = null,
    @SerialName("id_token_signed_response_alg") val idTokenSignedResponseAlg: String? = null,
    @SerialName("id_token_encrypted_response_alg") val idTokenEncryptedResponseAlg: String? = null,
    @SerialName("id_token_encrypted_response_enc") val idTokenEncryptedResponseEnc: String? = null,
    @SerialName("userinfo_signed_response_alg") val userinfoSignedResponseAlg: String? = null,
    @SerialName("userinfo_encrypted_response_alg") val userinfoEncryptedResponseAlg: String? = null,
    @SerialName("userinfo_encrypted_response_enc") val userinfoEncryptedResponseEnc: String? = null,
    @SerialName("request_object_signing_alg") val requestObjectSigningAlg: String? = null,
    @SerialName("request_object_encryption_alg") val requestObjectEncryptionAlg: String? = null,
    @SerialName("request_object_encryption_enc") val requestObjectEncryptionEnc: String? = null,
    @SerialName("token_endpoint_auth_method") val tokenEndpointAuthMethod: String? = null,
    @SerialName("token_endpoint_auth_signing_alg") val tokenEndpointAuthSigningAlg: String? = null,
    @SerialName("authorization_encrypted_response_alg") val authorizationEncryptedResponseAlg: String? = null,
    @SerialName("authorization_encrypted_response_enc") val authorizationEncryptedResponseEnc: String? = null,
    @SerialName("default_max_age") val defaultMaxAge: Long? = null,
    @SerialName("require_auth_time") val requireAuthTime: Boolean? = null,
    @SerialName("default_acr_values") val defaultAcrValues: List<String>? = null,
    @SerialName("initiate_login_uri") val initiateLoginUri: String? = null,
    @SerialName("request_uris") val requestUris: List<String>? = null,
    override val customParameters: Map<String, JsonElement>? = mapOf()
) : JsonDataObject() {
    override fun toJSON() = Json.encodeToJsonElement(OpenIDClientMetadataSerializer, this).jsonObject

    companion object : JsonDataObjectFactory<OpenIDClientMetadata>() {
        override fun fromJSON(jsonObject: JsonObject): OpenIDClientMetadata =
            Json.decodeFromJsonElement(OpenIDClientMetadataSerializer, jsonObject)
    }
}

internal object OpenIDClientMetadataSerializer :
    JsonDataObjectSerializer<OpenIDClientMetadata>(OpenIDClientMetadata.generatedSerializer())
