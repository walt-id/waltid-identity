package id.walt.oid4vc.data.dif

import id.walt.oid4vc.data.JsonDataObject
import id.walt.oid4vc.data.JsonDataObjectSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class InputDescriptorField(
    val path: List<String>,
    val id: String? = null,
    val purpose: String? = null,
    val name: String? = null,
    val filter: JsonObject? = null,
    val optional: Boolean? = null,
    override val customParameters: Map<String, JsonElement> = mapOf()
) : JsonDataObject() {
    override fun toJSON(): JsonObject {
        TODO("Not yet implemented")
    }
}

object InputDescriptorFieldSerializer :
    JsonDataObjectSerializer<InputDescriptorField>(InputDescriptorField.serializer())

object InputDescriptorFieldListSerializer : KSerializer<List<InputDescriptorField>> {
    private val internalSerializer = ListSerializer(InputDescriptorFieldSerializer)
    override val descriptor = internalSerializer.descriptor
    override fun deserialize(decoder: Decoder) = internalSerializer.deserialize(decoder)
    override fun serialize(encoder: Encoder, value: List<InputDescriptorField>) =
        internalSerializer.serialize(encoder, value)
}
