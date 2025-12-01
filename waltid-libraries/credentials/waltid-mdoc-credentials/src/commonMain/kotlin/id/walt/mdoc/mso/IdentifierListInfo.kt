package id.walt.mdoc.mso

import cbor.Cbor
import id.walt.mdoc.dataelement.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

@Serializable(with = IdentifierListInfoSerializer::class)
data class IdentifierListInfo(
    val id: ByteArray,
    val uri: String,
    val certificate: ByteArray? = null,
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as IdentifierListInfo

        if (!id.contentEquals(other.id)) return false
        if (uri != other.uri) return false
        if (certificate != null) {
            if (other.certificate == null) return false
            if (!certificate.contentEquals(other.certificate)) return false
        } else if (other.certificate != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.contentHashCode()
        result = 31 * result + uri.hashCode()
        result = 31 * result + (certificate?.contentHashCode() ?: 0)
        return result
    }

    /**
     * Convert to CBOR map element
     */
    fun toMapElement() = MapElement(
        buildMap {
            put(MapKey("id"), id.toDataElement())
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
        put("id", Json.encodeToJsonElement(id))
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
        fun fromCBOR(cbor: ByteArray) = Cbor.decodeFromByteArray<IdentifierListInfo>(cbor)

        /**
         * Deserialize from CBOR hex string
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun fromCBORHex(cbor: String) = Cbor.decodeFromHexString<IdentifierListInfo>(cbor)

        fun fromMapElement(element: MapElement): IdentifierListInfo {
            require(element.value.containsKey(MapKey("id"))) {
                "IdentifierListInfo CBOR map must contain key id"
            }
            require(element.value[MapKey("id")]!!.type == DEType.byteString) {
                "Value of id key of IdentifierListInfo CBOR map expected to be of type ${DEType.byteString}, but instead was " +
                        "found to be of type ${element.value[MapKey("id")]!!.type}"
            }
            require(element.value.containsKey(MapKey("uri"))) {
                "IdentifierListInfo CBOR map must contain key uri"
            }
            require(element.value[MapKey("uri")]!!.type == DEType.textString) {
                "Value of uri key of IdentifierListInfo CBOR map expected to be of type ${DEType.textString}, but instead was " +
                        "found to be of type ${element.value[MapKey("uri")]!!.type}"
            }
            return IdentifierListInfo(
                id = (element.value[MapKey("id")]!! as ByteStringElement).value,
                uri = (element.value[MapKey("uri")]!! as StringElement).value,
                certificate = element.value[MapKey("certificate")]?.let {
                    (it as ByteStringElement).value
                },
            )
        }

        fun fromJSON(jsonObject: JsonObject) = IdentifierListInfo(
            id = jsonObject.getValue("idx").let {
                Json.decodeFromJsonElement<ByteArray>(it)
            },
            uri = jsonObject.getValue("uri").jsonPrimitive.content,
            certificate = jsonObject["certificate"]?.let {
                Json.decodeFromJsonElement<ByteArray>(it)
            }
        )
    }
}

internal object IdentifierListInfoSerializer : KSerializer<IdentifierListInfo> {

    override val descriptor = buildClassSerialDescriptor("IdentifierListInfo")

    override fun serialize(encoder: Encoder, value: IdentifierListInfo) {
        when (encoder) {
            is JsonEncoder -> {
                encoder.encodeSerializableValue(JsonObject.serializer(), value.toJSON())
            }

            else -> {
                encoder.encodeSerializableValue(DataElementSerializer, value.toMapElement())
            }
        }
    }

    override fun deserialize(decoder: Decoder): IdentifierListInfo {
        return when (decoder) {
            is JsonDecoder -> {
                IdentifierListInfo.fromJSON(decoder.decodeJsonElement().jsonObject)
            }

            else -> {
                IdentifierListInfo.fromMapElement(decoder.decodeSerializableValue(DataElementSerializer) as MapElement)
            }
        }
    }
}
