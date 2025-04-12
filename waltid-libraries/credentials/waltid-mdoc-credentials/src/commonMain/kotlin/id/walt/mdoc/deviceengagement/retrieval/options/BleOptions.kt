package id.walt.mdoc.deviceengagement.retrieval.options

import cbor.Cbor
import id.walt.mdoc.dataelement.*
import id.walt.mdoc.dataelement.DataElementSerializer
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = BleOptionsSerializer::class)
data class BleOptions(
    val supportMDocPeripheralServerMode: Boolean,
    val supportMDocCentralClientMode: Boolean,
    val mdocPeripheralServerModeUUID: ByteArray? = null,
    val mdocCentralClientModeUUID: ByteArray? = null,
    val mdocPeripheralServerModeDeviceAddress: ByteArray? = null,
    val mdocPeripheralServerModeL2CAPPSM: UInt? = null,
) : RetrievalOptions() {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BleOptions) return false

        if (supportMDocPeripheralServerMode != other.supportMDocPeripheralServerMode) return false
        if (supportMDocCentralClientMode != other.supportMDocCentralClientMode) return false

        if (mdocPeripheralServerModeUUID != null) {
            if (other.mdocPeripheralServerModeUUID == null ||
                !mdocPeripheralServerModeUUID.contentEquals(other.mdocPeripheralServerModeUUID)
            ) return false
        } else if (other.mdocPeripheralServerModeUUID != null) return false

        if (mdocCentralClientModeUUID != null) {
            if (other.mdocCentralClientModeUUID == null ||
                !mdocCentralClientModeUUID.contentEquals(other.mdocCentralClientModeUUID)
            ) return false
        } else if (other.mdocCentralClientModeUUID != null) return false

        if (mdocPeripheralServerModeDeviceAddress != null) {
            if (other.mdocPeripheralServerModeDeviceAddress == null ||
                !mdocPeripheralServerModeDeviceAddress.contentEquals(other.mdocPeripheralServerModeDeviceAddress)
            ) return false
        } else if (other.mdocPeripheralServerModeDeviceAddress != null) return false

        if (mdocPeripheralServerModeL2CAPPSM != other.mdocPeripheralServerModeL2CAPPSM) return false

        return true
    }

    override fun hashCode(): Int {
        var result = supportMDocPeripheralServerMode.hashCode()
        result = 31 * result + supportMDocCentralClientMode.hashCode()
        result = 31 * result + (mdocPeripheralServerModeUUID?.contentHashCode() ?: 0)
        result = 31 * result + (mdocCentralClientModeUUID?.contentHashCode() ?: 0)
        result = 31 * result + (mdocPeripheralServerModeDeviceAddress?.contentHashCode() ?: 0)
        result = 31 * result + (mdocPeripheralServerModeL2CAPPSM?.hashCode() ?: 0)
        return result
    }

    override fun toDataElement() = toMapElement()

    /**
     * Convert to CBOR map element
     */
    fun toMapElement() = MapElement(
        buildMap {
            put(MapKey(0), supportMDocPeripheralServerMode.toDataElement())
            put(MapKey(1), supportMDocCentralClientMode.toDataElement())
            mdocPeripheralServerModeUUID?.let {
                put(MapKey(10), it.toDataElement())
            }
            mdocCentralClientModeUUID?.let {
                put(MapKey(11), it.toDataElement())
            }
            mdocPeripheralServerModeDeviceAddress?.let {
                put(MapKey(20), it.toDataElement())
            }
            mdocPeripheralServerModeL2CAPPSM?.let {
                put(MapKey(21), it.toDataElement())
            }
        }
    )

    companion object {

        /**
         * Deserialize from CBOR data
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun fromCBOR(cbor: ByteArray) = Cbor.decodeFromByteArray<BleOptions>(cbor)

        /**
         * Deserialize from CBOR hex string
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun fromCBORHex(cbor: String) = Cbor.decodeFromHexString<BleOptions>(cbor)

        /**
         * Convert from CBOR map element
         */
        fun fromMapElement(element: MapElement): BleOptions {
            require(element.value.containsKey(MapKey(0))) {
                "BleOptions CBOR map must have key 0 indicating support for mdoc peripheral server mode."
            }
            require(element.value.containsKey(MapKey(1))) {
                "BleOptions CBOR map must have key 1 indicating support for mdoc central client mode."
            }
            return BleOptions(
                supportMDocPeripheralServerMode = element.value[MapKey(0)]!!.let {
                    require(it.type == DEType.boolean) {
                        "Expected CBOR boolean type indicating support for mdoc peripheral server mode, instead found ${it.type} CBOR type"
                    }
                    (element.value[MapKey(0)] as BooleanElement).value
                },
                supportMDocCentralClientMode = element.value[MapKey(1)]!!.let {
                    require(it.type == DEType.boolean) {
                        "Expected CBOR boolean type indicating support for mdoc central client mode, instead found ${it.type} CBOR type"
                    }
                    (element.value[MapKey(1)] as BooleanElement).value
                },
                mdocPeripheralServerModeUUID = element.value[MapKey(10)]?.let {
                    require(it.type == DEType.byteString) {
                        "Expected CBOR byte string to encode a UUID for mdoc peripheral server mode, instead found ${it.type} CBOR type " +
                                "in BleOptions CBOR map key 10"
                    }
                    (element.value[MapKey(10)] as ByteStringElement).value
                },
                mdocCentralClientModeUUID = element.value[MapKey(11)]?.let {
                    require(it.type == DEType.byteString) {
                        "Expected CBOR byte string to encode a UUID for mdoc client central mode, instead found ${it.type} CBOR type " +
                                "in BleOptions CBOR map key 11"
                    }
                    (element.value[MapKey(11)] as ByteStringElement).value
                },
                mdocPeripheralServerModeDeviceAddress = element.value[MapKey(20)]?.let {
                    require(it.type == DEType.byteString) {
                        "Expected CBOR byte string to encode mdoc BLE device address for peripheral server mode, instead found ${it.type} CBOR type " +
                                "in BleOptions CBOR map key 20"
                    }
                    (element.value[MapKey(20)] as ByteStringElement).value
                },
                mdocPeripheralServerModeL2CAPPSM = element.value[MapKey(21)]?.let {
                    require(it.type == DEType.number) {
                        "Expected CBOR number (UInt) to encode mdoc BLE L2CAP PSM for peripheral server mode, instead found ${it.type} CBOR type " +
                                "in BleOptions CBOR map key 21"
                    }
                    (element.value[MapKey(21)] as NumberElement).value.toLong().toUInt()
                },
            )
        }
    }
}

internal object BleOptionsSerializer : KSerializer<BleOptions> {

    override val descriptor = buildClassSerialDescriptor("BleOptions")

    override fun serialize(encoder: Encoder, value: BleOptions) {
        encoder.encodeSerializableValue(DataElementSerializer, value.toMapElement())
    }

    override fun deserialize(decoder: Decoder): BleOptions {
        return BleOptions.fromMapElement(decoder.decodeSerializableValue(DataElementSerializer) as MapElement)
    }
}