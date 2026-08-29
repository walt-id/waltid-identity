@file:OptIn(
    kotlinx.serialization.ExperimentalSerializationApi::class,
    kotlin.ExperimentalUnsignedTypes::class,
)

package id.walt.mdoc.proximity.mobile

import id.walt.cose.coseCompliantCbor
import id.walt.mdoc.objects.engagement.BleCentralMode
import id.walt.mdoc.objects.engagement.BlePeripheralEndpoint
import id.walt.mdoc.objects.engagement.BlePeripheralMode
import id.walt.mdoc.objects.engagement.BlePeripheralServerOptions
import id.walt.mdoc.objects.engagement.DeviceRetrievalMethod
import id.walt.mdoc.proximity.ImmutableBytes
import id.walt.mdoc.proximity.ReaderSelectedTransportOffer
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.cbor.CborInteger
import kotlinx.serialization.cbor.CborMap
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray

/** Actor whose BLE role is described by an mdoc Connection Handover carrier record. */
internal enum class NfcMdocActor { HOLDER, READER }

/** ISO mdoc NFC and Bluetooth LE carrier-record conversion without platform types. */
internal object NfcMdocCarrierCodec {
    private val BLE_MIME_TYPE = ImmutableBytes.of("application/vnd.bluetooth.le.oob".encodeToByteArray())
    private val NFC_EXTERNAL_TYPE = ImmutableBytes.of("iso.org:18013:nfc".encodeToByteArray())

    /** Converts a supported method into one carrier plus its exact auxiliary references. */
    public fun encode(
        method: DeviceRetrievalMethod,
        carrierReference: ImmutableBytes,
        auxiliaryRecords: List<NdefRecord>,
        actor: NfcMdocActor,
        omitBleUuid: Boolean = false,
    ): NfcHandoverCarrier {
        require(carrierReference.size in 1..UByte.MAX_VALUE.toInt())
        val record = when (method) {
            is DeviceRetrievalMethod.Ble -> encodeBle(method, carrierReference, actor, omitBleUuid)
            is DeviceRetrievalMethod.Nfc -> encodeNfc(method, carrierReference)
            is DeviceRetrievalMethod.NfcV2 -> throw IllegalArgumentException("NFCv2 is never encoded as an NDEF carrier")
            is DeviceRetrievalMethod.WifiAware,
            is DeviceRetrievalMethod.Unknown -> throw IllegalArgumentException(
                "The retrieval method has no supported conventional NFC carrier encoding",
            )
        }
        return NfcHandoverCarrier(carrierRecord = record, auxiliaryRecords = auxiliaryRecords)
    }

    /** Decodes one resolved carrier into a method expressed in the named actor's role. */
    public fun decode(
        carrier: NfcResolvedCarrier,
        actor: NfcMdocActor,
        fallbackBleUuid: ByteArray? = null,
    ): DeviceRetrievalMethod? = when {
        carrier.carrierRecord.typeNameFormat == NdefTypeNameFormat.MIME_MEDIA &&
            carrier.carrierRecord.type == BLE_MIME_TYPE -> decodeBle(carrier.carrierRecord, actor, fallbackBleUuid)
        carrier.carrierRecord.typeNameFormat == NdefTypeNameFormat.EXTERNAL &&
            carrier.carrierRecord.type == NFC_EXTERNAL_TYPE -> decodeNfc(carrier.carrierRecord)
        else -> null
    }

    /** Decodes a conventional Handover Request carrier without inventing a holder-owned endpoint. */
    internal fun decodeReaderOffer(carrier: NfcResolvedCarrier): ReaderSelectedTransportOffer? = when {
        carrier.carrierRecord.typeNameFormat == NdefTypeNameFormat.MIME_MEDIA &&
            carrier.carrierRecord.type == BLE_MIME_TYPE -> {
            val decoded = parseBle(carrier.carrierRecord)
            val roles = decoded.roles(NfcMdocActor.READER)
            val options = decoded.peripheralServerOptions()
            val uuid = decoded.uuid
            require(options == null || roles.central) {
                "A reader peripheral endpoint requires offered mdoc central-client mode"
            }
            when {
                roles.central && uuid == null -> throw IllegalArgumentException(
                    "A reader offer for mdoc central-client mode must include its service UUID",
                )
                uuid != null -> ReaderSelectedTransportOffer.Method(
                    DeviceRetrievalMethod.Ble(
                        peripheralMode = BlePeripheralMode(uuid).takeIf { roles.peripheral },
                        centralMode = BleCentralMode(uuid).takeIf { roles.central },
                        peripheralEndpoint = options?.let(BlePeripheralEndpoint::Reader),
                    )
                )
                roles.peripheral -> ReaderSelectedTransportOffer.BlePeripheralServer
                else -> throw IllegalArgumentException("BLE Handover Request contains no usable holder role")
            }
        }
        carrier.carrierRecord.typeNameFormat == NdefTypeNameFormat.EXTERNAL &&
            carrier.carrierRecord.type == NFC_EXTERNAL_TYPE ->
            ReaderSelectedTransportOffer.Method(decodeNfc(carrier.carrierRecord))
        else -> null
    }

