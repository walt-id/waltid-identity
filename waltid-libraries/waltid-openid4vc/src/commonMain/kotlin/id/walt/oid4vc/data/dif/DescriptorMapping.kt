package id.walt.oid4vc.data.dif

import id.walt.oid4vc.data.JsonDataObject
import id.walt.oid4vc.data.JsonDataObjectFactory
import id.walt.oid4vc.data.JsonDataObjectSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class DescriptorMapping(
    val id: String? = null,
    val format: VCFormat,
    val path: String,
    @SerialName("path_nested") val pathNested: DescriptorMapping? = null,
    override val customParameters: Map<String, JsonElement> = mapOf()
) : JsonDataObject() {

    override fun toJSON(): JsonObject {
        TODO("Not yet implemented")
    }

    companion object : JsonDataObjectFactory<DescriptorMapping>() {
        override fun fromJSON(jsonObject: JsonObject): DescriptorMapping {
            TODO("Not yet implemented")
        }

        fun vpPath(totalVps: Int, vpIdx: Int) = when (totalVps) {
            0 -> "$"
            1 -> "$"
            else -> "$[$vpIdx]"
        }
    }
}

object DescriptorMappingSerializer : JsonDataObjectSerializer<DescriptorMapping>(DescriptorMapping.serializer())

object DescriptorMappingListSerializer : KSerializer<List<DescriptorMapping>> {
    private val internalSerializer = ListSerializer(DescriptorMappingSerializer)
    override val descriptor = internalSerializer.descriptor
    override fun deserialize(decoder: Decoder) = internalSerializer.deserialize(decoder)
    override fun serialize(encoder: Encoder, value: List<DescriptorMapping>) =
        internalSerializer.serialize(encoder, value)
}
