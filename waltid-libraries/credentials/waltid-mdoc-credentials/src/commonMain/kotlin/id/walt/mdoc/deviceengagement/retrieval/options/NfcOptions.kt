package id.walt.mdoc.deviceengagement.retrieval.options

import cbor.Cbor
import id.walt.mdoc.dataelement.*
import id.walt.mdoc.dataelement.DataElementSerializer
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = NfcOptionsSerializer::class)
data class NfcOptions(
    val commandDataFieldMaxLength: UInt,
    val responseDataFieldMaxLength: UInt,
) : RetrievalOptions() {

    override fun toDataElement() = toMapElement()

    /**
     * Convert to CBOR map element
     */
    fun toMapElement() = mapOf(
        MapKey(0) to commandDataFieldMaxLength.toDataElement(),
        MapKey(1) to responseDataFieldMaxLength.toDataElement(),
    ).toDataElement()

    companion object {

        /**
         * Deserialize from CBOR data
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun fromCBOR(cbor: ByteArray) = Cbor.decodeFromByteArray<NfcOptions>(cbor)

        /**
         * Deserialize from CBOR hex string
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun fromCBORHex(cbor: String) = Cbor.decodeFromHexString<NfcOptions>(cbor)

        /**
         * Convert from CBOR map element
         */
        fun fromMapElement(element: MapElement): NfcOptions {
            require(element.value.containsKey(MapKey(0))) {
                "NfcOptions CBOR map must have key 0 designating maximum length of command data field"
            }
            require(element.value.containsKey(MapKey(1))) {
                "NfcOptions CBOR map must have key 1 designating maximum length of response data field"
            }
            return NfcOptions(
                commandDataFieldMaxLength = (element.value[MapKey(0)]!! as NumberElement).value.toLong().toUInt(),
                responseDataFieldMaxLength = (element.value[MapKey(1)]!! as NumberElement).value.toLong().toUInt(),
            )
        }
    }
}

internal object NfcOptionsSerializer : KSerializer<NfcOptions> {

    override val descriptor = buildClassSerialDescriptor("NfcOptions")

    override fun serialize(encoder: Encoder, value: NfcOptions) {
        encoder.encodeSerializableValue(DataElementSerializer, value.toMapElement())
    }

    override fun deserialize(decoder: Decoder): NfcOptions {
        return NfcOptions.fromMapElement(decoder.decodeSerializableValue(DataElementSerializer) as MapElement)
    }
}