    private fun encodeNfc(method: DeviceRetrievalMethod.Nfc, reference: ImmutableBytes): NdefRecord {
        val bytes = MutableBytes()
        bytes.add(NFC_CARRIER_VERSION)
        bytes.addInteger(NFC_COMMAND_LENGTH_TYPE, method.maximumCommandDataLength.toInt())
        bytes.addInteger(NFC_RESPONSE_LENGTH_TYPE, method.maximumResponseDataLength.toInt())
        return NdefRecord(
            typeNameFormat = NdefTypeNameFormat.EXTERNAL,
            type = NFC_EXTERNAL_TYPE,
            identifier = reference,
            payload = ImmutableBytes.of(bytes.toByteArray()),
        )
    }

    private fun decodeNfc(record: NdefRecord): DeviceRetrievalMethod.Nfc {
        val cursor = Cursor(record.payload.copy())
        require(cursor.readByte() == NFC_CARRIER_VERSION) { "Unsupported mdoc NFC carrier version" }
        val command = cursor.readInteger(NFC_COMMAND_LENGTH_TYPE, "command-data length")
        val response = cursor.readInteger(NFC_RESPONSE_LENGTH_TYPE, "response-data length")
        require(cursor.exhausted) { "mdoc NFC carrier contains trailing data" }
        return DeviceRetrievalMethod.Nfc(command.toUInt(), response.toUInt())
    }

    private fun encodeBle(
        method: DeviceRetrievalMethod.Ble,
        reference: ImmutableBytes,
        actor: NfcMdocActor,
        omitUuid: Boolean,
    ): NdefRecord {
        val centralMode = method.centralMode
        val peripheralMode = method.peripheralMode
        when (method.peripheralEndpoint) {
            is BlePeripheralEndpoint.Mdoc -> require(actor == NfcMdocActor.HOLDER) {
                "An mdoc peripheral endpoint can only be encoded in the holder carrier"
            }
            is BlePeripheralEndpoint.Reader -> require(
                actor == NfcMdocActor.READER ||
                    (omitUuid && method.peripheralMode == null)
            ) {
                "A holder carrier may contain a reader endpoint only when selecting mdoc central-client mode"
            }
            null -> Unit
        }
        val (uuid, role) = when {
            centralMode != null && peripheralMode != null -> {
                require(centralMode.uuid.contentEquals(peripheralMode.uuid)) {
                    "A combined BLE carrier requires one shared service UUID"
                }
                centralMode.uuid to BLE_ROLE_ORIGINATOR_DUAL_CENTRAL_PREFERRED
            }
            centralMode != null -> centralMode.uuid to when (actor) {
                NfcMdocActor.HOLDER -> BLE_ROLE_ORIGINATOR_CENTRAL_ONLY
                NfcMdocActor.READER -> BLE_ROLE_ORIGINATOR_PERIPHERAL_ONLY
            }
            peripheralMode != null -> peripheralMode.uuid to when (actor) {
                NfcMdocActor.HOLDER -> BLE_ROLE_ORIGINATOR_PERIPHERAL_ONLY
                NfcMdocActor.READER -> BLE_ROLE_ORIGINATOR_CENTRAL_ONLY
            }
            else -> error("Validated BLE method contains no role")
        }
        val bytes = MutableBytes()
        bytes.addAd(BLE_AD_ROLE, byteArrayOf(role.toByte()))
        if (!omitUuid) bytes.addAd(BLE_AD_COMPLETE_128_BIT_UUIDS, uuid.reversedArray())
        method.peripheralEndpoint?.options?.deviceAddress?.let { bytes.addAd(BLE_AD_DEVICE_ADDRESS, it) }
        method.peripheralEndpoint?.options?.psm?.let { psm ->
            val serviceData = coseCompliantCbor.encodeToByteArray(
                CborElement.serializer(),
                CborMap(mapOf(CborInteger(0) to CborInteger(psm.toULong()))),
            )
            bytes.addAd(
                BLE_AD_SERVICE_DATA,
                byteArrayOf(BLE_SERVICE_DATA_UUID.toByte(), (BLE_SERVICE_DATA_UUID ushr 8).toByte()) + serviceData,
            )
        }
        return NdefRecord(
            typeNameFormat = NdefTypeNameFormat.MIME_MEDIA,
            type = BLE_MIME_TYPE,
            identifier = reference,
            payload = ImmutableBytes.of(bytes.toByteArray()),
        )
    }

