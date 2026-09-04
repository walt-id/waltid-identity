package id.walt.mdoc.proximity.mobile

import id.walt.mdoc.crypto.MdocKdf
import id.walt.mdoc.proximity.ImmutableBytes
import id.walt.mdoc.proximity.ProximityError
import id.walt.mdoc.proximity.ProximityException

internal object BleGattUuid {
    const val MDOC_STATE = "00000001-a123-48ce-896b-4c76973373e6"
    const val MDOC_CLIENT_TO_SERVER = "00000002-a123-48ce-896b-4c76973373e6"
    const val MDOC_SERVER_TO_CLIENT = "00000003-a123-48ce-896b-4c76973373e6"
    const val READER_STATE = "00000005-a123-48ce-896b-4c76973373e6"
    const val READER_CLIENT_TO_SERVER = "00000006-a123-48ce-896b-4c76973373e6"
    const val READER_SERVER_TO_CLIENT = "00000007-a123-48ce-896b-4c76973373e6"
    const val READER_IDENT = "00000008-a123-48ce-896b-4c76973373e6"
    const val MDOC_L2CAP_PSM = "0000000a-a123-48ce-896b-4c76973373e6"
    const val READER_L2CAP_PSM = "0000000b-a123-48ce-896b-4c76973373e6"
    const val CLIENT_CHARACTERISTIC_CONFIGURATION = "00002902-0000-1000-8000-00805f9b34fb"
}

internal const val BLE_STATE_START: Byte = 0x01
internal const val BLE_STATE_END: Byte = 0x02
internal const val BLE_MAX_GATT_PACKET_BYTES: Int = 512

internal enum class BlePeripheralStateCommandResult {
    REJECTED,
    STARTED,
    TERMINATE;

    val accepted: Boolean get() = this != REJECTED
}

internal fun evaluateBlePeripheralStateCommand(
    value: ByteArray,
    notificationsReady: Boolean,
    start: () -> Boolean,
): BlePeripheralStateCommandResult = when {
    value.contentEquals(byteArrayOf(BLE_STATE_START)) ->
        if (notificationsReady && start()) BlePeripheralStateCommandResult.STARTED
        else BlePeripheralStateCommandResult.REJECTED
    value.contentEquals(byteArrayOf(BLE_STATE_END)) ->
        BlePeripheralStateCommandResult.TERMINATE
    else -> BlePeripheralStateCommandResult.REJECTED
}

internal object BleIdent {
    private val INFO = "BLEIdent".encodeToByteArray()
    // RFC 5869 represents an omitted salt as HashLen zero octets. MdocKdf's HMAC
    // implementation rejects an empty key, so pass that equivalent representation explicitly.
    private val NO_SALT = ByteArray(32)

    fun derive(eDeviceKeyBytes: ImmutableBytes): ByteArray = MdocKdf.deriveSha256(
        inputKeyMaterial = eDeviceKeyBytes.copy(),
        salt = NO_SALT,
        info = INFO,
        length = 16,
    )

    fun matches(expected: ByteArray, actual: ByteArray): Boolean {
        var difference = expected.size xor actual.size
        for (index in 0 until maxOf(expected.size, actual.size)) {
            difference = difference or (
                expected.getOrElse(index) { 0 }.toInt() xor actual.getOrElse(index) { 0 }.toInt()
            )
        }
        return difference == 0
    }
}

internal object BlePsmCodec {
    private val DYNAMIC_LE_PSM_RANGE: UIntRange = 0x0080u..0x00ffu

    fun isDynamicLe(psm: UInt): Boolean = psm in DYNAMIC_LE_PSM_RANGE

    fun encode(psm: UInt): ByteArray {
        require(isDynamicLe(psm)) { "A BLE L2CAP PSM must be in the dynamic LE range 0x0080..0x00ff" }
        return byteArrayOf(
            (psm shr 24).toByte(),
            (psm shr 16).toByte(),
            (psm shr 8).toByte(),
            psm.toByte(),
        )
    }

    fun decode(encoded: ByteArray): UInt {
        if (encoded.size != 4) throw protocolFailure("invalid_l2cap_psm", "A BLE L2CAP PSM must contain four bytes")
        val value = encoded.fold(0u) { result, byte -> (result shl 8) or byte.toUByte().toUInt() }
        if (!isDynamicLe(value)) throw protocolFailure(
            "invalid_l2cap_psm",
            "A BLE L2CAP PSM must be in the dynamic LE range 0x0080..0x00ff",
        )
        return value
    }
}

internal class BleGattMessageCodec(private val maximumMessageBytes: Int) {
    private val received = GrowingByteBuffer(maximumMessageBytes)
    private var awaitingFinalChunk = false

    init {
        require(maximumMessageBytes > 0)
    }

