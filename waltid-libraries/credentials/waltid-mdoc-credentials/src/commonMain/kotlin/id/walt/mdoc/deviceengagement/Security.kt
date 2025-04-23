package id.walt.mdoc.deviceengagement

import cbor.Cbor
import id.walt.mdoc.dataelement.*
import id.walt.mdoc.dataelement.DataElementSerializer
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = SecuritySerializer::class)
data class Security(
    val cipherSuite: Int = 1, //only cipher suite 1 is described in the specification
    val eDeviceKeyBytes: EncodedCBORElement,
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Security) return false

        if (cipherSuite != other.cipherSuite) return false
        if (!eDeviceKeyBytes.value.contentEquals(other.eDeviceKeyBytes.value)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = cipherSuite
        result = 31 * result + eDeviceKeyBytes.value.contentHashCode()
        return result
    }

    /**
     * Convert to CBOR array element
     */
    fun toListElement() = listOf(
        cipherSuite.toDataElement(),
        eDeviceKeyBytes,
    ).toDataElement()

    /**
     * Serialize to CBOR data
     */
    fun toCBOR() = toListElement().toCBOR()

    /**
     * Serialize to CBOR hex string
     */
    fun toCBORHex() = toListElement().toCBORHex()

    companion object {

        /**
         * Deserialize from CBOR data
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun fromCBOR(cbor: ByteArray) = Cbor.decodeFromByteArray<Security>(cbor)

        /**
         * Deserialize from CBOR hex string
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun fromCBORHex(cbor: String) = Cbor.decodeFromHexString<Security>(cbor)

        /**
         * Convert from CBOR array element
         */
        fun fromListElement(element: ListElement): Security {
            require(element.value.size == 2) {
                "Security is CBOR encoded as an array of 2 elements, instead found ${element.value.size} elements"
            }
            require(element.value[0].type == DEType.number) {
                "Cipher suite identifier field of Security must be a number (specifically Int), instead was found to be of type ${element.value[0].type}"
            }
            require(element.value[1].type == DEType.encodedCbor) {
                "EDeviceKeyBytes field of Security must be an encoded cbor data item, instead was found to be of type ${element.value[1].type}"
            }
            return Security(
                cipherSuite = (element.value[0] as NumberElement).value.toInt(),
                eDeviceKeyBytes = element.value[1] as EncodedCBORElement,
            )
        }
    }

}

internal object SecuritySerializer : KSerializer<Security> {

    override val descriptor = buildClassSerialDescriptor("Security")

    override fun serialize(encoder: Encoder, value: Security) {
        encoder.encodeSerializableValue(DataElementSerializer, value.toListElement())
    }

    override fun deserialize(decoder: Decoder): Security {
        return Security.fromListElement(decoder.decodeSerializableValue(DataElementSerializer) as ListElement)
    }
}
