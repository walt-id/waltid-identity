package id.walt.mdoc.deviceengagement.retrieval.options

import cbor.Cbor
import id.walt.mdoc.dataelement.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = WifiOptionsSerializer::class)
data class WifiOptions(
    val passPhrase: String? = null,
    val channelInfoOperatingClass: UInt? = null,
    val channelInfoChannelNumber: UInt? = null,
    val bandInfoSupportedBands: ByteArray,
) : RetrievalOptions() {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WifiOptions) return false

        return passPhrase == other.passPhrase &&
                channelInfoOperatingClass == other.channelInfoOperatingClass &&
                channelInfoChannelNumber == other.channelInfoChannelNumber &&
                bandInfoSupportedBands.contentEquals(other.bandInfoSupportedBands)
    }

    override fun hashCode(): Int {
        var result = passPhrase?.hashCode() ?: 0
        result = 31 * result + (channelInfoOperatingClass?.hashCode() ?: 0)
        result = 31 * result + (channelInfoChannelNumber?.hashCode() ?: 0)
        result = 31 * result + bandInfoSupportedBands.contentHashCode()
        return result
    }

    override fun toDataElement() = toMapElement()

    /**
     * Convert to CBOR map element
     */
    fun toMapElement() = MapElement(
        buildMap {
            passPhrase?.let {
                put(MapKey(0), passPhrase.toDataElement())
            }
            channelInfoOperatingClass?.let {
                put(MapKey(1), channelInfoOperatingClass.toDataElement())
            }
            channelInfoChannelNumber?.let {
                put(MapKey(2), channelInfoChannelNumber.toDataElement())
            }
            put(MapKey(3), bandInfoSupportedBands.toDataElement())
        }
    )

    companion object {

        /**
         * Deserialize from CBOR data
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun fromCBOR(cbor: ByteArray) = Cbor.decodeFromByteArray<WifiOptions>(cbor)

        /**
         * Deserialize from CBOR hex string
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun fromCBORHex(cbor: String) = Cbor.decodeFromHexString<WifiOptions>(cbor)

        /**
         * Convert from CBOR map element
         */
        fun fromMapElement(element: MapElement) = WifiOptions(
            passPhrase = element.value[MapKey(0)]?.let {
                require(it.type == DEType.textString) { "Passphrase field of WifiOptions needs to be a text string" }
                (it as StringElement).value
            },
            channelInfoOperatingClass = element.value[MapKey(1)]?.let {
                (it as NumberElement).value.toLong().toUInt()
            },
            channelInfoChannelNumber = element.value[MapKey(2)]?.let {
                (it as NumberElement).value.toLong().toUInt()
            },
            bandInfoSupportedBands = element.value[MapKey(3)]!!.let {
                require(it.type == DEType.byteString) {
                    "Band info supported bands of WifiOptions needs to be a byte string"
                }
                (element.value[MapKey(3)] as ByteStringElement).value
            },
        )
    }
}

internal object WifiOptionsSerializer : KSerializer<WifiOptions> {

    override val descriptor = buildClassSerialDescriptor("WifiOptions")

    override fun serialize(encoder: Encoder, value: WifiOptions) {
        encoder.encodeSerializableValue(DataElementSerializer, value.toMapElement())
    }

    override fun deserialize(decoder: Decoder): WifiOptions {
        return WifiOptions.fromMapElement(decoder.decodeSerializableValue(DataElementSerializer) as MapElement)
    }
}
