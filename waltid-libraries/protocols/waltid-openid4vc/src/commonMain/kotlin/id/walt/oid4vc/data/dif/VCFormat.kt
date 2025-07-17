package id.walt.oid4vc.data.dif

import id.walt.oid4vc.data.JsonDataObject
import id.walt.oid4vc.data.JsonDataObjectFactory
import id.walt.oid4vc.data.JsonDataObjectSerializer
import id.walt.w3c.utils.VCFormat
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
@Serializable(with = VCFormatDefinitionSerializer::class)
data class VCFormatDefinition(
    val alg: Set<String>? = null,
    val proof_type: Set<String>? = null,
    override val customParameters: Map<String, JsonElement>? = mapOf()
) : JsonDataObject() {

    override fun toJSON() = Json.encodeToJsonElement(VCFormatDefinitionSerializer, this).jsonObject

    companion object : JsonDataObjectFactory<VCFormatDefinition>() {
        override fun fromJSON(jsonObject: JsonObject): VCFormatDefinition =
            Json.decodeFromJsonElement(VCFormatDefinitionSerializer, jsonObject)
    }
}

internal object VCFormatDefinitionSerializer :
    JsonDataObjectSerializer<VCFormatDefinition>(VCFormatDefinition.generatedSerializer())

internal object VCFormatMapSerializer : KSerializer<Map<VCFormat, VCFormatDefinition>> {
    private val internalSerializer = MapSerializer(VCFormat.serializer(), VCFormatDefinitionSerializer)
    override val descriptor = internalSerializer.descriptor
    override fun deserialize(decoder: Decoder) = internalSerializer.deserialize(decoder)
    override fun serialize(encoder: Encoder, value: Map<VCFormat, VCFormatDefinition>) =
        internalSerializer.serialize(encoder, value)
}
