package id.walt.oid4vc.data

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
@Serializable(with = SupportedVPFormatSerializer::class)
data class SupportedVPFormat(
    val alg_values_supported: Set<String>,
    override val customParameters: Map<String, JsonElement>? = mapOf()
) : JsonDataObject() {
    override fun toJSON() = Json.encodeToJsonElement(SupportedVPFormatSerializer, this).jsonObject

    companion object : JsonDataObjectFactory<SupportedVPFormat>() {
        override fun fromJSON(jsonObject: JsonObject): SupportedVPFormat =
            Json.decodeFromJsonElement(SupportedVPFormatSerializer, jsonObject)
    }
}

internal object SupportedVPFormatSerializer :
    JsonDataObjectSerializer<SupportedVPFormat>(SupportedVPFormat.generatedSerializer())
