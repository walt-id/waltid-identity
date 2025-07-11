package id.walt.oid4vc.data.dif

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

@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
@Serializable(with = InputDescriptorConstraintsSerializer::class)
data class InputDescriptorConstraints(
    @Serializable(InputDescriptorFieldListSerializer::class) val fields: List<InputDescriptorField>? = null,
    @SerialName("limit_disclosure") val limitDisclosure: DisclosureLimitation? = null,
    override val customParameters: Map<String, JsonElement>? = mapOf()
) : JsonDataObject() {
    override fun toJSON() = Json.encodeToJsonElement(InputDescriptorConstraintsSerializer, this).jsonObject

    companion object : JsonDataObjectFactory<InputDescriptorConstraints>() {
        override fun fromJSON(jsonObject: JsonObject): InputDescriptorConstraints =
            Json.decodeFromJsonElement(InputDescriptorConstraintsSerializer, jsonObject)
    }
}

internal object InputDescriptorConstraintsSerializer : JsonDataObjectSerializer<InputDescriptorConstraints>(
    InputDescriptorConstraints.generatedSerializer()
)
