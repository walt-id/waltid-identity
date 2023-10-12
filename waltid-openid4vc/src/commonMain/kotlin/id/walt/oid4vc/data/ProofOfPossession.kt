package id.walt.oid4vc.data

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

@Serializable
data class ProofOfPossession(
    @EncodeDefault @SerialName("proof_type") val proofType: ProofType = ProofType.jwt,
    val jwt: String?,
    override val customParameters: Map<String, JsonElement> = mapOf()
) : JsonDataObject() {
    override fun toJSON() = Json.encodeToJsonElement(ProofOfPossessionSerializer, this).jsonObject

    companion object : JsonDataObjectFactory<ProofOfPossession>() {
        override fun fromJSON(jsonObject: JsonObject) =
            Json.decodeFromJsonElement(ProofOfPossessionSerializer, jsonObject)
    }
}

object ProofOfPossessionSerializer : JsonDataObjectSerializer<ProofOfPossession>(ProofOfPossession.serializer())

enum class ProofType {
    jwt
}
