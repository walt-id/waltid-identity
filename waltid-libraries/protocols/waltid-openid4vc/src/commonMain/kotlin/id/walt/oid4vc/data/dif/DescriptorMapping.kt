package id.walt.oid4vc.data.dif

import id.walt.oid4vc.data.JsonDataObject
import id.walt.oid4vc.data.JsonDataObjectFactory
import id.walt.oid4vc.data.JsonDataObjectSerializer
import id.walt.w3c.utils.VCFormat
import kotlinx.serialization.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
@Serializable(with = DescriptorMappingSerializer::class)
data class DescriptorMapping(
    val id: String? = null,
    val format: VCFormat? = null,
    val path: String,
    @SerialName("path_nested") val pathNested: DescriptorMapping? = null,
    override val customParameters: Map<String, JsonElement>? = mapOf()
) : JsonDataObject() {

    override fun toJSON(): JsonObject = Json.encodeToJsonElement(DescriptorMappingSerializer, this).jsonObject

    companion object : JsonDataObjectFactory<DescriptorMapping>() {
        override fun fromJSON(jsonObject: JsonObject): DescriptorMapping =
            Json.decodeFromJsonElement(DescriptorMappingSerializer, jsonObject)

        fun vpPath(totalVps: Int, vpIdx: Int) = when (totalVps) {
            0 -> "$"
            1 -> "$"
            else -> "$[$vpIdx]"
        }
    }
}

internal object DescriptorMappingSerializer :
    JsonDataObjectSerializer<DescriptorMapping>(DescriptorMapping.generatedSerializer())

internal object DescriptorMappingListSerializer : KSerializer<List<DescriptorMapping>> {
    private val internalSerializer = ListSerializer(DescriptorMappingSerializer)
    override val descriptor = internalSerializer.descriptor
    override fun deserialize(decoder: Decoder) = internalSerializer.deserialize(decoder)
    override fun serialize(encoder: Encoder, value: List<DescriptorMapping>) =
        internalSerializer.serialize(encoder, value)
}
