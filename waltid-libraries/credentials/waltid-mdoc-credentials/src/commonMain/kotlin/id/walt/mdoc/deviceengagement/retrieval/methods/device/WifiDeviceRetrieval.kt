package id.walt.mdoc.deviceengagement.retrieval.methods.device

import cbor.Cbor
import id.walt.mdoc.dataelement.DataElementSerializer
import id.walt.mdoc.dataelement.ListElement
import id.walt.mdoc.deviceengagement.retrieval.options.WifiOptions
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = WifiDeviceRetrievalSerializer::class)
data class WifiDeviceRetrieval(
    override val type: DeviceRetrievalMethodType = DeviceRetrievalMethodType.WIFI,
    override val version: UInt = 1U,
    override val retrievalOptions: WifiOptions,
) : DeviceRetrievalMethod() {

    companion object {

        /**
         * Deserialize from CBOR data
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun fromCBOR(cbor: ByteArray) = Cbor.decodeFromByteArray<DeviceRetrievalMethod>(cbor) as WifiDeviceRetrieval

        /**
         * Deserialize from CBOR hex string
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun fromCBORHex(cbor: String) = Cbor.decodeFromHexString<DeviceRetrievalMethod>(cbor) as WifiDeviceRetrieval

        /**
         * Convert from CBOR array element
         */
        fun fromListElement(element: ListElement) = DeviceRetrievalMethod.fromListElement(element) as WifiDeviceRetrieval
    }
}

internal object WifiDeviceRetrievalSerializer : KSerializer<WifiDeviceRetrieval> {

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("WifiDeviceRetrieval")

    override fun serialize(encoder: Encoder, value: WifiDeviceRetrieval) {
        encoder.encodeSerializableValue(DataElementSerializer, value.toListElement())
    }

    override fun deserialize(decoder: Decoder): WifiDeviceRetrieval {
        return WifiDeviceRetrieval.fromListElement(decoder.decodeSerializableValue(DataElementSerializer) as ListElement)
    }
}
