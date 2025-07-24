package id.walt.oid4vc.requests

import id.walt.oid4vc.data.*
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
@Serializable(with = AuthorizationJSONRequestSerializer::class)
data class AuthorizationJSONRequest(
    @SerialName("response_type") override val responseType: Set<ResponseType> = setOf(ResponseType.Code),
    @SerialName("client_id") override val clientId: String,
    @SerialName("response_mode") override val responseMode: ResponseMode? = null,
    @SerialName("redirect_uri") override val redirectUri: String? = null,
    override val scope: Set<String> = setOf(),
    override val state: String? = null,
    override val nonce: String? = null,
    override val customParameters: Map<String, JsonElement>? = mapOf()
) : JsonDataObject(), IAuthorizationRequest {
    override fun toJSON() = Json.encodeToJsonElement(AuthorizationJSONRequestSerializer, this).jsonObject

    companion object : JsonDataObjectFactory<AuthorizationJSONRequest>() {
        override fun fromJSON(jsonObject: JsonObject): AuthorizationJSONRequest =
            Json.decodeFromJsonElement(AuthorizationJSONRequestSerializer, jsonObject)
    }
}

internal object AuthorizationJSONRequestSerializer :
    JsonDataObjectSerializer<AuthorizationJSONRequest>(AuthorizationJSONRequest.generatedSerializer())
