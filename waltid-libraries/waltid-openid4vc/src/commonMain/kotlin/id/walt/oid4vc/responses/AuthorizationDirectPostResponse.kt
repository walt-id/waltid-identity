package id.walt.oid4vc.responses

import id.walt.oid4vc.data.JsonDataObject
import id.walt.oid4vc.data.JsonDataObjectFactory
import id.walt.oid4vc.data.JsonDataObjectSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

@Serializable
data class AuthorizationDirectPostResponse(
    @SerialName("redirect_uri") val redirectUri: String? = null,
    val error: String?,
    @SerialName("error_description") val errorDescription: String?,
    override val customParameters: Map<String, JsonElement> = mapOf()
) : JsonDataObject() {

    override fun toJSON() = Json.encodeToJsonElement(AuthorizationDirectPostResponseSerializer, this).jsonObject

    companion object : JsonDataObjectFactory<AuthorizationDirectPostResponse>() {
        override fun fromJSON(jsonObject: JsonObject) =
            Json.decodeFromJsonElement(AuthorizationDirectPostResponseSerializer, jsonObject)
    }
}

object AuthorizationDirectPostResponseSerializer :
    JsonDataObjectSerializer<AuthorizationDirectPostResponse>(AuthorizationDirectPostResponse.serializer())
