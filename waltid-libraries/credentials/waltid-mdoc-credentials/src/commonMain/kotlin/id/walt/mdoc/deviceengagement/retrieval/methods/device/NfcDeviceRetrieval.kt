package id.walt.mdoc.deviceengagement.retrieval.methods.device

import cbor.Cbor
import id.walt.mdoc.dataelement.DataElementSerializer
import id.walt.mdoc.dataelement.ListElement
import id.walt.mdoc.deviceengagement.retrieval.options.NfcOptions
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = NfcDeviceRetrievalSerializer::class)
data class NfcDeviceRetrieval(
    override val type: DeviceRetrievalMethodType = DeviceRetrievalMethodType.NFC,
    override val version: UInt = 1U,
    override val retrievalOptions: NfcOptions
) : DeviceRetrievalMethod() {

    companion object {

        /**
         * Deserialize from CBOR data
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun fromCBOR(cbor: ByteArray) = Cbor.decodeFromByteArray<DeviceRetrievalMethod>(cbor) as NfcDeviceRetrieval

        /**
         * Deserialize from CBOR hex string
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun fromCBORHex(cbor: String) = Cbor.decodeFromHexString<DeviceRetrievalMethod>(cbor) as NfcDeviceRetrieval

        /**
         * Convert from CBOR array element
         */
        fun fromListElement(element: ListElement) = DeviceRetrievalMethod.fromListElement(element) as NfcDeviceRetrieval
    }
}

internal object NfcDeviceRetrievalSerializer : KSerializer<NfcDeviceRetrieval> {

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("NfcDeviceRetrieval")

    override fun serialize(encoder: Encoder, value: NfcDeviceRetrieval) {
        encoder.encodeSerializableValue(DataElementSerializer, value.toListElement())
    }

    override fun deserialize(decoder: Decoder): NfcDeviceRetrieval {
        return NfcDeviceRetrieval.fromListElement(decoder.decodeSerializableValue(DataElementSerializer) as ListElement)
    }
}
