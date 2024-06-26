package id.walt.oid4vc.requests

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
data class BatchCredentialRequest(
    @SerialName("credential_requests") @Serializable(CredentialRequestListSerializer::class) val credentialRequests: List<CredentialRequest>,
    override val customParameters: Map<String, JsonElement> = mapOf()
) : JsonDataObject() {
    override fun toJSON() = Json.encodeToJsonElement(BatchCredentialRequestSerializer, this).jsonObject

    companion object : JsonDataObjectFactory<BatchCredentialRequest>() {
        override fun fromJSON(jsonObject: JsonObject) =
            Json.decodeFromJsonElement(BatchCredentialRequestSerializer, jsonObject)
    }
}

object BatchCredentialRequestSerializer :
    JsonDataObjectSerializer<BatchCredentialRequest>(BatchCredentialRequest.serializer())
