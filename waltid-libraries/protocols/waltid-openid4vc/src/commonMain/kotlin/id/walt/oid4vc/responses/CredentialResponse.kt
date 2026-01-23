package id.walt.oid4vc.responses

import id.walt.oid4vc.data.CredentialFormat
import id.walt.oid4vc.data.JsonDataObject
import id.walt.oid4vc.data.JsonDataObjectFactory
import id.walt.oid4vc.data.JsonDataObjectSerializer
import kotlinx.serialization.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
@Serializable(with = CredentialResponseSerializer::class)
data class CredentialResponse(
    val format: CredentialFormat? = null,
    val credential: JsonElement? = null,
    @SerialName("acceptance_token") val acceptanceToken: String? = null,
    @SerialName("c_nonce") val cNonce: String? = null,
    @SerialName("c_nonce_expires_in") @Serializable(with = DurationAsSecondsSerializer::class) val cNonceExpiresIn: Duration? = null,
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
    @SerialName("error_uri") val errorUri: String? = null,
    override val customParameters: Map<String, JsonElement>? = mapOf()
) : JsonDataObject() {
    val isSuccess get() = (format != null && credential != null) || isDeferred
    val isDeferred get() = acceptanceToken != null
    override fun toJSON() = Json.encodeToJsonElement(CredentialResponseSerializer, this).jsonObject

    companion object : JsonDataObjectFactory<CredentialResponse>() {
        override fun fromJSON(jsonObject: JsonObject): CredentialResponse =
            Json.decodeFromJsonElement(CredentialResponseSerializer, jsonObject)

        fun success(
            format: CredentialFormat,
            credentialString: String,
            cNonce: String? = null,
            cNonceExpiresIn: Duration? = null,
            customParameters: Map<String, JsonElement> = mapOf()
        ) = CredentialResponse(
            format,
            JsonPrimitive(credentialString),
            null,
            cNonce,
            cNonceExpiresIn,
            customParameters = customParameters
        )

        fun success(
            format: CredentialFormat,
            credential: JsonElement,
            cNonce: String? = null,
            cNonceExpiresIn: Duration? = null,
            customParameters: Map<String, JsonElement> = mapOf()
        ) = CredentialResponse(format, credential, null, cNonce, cNonceExpiresIn, customParameters = customParameters)

        fun deferred(
            format: CredentialFormat,
            acceptanceToken: String,
            cNonce: String? = null,
            cNonceExpiresIn: Duration? = null,
            customParameters: Map<String, JsonElement> = mapOf()
        ) = CredentialResponse(
            format,
            null,
            acceptanceToken,
            cNonce,
            cNonceExpiresIn,
            customParameters = customParameters
        )

        fun error(
            error: CredentialErrorCode,
            errorDescription: String? = null,
            errorUri: String? = null,
            cNonce: String? = null,
            cNonceExpiresIn: Duration? = null,
            customParameters: Map<String, JsonElement> = mapOf()
        ) = CredentialResponse(
            error = error.name,
            errorDescription = errorDescription,
            errorUri = errorUri,
            cNonce = cNonce,
            cNonceExpiresIn = cNonceExpiresIn,
            customParameters = customParameters
        )
    }
}

internal object CredentialResponseSerializer :
    JsonDataObjectSerializer<CredentialResponse>(CredentialResponse.generatedSerializer())

enum class CredentialErrorCode {
    invalid_request,
    invalid_token,
    insufficient_scope,
    unsupported_credential_type,
    unsupported_credential_format,
    invalid_or_missing_proof,
    server_error
}

internal object CredentialResponseListSerializer : KSerializer<List<CredentialResponse>> {
    private val internalSerializer = ListSerializer(CredentialResponseSerializer)
    override val descriptor: SerialDescriptor = internalSerializer.descriptor
    override fun deserialize(decoder: Decoder): List<CredentialResponse> = internalSerializer.deserialize(decoder)
    override fun serialize(encoder: Encoder, value: List<CredentialResponse>) =
        internalSerializer.serialize(encoder, value)
}

internal object DurationAsSecondsSerializer : KSerializer<Duration> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("DurationAsSeconds", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: Duration) {
        encoder.encodeInt(value.inWholeSeconds.toInt())
    }

    override fun deserialize(decoder: Decoder): Duration {
        return decoder.decodeInt().seconds
    }
}
