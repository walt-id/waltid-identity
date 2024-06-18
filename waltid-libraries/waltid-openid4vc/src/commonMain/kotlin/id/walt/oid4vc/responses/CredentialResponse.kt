package id.walt.oid4vc.responses

import id.walt.oid4vc.data.CredentialFormat
import id.walt.oid4vc.data.JsonDataObject
import id.walt.oid4vc.data.JsonDataObjectFactory
import id.walt.oid4vc.data.JsonDataObjectSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import kotlin.time.Duration

@Serializable
data class CredentialResponse private constructor(
    val format: CredentialFormat? = null,
    val credential: JsonElement? = null,
    @SerialName("acceptance_token") val acceptanceToken: String? = null,
    @SerialName("c_nonce") val cNonce: String? = null,
    @SerialName("c_nonce_expires_in") val cNonceExpiresIn: Duration? = null,
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
    @SerialName("error_uri") val errorUri: String? = null,
    override val customParameters: Map<String, JsonElement> = mapOf()
) : JsonDataObject() {
    val isSuccess get() = (format != null && credential != null) || isDeferred
    val isDeferred get() = acceptanceToken != null
    override fun toJSON() = Json.encodeToJsonElement(CredentialResponseSerializer, this).jsonObject

    companion object : JsonDataObjectFactory<CredentialResponse>() {
        override fun fromJSON(jsonObject: JsonObject) =
            Json.decodeFromJsonElement(CredentialResponseSerializer, jsonObject)

        fun success(
            format: CredentialFormat,
            credential: String,
            cNonce: String? = null,
            cNonceExpiresIn: Duration? = null
        ) = CredentialResponse(format, JsonPrimitive(credential), null, cNonce, cNonceExpiresIn)

        fun success(
            format: CredentialFormat,
            credential: JsonElement,
            cNonce: String? = null,
            cNonceExpiresIn: Duration? = null
        ) = CredentialResponse(format, credential, null, cNonce, cNonceExpiresIn)

        fun deferred(
            format: CredentialFormat,
            acceptanceToken: String,
            cNonce: String? = null,
            cNonceExpiresIn: Duration? = null
        ) = CredentialResponse(format, null, acceptanceToken, cNonce, cNonceExpiresIn)

        fun error(
            error: CredentialErrorCode,
            errorDescription: String? = null,
            errorUri: String? = null,
            cNonce: String? = null,
            cNonceExpiresIn: Duration? = null
        ) = CredentialResponse(
            error = error.name,
            errorDescription = errorDescription,
            errorUri = errorUri,
            cNonce = cNonce,
            cNonceExpiresIn = cNonceExpiresIn
        )
    }
}

object CredentialResponseSerializer : JsonDataObjectSerializer<CredentialResponse>(CredentialResponse.serializer())

enum class CredentialErrorCode {
    invalid_request,
    invalid_token,
    insufficient_scope,
    unsupported_credential_type,
    unsupported_credential_format,
    invalid_or_missing_proof,
    server_error
}

object CredentialResponseListSerializer : KSerializer<List<CredentialResponse>> {
    private val internalSerializer = ListSerializer(CredentialResponseSerializer)
    override val descriptor: SerialDescriptor = internalSerializer.descriptor
    override fun deserialize(decoder: Decoder): List<CredentialResponse> = internalSerializer.deserialize(decoder)
    override fun serialize(encoder: Encoder, value: List<CredentialResponse>) =
        internalSerializer.serialize(encoder, value)
}
