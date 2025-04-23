package id.walt.mdoc.deviceengagement.retrieval.methods.device

import cbor.Cbor
import id.walt.mdoc.dataelement.*
import id.walt.mdoc.deviceengagement.retrieval.options.BleOptions
import id.walt.mdoc.deviceengagement.retrieval.options.NfcOptions
import id.walt.mdoc.deviceengagement.retrieval.options.RetrievalOptions
import id.walt.mdoc.deviceengagement.retrieval.options.WifiOptions
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = DeviceRetrievalMethodSerializer::class)
sealed class DeviceRetrievalMethod {
    abstract val type: DeviceRetrievalMethodType
    abstract val version: UInt
    abstract val retrievalOptions: RetrievalOptions

    /**
     * Convert to CBOR array element
     */
    fun toListElement() = listOf(
        type.toNumberElement(),
        version.toDataElement(),
        retrievalOptions.toDataElement(),
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
        fun fromCBOR(cbor: ByteArray) = Cbor.decodeFromByteArray<DeviceRetrievalMethod>(cbor)

        /**
         * Deserialize from CBOR hex string
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun fromCBORHex(cbor: String) = Cbor.decodeFromHexString<DeviceRetrievalMethod>(cbor)

        /**
         * Convert from CBOR array element
         */
        fun fromListElement(element: ListElement): DeviceRetrievalMethod {
            require(element.value.size == 3) {
                "DeviceRetrievalMethod is encoded as a CBOR array of 3 items, instead got an array of ${element.value.size} items"
            }

            require(element.value[0].type == DEType.number) {
                "DeviceRetrievalMethod CBOR array element 0 must be of type ${DEType.number}, instead was found to be ${element.value[0].type}"
            }

            require(element.value[1].type == DEType.number) {
                "DeviceRetrievalMethod CBOR array element 1 must be of type ${DEType.number}, instead was found to be ${element.value[1].type}"
            }

            val type = DeviceRetrievalMethodType.fromNumberElement(element.value[0] as NumberElement)

            val version = element.value[1].let { typeDE ->
                (typeDE as NumberElement).value.toLong().toUInt()
            }

            return when (type) {
                DeviceRetrievalMethodType.NFC -> {
                    NfcDeviceRetrieval(
                        type = type,
                        version = version,
                        retrievalOptions = NfcOptions.fromMapElement(element.value[2] as MapElement),
                    )
                }

                DeviceRetrievalMethodType.BLE -> {
                    BleDeviceRetrieval(
                        type = type,
                        version = version,
                        retrievalOptions = BleOptions.fromMapElement(element.value[2] as MapElement),
                    )
                }

                DeviceRetrievalMethodType.WIFI -> {
                    WifiDeviceRetrieval(
                        type = type,
                        version = version,
                        retrievalOptions = WifiOptions.fromMapElement(element.value[2] as MapElement),
                    )
                }
            }
        }
    }
}

internal object DeviceRetrievalMethodSerializer : KSerializer<DeviceRetrievalMethod> {

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("DeviceRetrievalMethod")

    override fun serialize(encoder: Encoder, value: DeviceRetrievalMethod) {
        encoder.encodeSerializableValue(DataElementSerializer, value.toListElement())
    }

    override fun deserialize(decoder: Decoder): DeviceRetrievalMethod {
        return DeviceRetrievalMethod.fromListElement(decoder.decodeSerializableValue(DataElementSerializer) as ListElement)
    }
}
