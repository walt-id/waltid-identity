package id.walt.oid4vc.data

import kotlinx.serialization.*
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

// TODO: reconsider nested claims handling, which seems to be mis-specified (mixing claim properties and nested properties)
@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
@Serializable(with = ClaimDescriptorSerializer::class)
data class ClaimDescriptor(
    val mandatory: Boolean? = null,
    @SerialName("value_type") val valueType: String? = null,
    @Serializable(DisplayPropertiesListSerializer::class) val display: List<DisplayProperties>? = null,
    override val customParameters: Map<String, JsonElement>? = mapOf()
) : JsonDataObject() {
    val nestedClaims: Map<String, ClaimDescriptor> = customParameters!!.filterValues { it is JsonObject }
        .mapValues { fromJSON(it.value.jsonObject) }

    override fun toJSON() = Json.encodeToJsonElement(ClaimDescriptorSerializer, this).jsonObject

    companion object : JsonDataObjectFactory<ClaimDescriptor>() {
        override fun fromJSON(jsonObject: JsonObject): ClaimDescriptor =
            Json.decodeFromJsonElement(ClaimDescriptorSerializer, jsonObject)
    }
}

internal object ClaimDescriptorSerializer :
    JsonDataObjectSerializer<ClaimDescriptor>(ClaimDescriptor.generatedSerializer())

internal object ClaimDescriptorMapSerializer : KSerializer<Map<String, ClaimDescriptor>> {
    private val internalSerializer = MapSerializer(String.serializer(), ClaimDescriptorSerializer)
    override val descriptor: SerialDescriptor = internalSerializer.descriptor
    override fun deserialize(decoder: Decoder): Map<String, ClaimDescriptor> = internalSerializer.deserialize(decoder)
    override fun serialize(encoder: Encoder, value: Map<String, ClaimDescriptor>) =
        internalSerializer.serialize(encoder, value)
}

internal object ClaimDescriptorNamespacedMapSerializer : KSerializer<Map<String, Map<String, ClaimDescriptor>>> {
    private val internalSerializer =
        MapSerializer(String.serializer(), MapSerializer(String.serializer(), ClaimDescriptorSerializer))
    override val descriptor: SerialDescriptor = internalSerializer.descriptor
    override fun deserialize(decoder: Decoder): Map<String, Map<String, ClaimDescriptor>> =
        internalSerializer.deserialize(decoder)

    override fun serialize(encoder: Encoder, value: Map<String, Map<String, ClaimDescriptor>>) =
        internalSerializer.serialize(encoder, value)
}