    private fun decodeBle(
        record: NdefRecord,
        actor: NfcMdocActor,
        fallbackUuid: ByteArray?,
    ): DeviceRetrievalMethod.Ble {
        require(fallbackUuid == null || fallbackUuid.size == 16) { "Fallback BLE UUID must contain 16 bytes" }
        val decoded = parseBle(record)
        val roles = decoded.roles(actor)
        val exactUuid = decoded.uuid ?: fallbackUuid
            ?: throw IllegalArgumentException("BLE carrier is missing its service UUID")
        val endpoint = decoded.peripheralServerOptions()?.let { options ->
            when {
                actor == NfcMdocActor.READER -> BlePeripheralEndpoint.Reader(options)
                roles.central && !roles.peripheral -> BlePeripheralEndpoint.Reader(options)
                else -> BlePeripheralEndpoint.Mdoc(options)
            }
        }
        return DeviceRetrievalMethod.Ble(
            peripheralMode = BlePeripheralMode(exactUuid).takeIf { roles.peripheral },
            centralMode = BleCentralMode(exactUuid).takeIf { roles.central },
            peripheralEndpoint = endpoint,
        )
    }

    private fun parseBle(record: NdefRecord): DecodedBleCarrier {
        val cursor = Cursor(record.payload.copy())
        var role: Int? = null
        var uuid: ByteArray? = null
        var address: ByteArray? = null
        var psm: UInt? = null
        while (!cursor.exhausted) {
            val data = cursor.readAd()
            when (data.type) {
                BLE_AD_ROLE -> {
                    require(role == null && data.value.size == 1) { "BLE carrier must contain one valid role field" }
                    role = data.value[0].toInt() and 0xff
                }
                BLE_AD_COMPLETE_128_BIT_UUIDS -> {
                    require(uuid == null && data.value.size == 16) { "BLE carrier must contain at most one 128-bit UUID" }
                    uuid = data.value.reversedArray()
                }
                BLE_AD_DEVICE_ADDRESS -> {
                    require(address == null && data.value.size == 6) { "BLE carrier contains an invalid device address" }
                    address = data.value
                }
                BLE_AD_SERVICE_DATA -> {
                    require(data.value.size >= 2) { "BLE carrier contains invalid service data" }
                    val serviceUuid = (data.value[0].toInt() and 0xff) or
                        ((data.value[1].toInt() and 0xff) shl 8)
                    if (serviceUuid == BLE_SERVICE_DATA_UUID) {
                        require(psm == null && data.value.size >= 3) {
                            "BLE carrier must contain at most one valid mdoc service-data field"
                        }
                        val map = coseCompliantCbor.decodeFromByteArray<CborMap>(data.value.copyOfRange(2, data.value.size))
                        val value = map[CborInteger(0)] as? CborInteger
                            ?: throw IllegalArgumentException("BLE service data is missing its PSM")
                        require(value.isPositive && value.absoluteValue in 1uL..UShort.MAX_VALUE.toULong()) {
                            "BLE service-data PSM is outside the supported range"
                        }
                        psm = value.absoluteValue.toUInt()
                    }
                }
            }
        }
        val exactRole = requireNotNull(role) { "BLE carrier is missing its role" }
        require(exactRole in BLE_ROLE_ORIGINATOR_PERIPHERAL_ONLY..BLE_ROLE_ORIGINATOR_DUAL_CENTRAL_PREFERRED) {
            "BLE carrier contains a reserved role"
        }
        return DecodedBleCarrier(exactRole, uuid, address, psm)
    }

