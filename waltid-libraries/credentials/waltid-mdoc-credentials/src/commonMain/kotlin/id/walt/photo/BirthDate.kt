package id.walt.photo

import cbor.Cbor
import id.walt.mdoc.dataelement.*
import kotlinx.datetime.LocalDate
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

/**
 * Birth date structure as per ISO/IEC 23220-2
 * Contains the birth date and optional approximate mask for partial date information
 */
@Serializable(with = BirthDateSerializer::class)
data class BirthDate(
    val birthDate: LocalDate, // YYYY-MM-DD format
    val approximateMask: String? = null // 8-digit 0/1 mask
) {
    init {
        // Validate approximate_mask format if provided
        approximateMask?.let { mask ->
            require(mask.matches(Regex("[01]{8}"))) { 
                "approximate_mask must be an 8-digit binary string (0s and 1s only)" 
            }
        }
    }

    /**
     * Convert to CBOR map element
     */
    fun toMapElement() = MapElement(
        buildMap {
            put(MapKey("birth_date"), birthDate.toDataElement())
            approximateMask?.let {
                put(MapKey("approximate_mask"), it.toDataElement())
            }
        }
    )

    /**
     * Serialize to CBOR data
     */
    fun toCBOR() = toMapElement().toCBOR()

    /**
     * Serialize to CBOR hex string
     */
    fun toCBORHex() = toMapElement().toCBORHex()

    /**
     * Serialize to JSON object
     */
    fun toJSON() = buildJsonObject {
        put("birth_date", Json.encodeToJsonElement(birthDate))
        approximateMask?.let {
            put("approximate_mask", Json.encodeToJsonElement(it))
        }
    }

    companion object {
        /**
         * Deserialize from CBOR data
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun fromCBOR(cbor: ByteArray) = Cbor.decodeFromByteArray<BirthDate>(cbor)

        /**
         * Deserialize from CBOR hex string
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun fromCBORHex(cbor: String) = Cbor.decodeFromHexString<BirthDate>(cbor)

        fun fromMapElement(element: MapElement): BirthDate {
            require(element.value.containsKey(MapKey("birth_date"))) {
                "BirthDate CBOR map must contain string key birth_date"
            }
            require(element.value[MapKey("birth_date")]!!.type == DEType.fullDate) {
                "birth_date key of BirthDate CBOR map expected to be of type ${DEType.fullDate}, but " +
                        "instead was found to be of type ${element.value[MapKey("birth_date")]!!.type}"
            }
            return BirthDate(
                birthDate = (element.value[MapKey("birth_date")]!! as FullDateElement).value,
                approximateMask = element.value[MapKey("approximate_mask")]?.let {
                    (it as StringElement).value
                }
            )
        }

        fun fromJSON(jsonObject: JsonObject) = BirthDate(
            birthDate = Json.decodeFromJsonElement<LocalDate>(jsonObject.getValue("birth_date")),
            approximateMask = jsonObject["approximate_mask"]?.jsonPrimitive?.content
        )
    }
}

internal object BirthDateSerializer : KSerializer<BirthDate> {
    override val descriptor = buildClassSerialDescriptor("BirthDate")

    override fun serialize(encoder: Encoder, value: BirthDate) {
        when (encoder) {
            is JsonEncoder -> {
                encoder.encodeSerializableValue(JsonObject.serializer(), value.toJSON())
            }
            else -> {
                encoder.encodeSerializableValue(DataElementSerializer, value.toMapElement())
            }
        }
    }

    override fun deserialize(decoder: Decoder): BirthDate {
        return when (decoder) {
            is JsonDecoder -> {
                BirthDate.fromJSON(decoder.decodeJsonElement().jsonObject)
            }
            else -> {
                BirthDate.fromMapElement(decoder.decodeSerializableValue(DataElementSerializer) as MapElement)
            }
        }
    }
}
