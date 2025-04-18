package id.walt.mdoc.mso

import cbor.Cbor
import id.walt.mdoc.dataelement.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

@Serializable(with = StatusListInfoSerializer::class)
data class StatusListInfo(
    val index: UInt,
    val uri: String,
    val certificate: ByteArray? = null,
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as StatusListInfo

        if (index != other.index) return false
        if (uri != other.uri) return false
        if (certificate != null) {
            if (other.certificate == null) return false
            if (!certificate.contentEquals(other.certificate)) return false
        } else if (other.certificate != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = index.hashCode()
        result = 31 * result + uri.hashCode()
        result = 31 * result + (certificate?.contentHashCode() ?: 0)
        return result
    }

    /**
     * Convert to CBOR map element
     */
    fun toMapElement() = MapElement(
        buildMap {
            put(MapKey("idx"), index.toDataElement())
            put(MapKey("uri"), uri.toDataElement())
            certificate?.let {
                put(MapKey("certificate"), certificate.toDataElement())
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
     * */
    fun toJSON() = buildJsonObject {
        put("idx", Json.encodeToJsonElement(index))
        put("uri", Json.encodeToJsonElement(uri))
        certificate?.let {
            put("certificate", Json.encodeToJsonElement(certificate))
        }
    }

    companion object {

        /**
         * Deserialize from CBOR data
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun fromCBOR(cbor: ByteArray) = Cbor.decodeFromByteArray<StatusListInfo>(cbor)

        /**
         * Deserialize from CBOR hex string
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun fromCBORHex(cbor: String) = Cbor.decodeFromHexString<StatusListInfo>(cbor)

        fun fromMapElement(element: MapElement): StatusListInfo {
            require(element.value.containsKey(MapKey("idx"))) {
                "StatusListInfo CBOR map must contain key idx"
            }
            require(element.value[MapKey("idx")]!!.type == DEType.number) {
                "Value of idx key of StatusListInfo CBOR map expected to be of type ${DEType.number}, but instead was " +
                        "found to be of type ${element.value[MapKey("idx")]!!.type}"
            }
            require(element.value.containsKey(MapKey("uri"))) {
                "StatusListInfo CBOR map must contain key uri"
            }
            require(element.value[MapKey("uri")]!!.type == DEType.textString) {
                "Value of uri key of StatusListInfo CBOR map expected to be of type ${DEType.textString}, but instead was " +
                        "found to be of type ${element.value[MapKey("uri")]!!.type}"
            }
            return StatusListInfo(
                index = (element.value[MapKey("idx")]!! as NumberElement).value.toLong().toUInt(),
                uri = (element.value[MapKey("uri")]!! as StringElement).value,
                certificate = element.value[MapKey("certificate")]?.let {
                    (it as ByteStringElement).value
                },
            )
        }

        fun fromJSON(jsonObject: JsonObject) = StatusListInfo(
            index = jsonObject.getValue("idx").jsonPrimitive.long.toUInt(),
            uri = jsonObject.getValue("uri").jsonPrimitive.content,
            certificate = jsonObject["certificate"]?.let {
                Json.decodeFromJsonElement<ByteArray>(it)
            }
        )
    }

}

internal object StatusListInfoSerializer: KSerializer<StatusListInfo> {

    override val descriptor = buildClassSerialDescriptor("StatusListInfo")

    override fun serialize(encoder: Encoder, value: StatusListInfo) {
        when(encoder) {
            is JsonEncoder -> {
                encoder.encodeSerializableValue(JsonObject.serializer(), value.toJSON())
            }

            else -> {
                encoder.encodeSerializableValue(DataElementSerializer, value.toMapElement())
            }
        }
    }

    override fun deserialize(decoder: Decoder): StatusListInfo {
        return when(decoder) {
            is JsonDecoder -> {
                StatusListInfo.fromJSON(decoder.decodeJsonElement().jsonObject)
            }

            else -> {
                StatusListInfo.fromMapElement(decoder.decodeSerializableValue(DataElementSerializer) as MapElement)
            }
        }
    }
}