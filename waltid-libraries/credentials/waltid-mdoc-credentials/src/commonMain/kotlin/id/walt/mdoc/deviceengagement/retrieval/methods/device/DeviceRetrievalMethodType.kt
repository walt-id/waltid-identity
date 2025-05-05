package id.walt.mdoc.deviceengagement.retrieval.methods.device

import cbor.Cbor
import id.walt.mdoc.dataelement.DataElementSerializer
import id.walt.mdoc.dataelement.NumberElement
import id.walt.mdoc.dataelement.toDataElement
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = DeviceRetrievalMethodTypeSerializer::class)
enum class DeviceRetrievalMethodType(val value: UInt) {

    WIFI(3U),
    BLE(2U),
    NFC(1U);

    /**
     * Convert to CBOR number element
     */
    fun toNumberElement() = value.toDataElement()

    /**
     * Serialize to CBOR data
     */
    fun toCBOR() = toNumberElement().toCBOR()

    /**
     * Serialize to CBOR hex string
     */
    fun toCBORHex() = toNumberElement().toCBORHex()

    companion object {

        /**
         * Deserialize from CBOR data
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun fromCBOR(cbor: ByteArray) = Cbor.decodeFromByteArray<DeviceRetrievalMethodType>(cbor)

        /**
         * Deserialize from CBOR hex string
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun fromCBORHex(cbor: String) = Cbor.decodeFromHexString<DeviceRetrievalMethodType>(cbor)

        fun fromNumberElement(element: NumberElement) = DeviceRetrievalMethodType
            .entries
            .find {
                it.value == element.value.toLong().toUInt()
            } ?: throw SerializationException("Unknown DeviceRetrievalMethodType ${element.value}")
    }
}

internal object DeviceRetrievalMethodTypeSerializer: KSerializer<DeviceRetrievalMethodType> {

    override val descriptor = buildClassSerialDescriptor("DeviceRetrievalMethodType")

    override fun serialize(encoder: Encoder, value: DeviceRetrievalMethodType) {
        encoder.encodeSerializableValue(DataElementSerializer, value.toNumberElement())
    }

    override fun deserialize(decoder: Decoder): DeviceRetrievalMethodType {
        return DeviceRetrievalMethodType.fromNumberElement(decoder.decodeSerializableValue(DataElementSerializer) as NumberElement)
    }
}