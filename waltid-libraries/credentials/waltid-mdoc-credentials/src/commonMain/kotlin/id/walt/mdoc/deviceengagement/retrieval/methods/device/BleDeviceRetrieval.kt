package id.walt.mdoc.deviceengagement.retrieval.methods.device

import cbor.Cbor
import id.walt.mdoc.dataelement.DataElementSerializer
import id.walt.mdoc.dataelement.ListElement
import id.walt.mdoc.deviceengagement.retrieval.options.BleOptions
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = BleDeviceRetrievalSerializer::class)
data class BleDeviceRetrieval(
    override val type: DeviceRetrievalMethodType = DeviceRetrievalMethodType.BLE,
    override val version: UInt = 1U,
    override val retrievalOptions: BleOptions,
) : DeviceRetrievalMethod() {

    companion object {

        /**
         * Deserialize from CBOR data
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun fromCBOR(cbor: ByteArray) = Cbor.decodeFromByteArray<DeviceRetrievalMethod>(cbor) as BleDeviceRetrieval

        /**
         * Deserialize from CBOR hex string
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun fromCBORHex(cbor: String) = Cbor.decodeFromHexString<DeviceRetrievalMethod>(cbor) as BleDeviceRetrieval

        /**
         * Convert from CBOR array element
         */
        fun fromListElement(element: ListElement) = DeviceRetrievalMethod.fromListElement(element) as BleDeviceRetrieval
    }
}

internal object BleDeviceRetrievalSerializer : KSerializer<BleDeviceRetrieval> {

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("BleDeviceRetrieval")

    override fun serialize(encoder: Encoder, value: BleDeviceRetrieval) {
        encoder.encodeSerializableValue(DataElementSerializer, value.toListElement())
    }

    override fun deserialize(decoder: Decoder): BleDeviceRetrieval {
        return BleDeviceRetrieval.fromListElement(decoder.decodeSerializableValue(DataElementSerializer) as ListElement)
    }
}
