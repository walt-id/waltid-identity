package id.walt.oid4vc.responses

import id.walt.oid4vc.data.JsonDataObject
import id.walt.oid4vc.data.JsonDataObjectFactory
import id.walt.oid4vc.data.JsonDataObjectSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.time.Duration

@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
@Serializable(with = PushedAuthorizationResponseSerializer::class)
@ConsistentCopyVisibility
data class PushedAuthorizationResponse private constructor(
    @SerialName("request_uri") val requestUri: String? = null,
    @SerialName("expires_in") val expiresIn: Duration? = null,
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
    override val customParameters: Map<String, JsonElement>? = mapOf()
) : JsonDataObject() {
    val isSuccess get() = requestUri != null
    override fun toJSON(): JsonObject = Json.encodeToJsonElement(PushedAuthorizationResponseSerializer, this).jsonObject

    companion object : JsonDataObjectFactory<PushedAuthorizationResponse>() {
        override fun fromJSON(jsonObject: JsonObject): PushedAuthorizationResponse =
            Json.decodeFromJsonElement(PushedAuthorizationResponseSerializer, jsonObject)

        fun success(requestUri: String, expiresIn: Duration) =
            PushedAuthorizationResponse(requestUri, expiresIn, null, null)

        fun error(error: AuthorizationErrorCode, errorDescription: String? = null) =
            PushedAuthorizationResponse(null, null, error.name, errorDescription)
    }
}

internal object PushedAuthorizationResponseSerializer :
    JsonDataObjectSerializer<PushedAuthorizationResponse>(PushedAuthorizationResponse.generatedSerializer())
