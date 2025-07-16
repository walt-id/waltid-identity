package id.walt.oid4vc.data

import kotlinx.serialization.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Display properties of a Credential Issuer for a certain language
 * @param name REQUIRED String value of a display name for the Credential.
 * @param locale OPTIONAL. String value that identifies the language of this object represented as a language tag taken from values defined in BCP47 [RFC5646]. Multiple display objects MAY be included for separate languages. There MUST be only one object with the same language identifier.
 * @param logo OPTIONAL. A JSON object with information about the logo of the Credential
 * @param description OPTIONAL. String value of a description of the Credential.
 * @param backgroundColor OPTIONAL. String value of a background color of the Credential represented as numerical color values defined in CSS Color Module Level 37 [CSS-Color].
 * @param textColor String value of a text color of the Credential represented as numerical color values defined in CSS Color Module Level 37 [CSS-Color].
 * @param backgroundImage OPTIONAL. Object with information about the background image of the Credential.
 */
@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
@Serializable(with = DisplayPropertiesSerializer::class)
data class DisplayProperties(
    val name: String,
    val locale: String? = null,
    @Serializable(LogoPropertiesSerializer::class) val logo: LogoProperties? = null,
    val description: String? = null,
    @SerialName("secondary_image") @Serializable(LogoPropertiesSerializer::class) val secondaryImage: LogoProperties? = null,
    @SerialName("background_color") val backgroundColor: String? = null,
    @SerialName("background_image") @Serializable(LogoPropertiesSerializer::class) val backgroundImage: LogoProperties? = null,
    @SerialName("text_color") val textColor: String? = null,
    override val customParameters: Map<String, JsonElement>? = mapOf(),
) : JsonDataObject() {
    override fun toJSON(): JsonObject = Json.encodeToJsonElement(DisplayPropertiesSerializer, this).jsonObject

    companion object : JsonDataObjectFactory<DisplayProperties>() {
        override fun fromJSON(jsonObject: JsonObject): DisplayProperties =
            Json.decodeFromJsonElement(DisplayPropertiesSerializer, jsonObject)
    }
}

object DisplayPropertiesSerializer :
    JsonDataObjectSerializer<DisplayProperties>(DisplayProperties.generatedSerializer())

object DisplayPropertiesListSerializer : KSerializer<List<DisplayProperties>> {
    private val internalSerializer = ListSerializer(DisplayPropertiesSerializer)
    override val descriptor: SerialDescriptor = internalSerializer.descriptor
    override fun deserialize(decoder: Decoder) = internalSerializer.deserialize(decoder)
    override fun serialize(encoder: Encoder, value: List<DisplayProperties>) =
        internalSerializer.serialize(encoder, value)
}
