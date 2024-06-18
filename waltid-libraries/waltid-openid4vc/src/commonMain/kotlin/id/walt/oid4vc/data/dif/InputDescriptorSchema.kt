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
data class InputDescriptorSchema(
  val uri: String,
  override val customParameters: Map<String, JsonElement> = mapOf()
): JsonDataObject() {
  override fun toJSON(): JsonObject {
    TODO("Not yet implemented")
  }
}

object InputDescriptorSchemaSerializer :
  JsonDataObjectSerializer<InputDescriptorSchema>(InputDescriptorSchema.serializer())

object InputDescriptorSchemaListSerializer : KSerializer<List<InputDescriptorSchema>> {
  private val internalSerializer = ListSerializer(InputDescriptorSchemaSerializer)
  override val descriptor = internalSerializer.descriptor
  override fun deserialize(decoder: Decoder) = internalSerializer.deserialize(decoder)
  override fun serialize(encoder: Encoder, value: List<InputDescriptorSchema>) =
    internalSerializer.serialize(encoder, value)
}