    fun encode(message: ImmutableBytes, maximumPacketBytes: Int): List<ByteArray> {
        require(maximumPacketBytes in 2..BLE_MAX_GATT_PACKET_BYTES) {
            "A GATT packet must leave room for the ISO chunk marker and payload"
        }
        if (message.size > maximumMessageBytes) throw messageTooLarge(message.size.toLong(), maximumMessageBytes)
        val bytes = message.copy()
        val payloadBytes = maximumPacketBytes - 1
        if (bytes.isEmpty()) return listOf(byteArrayOf(0x00))
        return buildList {
            var offset = 0
            while (offset < bytes.size) {
                val count = minOf(payloadBytes, bytes.size - offset)
                val hasMore = offset + count < bytes.size
                add(ByteArray(count + 1).also { packet ->
                    packet[0] = if (hasMore) 0x01 else 0x00
                    bytes.copyInto(packet, destinationOffset = 1, startIndex = offset, endIndex = offset + count)
                })
                offset += count
            }
        }
    }

    fun decode(packet: ByteArray): ImmutableBytes? {
        if (packet.isEmpty()) throw protocolFailure("invalid_gatt_chunk", "A GATT data chunk must not be empty")
        if (packet[0] != 0x00.toByte() && packet[0] != 0x01.toByte()) {
            throw protocolFailure("invalid_gatt_chunk", "A GATT data chunk has an unknown continuation marker")
        }
        received.append(packet, 1, packet.size)
        if (packet[0] == 0x01.toByte()) {
            awaitingFinalChunk = true
            return null
        }
        awaitingFinalChunk = false
        return ImmutableBytes.of(received.takeAndReset())
    }

    fun hasIncompleteMessage(): Boolean = awaitingFinalChunk
}

internal object BleL2capMessageCodec {
    const val HEADER_BYTES = 4

    fun encode(message: ImmutableBytes, maximumMessageBytes: Int): ByteArray {
        if (message.size > maximumMessageBytes) throw messageTooLarge(message.size.toLong(), maximumMessageBytes)
        val output = ByteArray(HEADER_BYTES + message.size)
        val size = message.size.toUInt()
        output[0] = (size shr 24).toByte()
        output[1] = (size shr 16).toByte()
        output[2] = (size shr 8).toByte()
        output[3] = size.toByte()
        message.copy().copyInto(output, destinationOffset = HEADER_BYTES)
        return output
    }
}

internal class BleL2capMessageDecoder(private val maximumMessageBytes: Int) {
    private val header = ByteArray(BleL2capMessageCodec.HEADER_BYTES)
    private var headerBytes = 0
    private var payload: ByteArray? = null
    private var payloadBytes = 0

    init {
        require(maximumMessageBytes > 0)
    }

    fun feed(bytes: ByteArray): List<ImmutableBytes> {
        val messages = mutableListOf<ImmutableBytes>()
        var offset = 0
        while (offset < bytes.size) {
            val currentPayload = payload
            if (currentPayload == null) {
                val count = minOf(header.size - headerBytes, bytes.size - offset)
                bytes.copyInto(header, destinationOffset = headerBytes, startIndex = offset, endIndex = offset + count)
                headerBytes += count
                offset += count
                if (headerBytes == header.size) {
                    val messageBytes = header.fold(0u) { result, byte ->
                        (result shl 8) or byte.toUByte().toUInt()
                    }
                    if (messageBytes > maximumMessageBytes.toUInt()) {
                        throw messageTooLarge(messageBytes.toLong(), maximumMessageBytes)
                    }
                    headerBytes = 0
                    if (messageBytes == 0u) messages += ImmutableBytes.of(ByteArray(0))
                    else payload = ByteArray(messageBytes.toInt())
                }
            } else {
                val count = minOf(currentPayload.size - payloadBytes, bytes.size - offset)
                bytes.copyInto(currentPayload, destinationOffset = payloadBytes, startIndex = offset, endIndex = offset + count)
                payloadBytes += count
                offset += count
                if (payloadBytes == currentPayload.size) {
                    messages += ImmutableBytes.of(currentPayload)
                    payload = null
                    payloadBytes = 0
                }
            }
        }
        return messages
    }

    fun hasIncompleteFrame(): Boolean = headerBytes != 0 || payload != null
}

private class GrowingByteBuffer(private val maximumSize: Int) {
    private var value = ByteArray(minOf(256, maximumSize.coerceAtLeast(1)))
    var size: Int = 0
        private set

    fun append(bytes: ByteArray, startIndex: Int, endIndex: Int) {
        require(startIndex in 0..endIndex && endIndex <= bytes.size)
        val count = endIndex - startIndex
        if (count > maximumSize - size) throw messageTooLarge(size.toLong() + count, maximumSize)
        ensureCapacity(size + count)
        bytes.copyInto(value, destinationOffset = size, startIndex = startIndex, endIndex = endIndex)
        size += count
    }

    fun takeAndReset(): ByteArray = value.copyOf(size).also { size = 0 }

    private fun ensureCapacity(required: Int) {
        if (required <= value.size) return
        var capacity = value.size
        while (capacity < required) capacity = minOf(maximumSize, maxOf(capacity * 2, required))
        value = value.copyOf(capacity)
    }
}

private fun messageTooLarge(actual: Long, maximum: Int) = protocolFailure(
    "message_too_large",
    "BLE message size $actual exceeds the configured limit $maximum",
)

private fun protocolFailure(code: String, message: String) = ProximityException(ProximityError.Protocol(code, message))
