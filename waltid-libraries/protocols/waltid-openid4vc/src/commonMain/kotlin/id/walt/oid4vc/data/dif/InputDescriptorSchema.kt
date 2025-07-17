package id.walt.oid4vc.data.dif

import id.walt.oid4vc.data.JsonDataObject
import id.walt.oid4vc.data.JsonDataObjectSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject

@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
@Serializable(with = InputDescriptorSchemaSerializer::class)
data class InputDescriptorSchema(
    val uri: String,
    override val customParameters: Map<String, JsonElement>? = mapOf()
) : JsonDataObject() {
    override fun toJSON() = Json.encodeToJsonElement(InputDescriptorSchemaSerializer, this).jsonObject
}

internal object InputDescriptorSchemaSerializer :
    JsonDataObjectSerializer<InputDescriptorSchema>(InputDescriptorSchema.generatedSerializer())

internal object InputDescriptorSchemaListSerializer : KSerializer<List<InputDescriptorSchema>> {
    private val internalSerializer = ListSerializer(InputDescriptorSchemaSerializer)
    override val descriptor = internalSerializer.descriptor
    override fun deserialize(decoder: Decoder) = internalSerializer.deserialize(decoder)
    override fun serialize(encoder: Encoder, value: List<InputDescriptorSchema>) =
        internalSerializer.serialize(encoder, value)
}
