package id.walt.oid4vc.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

@Serializable
data class SupportedVPFormat(
    val alg_values_supported: Set<String>,
    override val customParameters: Map<String, JsonElement> = mapOf()
) : JsonDataObject() {
    override fun toJSON() = Json.encodeToJsonElement(SupportedVPFormatSerializer, this).jsonObject

    companion object : JsonDataObjectFactory<SupportedVPFormat>() {
        override fun fromJSON(jsonObject: JsonObject) =
            Json.decodeFromJsonElement(SupportedVPFormatSerializer, jsonObject)
    }
}

object SupportedVPFormatSerializer : JsonDataObjectSerializer<SupportedVPFormat>(SupportedVPFormat.serializer())

object SupportedVPFormatMapSerializer : KSerializer<Map<CredentialFormat, SupportedVPFormat>> {
    private val internalSerializer = MapSerializer(CredentialFormatSerializer, SupportedVPFormatSerializer)
    override val descriptor = internalSerializer.descriptor
    override fun deserialize(decoder: Decoder) = internalSerializer.deserialize(decoder)
    override fun serialize(encoder: Encoder, value: Map<CredentialFormat, SupportedVPFormat>) =
        internalSerializer.serialize(encoder, value)
}