    private data class DecodedBleCarrier(
        val role: Int,
        val uuid: ByteArray?,
        val deviceAddress: ByteArray?,
        val psm: UInt?,
    ) {
        fun roles(actor: NfcMdocActor): DecodedBleRoles = DecodedBleRoles(
            central = when (role) {
                BLE_ROLE_ORIGINATOR_PERIPHERAL_ONLY -> actor == NfcMdocActor.READER
                BLE_ROLE_ORIGINATOR_CENTRAL_ONLY -> actor == NfcMdocActor.HOLDER
                else -> true
            },
            peripheral = when (role) {
                BLE_ROLE_ORIGINATOR_PERIPHERAL_ONLY -> actor == NfcMdocActor.HOLDER
                BLE_ROLE_ORIGINATOR_CENTRAL_ONLY -> actor == NfcMdocActor.READER
                else -> true
            },
        )

        fun peripheralServerOptions(): BlePeripheralServerOptions? =
            if (deviceAddress != null || psm != null) BlePeripheralServerOptions(deviceAddress, psm) else null
    }

    private data class DecodedBleRoles(val central: Boolean, val peripheral: Boolean)

    private class MutableBytes {
        private val values = mutableListOf<Byte>()
        fun add(value: Int) {
            require(value in 0..255)
            values += value.toByte()
        }
        fun add(bytes: ByteArray) { bytes.forEach(values::add) }
        fun addInteger(type: Int, value: Int) {
            require(value > 0)
            val encoded = when {
                value <= 0xff -> byteArrayOf(value.toByte())
                value <= 0xffff -> byteArrayOf((value ushr 8).toByte(), value.toByte())
                value <= 0xff_ffff -> byteArrayOf((value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte())
                else -> throw IllegalArgumentException("NFC carrier integer exceeds three bytes")
            }
            add(encoded.size + 1)
            add(type)
            add(encoded)
        }
        fun addAd(type: Int, value: ByteArray) {
            require(value.size in 1..254) { "BLE advertising data value must contain 1..254 bytes" }
            add(value.size + 1)
            add(type)
            add(value)
        }
        fun toByteArray(): ByteArray = values.toByteArray()
    }

    private data class AdvertisingData(val type: Int, val value: ByteArray)

    private class Cursor(private val bytes: ByteArray) {
        private var offset = 0
        val exhausted: Boolean get() = offset == bytes.size
        fun readByte(): Int {
            require(offset < bytes.size) { "Truncated NFC carrier data" }
            return bytes[offset++].toInt() and 0xff
        }
        fun readInteger(expectedType: Int, field: String): Int {
            val length = readByte()
            require(length in 2..4) { "NFC carrier $field has an invalid length" }
            require(readByte() == expectedType) { "NFC carrier $field has an invalid type" }
            var value = 0
            repeat(length - 1) { value = (value shl 8) or readByte() }
            require(value > 0 && (length == 2 || value > (1 shl (8 * (length - 2))) - 1)) {
                "NFC carrier $field is not minimally encoded"
            }
            return value
        }
        fun readAd(): AdvertisingData {
            val length = readByte()
            require(length >= 1 && length <= bytes.size - offset) { "Truncated BLE advertising data" }
            val type = readByte()
            val valueLength = length - 1
            return AdvertisingData(type, bytes.copyOfRange(offset, offset + valueLength)).also {
                offset += valueLength
            }
        }
    }

    private const val NFC_CARRIER_VERSION = 0x01
    private const val NFC_COMMAND_LENGTH_TYPE = 0x01
    private const val NFC_RESPONSE_LENGTH_TYPE = 0x02
    private const val BLE_AD_COMPLETE_128_BIT_UUIDS = 0x07
    private const val BLE_AD_SERVICE_DATA = 0x16
    private const val BLE_AD_DEVICE_ADDRESS = 0x1b
    private const val BLE_AD_ROLE = 0x1c
    // Bluetooth LE Role AD values describe the record originator, not always the holder.
    private const val BLE_ROLE_ORIGINATOR_PERIPHERAL_ONLY = 0x00
    private const val BLE_ROLE_ORIGINATOR_CENTRAL_ONLY = 0x01
    private const val BLE_ROLE_ORIGINATOR_DUAL_CENTRAL_PREFERRED = 0x03
    // Provisional value used by pinned Multipaz 0.100.0; the available DIS still contains XXXX.
    private const val BLE_SERVICE_DATA_UUID = 0xff01
}
