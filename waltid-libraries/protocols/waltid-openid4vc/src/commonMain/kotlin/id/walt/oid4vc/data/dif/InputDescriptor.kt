package id.walt.oid4vc.data.dif

import id.walt.oid4vc.data.JsonDataObject
import id.walt.oid4vc.data.JsonDataObjectFactory
import id.walt.oid4vc.data.JsonDataObjectSerializer
import id.walt.w3c.utils.VCFormat
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
@Serializable(with = InputDescriptorSerializer::class)
data class InputDescriptor(
    val id: String = "1",
    val name: String? = null,
    val purpose: String? = null,
    @Serializable(VCFormatMapSerializer::class) val format: Map<VCFormat, VCFormatDefinition>? = null,
    @Serializable(InputDescriptorConstraintsSerializer::class) val constraints: InputDescriptorConstraints? = null,
    @Serializable(InputDescriptorSchemaListSerializer::class) val schema: List<InputDescriptorSchema>? = null,
    val group: Set<String>? = null,
    override val customParameters: Map<String, JsonElement>? = mapOf()
) : JsonDataObject() {
    override fun toJSON() = Json.encodeToJsonElement(InputDescriptorSerializer, this).jsonObject

    companion object : JsonDataObjectFactory<InputDescriptor>() {
        override fun fromJSON(jsonObject: JsonObject) =
            Json.decodeFromJsonElement(InputDescriptorSerializer, jsonObject)
    }
}

internal object InputDescriptorSerializer :
    JsonDataObjectSerializer<InputDescriptor>(InputDescriptor.generatedSerializer())

internal object InputDescriptorListSerializer : KSerializer<List<InputDescriptor>> {
    private val internalSerializer = ListSerializer(InputDescriptorSerializer)
    override val descriptor = internalSerializer.descriptor
    override fun deserialize(decoder: Decoder) = internalSerializer.deserialize(decoder)
    override fun serialize(encoder: Encoder, value: List<InputDescriptor>) =
        internalSerializer.serialize(encoder, value)
}
