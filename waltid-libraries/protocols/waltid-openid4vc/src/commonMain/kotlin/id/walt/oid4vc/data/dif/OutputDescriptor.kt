package id.walt.oid4vc.data.dif

import id.walt.oid4vc.data.JsonDataObject
import id.walt.oid4vc.data.JsonDataObjectFactory
import id.walt.oid4vc.data.JsonDataObjectSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
@Serializable(with = OutputDescriptorSerializer::class)
data class OutputDescriptor(
    val id: String,
    val schema: String,
    val name: String? = null,
    override val customParameters: Map<String, JsonElement>? = mapOf()
) : JsonDataObject() {
    override fun toJSON() = Json.encodeToJsonElement(OutputDescriptorSerializer, this).jsonObject

    companion object : JsonDataObjectFactory<OutputDescriptor>() {
        override fun fromJSON(jsonObject: JsonObject): OutputDescriptor =
            Json.decodeFromJsonElement(OutputDescriptorSerializer, jsonObject)
    }
}

internal object OutputDescriptorSerializer :
    JsonDataObjectSerializer<OutputDescriptor>(OutputDescriptor.generatedSerializer())
