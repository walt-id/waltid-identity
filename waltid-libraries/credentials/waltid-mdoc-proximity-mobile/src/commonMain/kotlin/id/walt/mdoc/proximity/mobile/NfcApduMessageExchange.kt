package id.walt.mdoc.proximity.mobile

import id.walt.mdoc.proximity.ImmutableBytes

/** Shared ISO 7816 ENVELOPE/GET RESPONSE mechanism used by conventional NFC and NFCv2. */
internal class NfcApduMessageExchange(
    private val maximumCommandDataLength: Int,
    maximumResponseDataLength: Int,
    private val maximumMessageBytes: Int,
) {
    private var maximumResponseDataLength: Int = maximumResponseDataLength
    sealed interface IncomingResult {
        data class Continue(val response: NfcResponseApdu) : IncomingResult
        data class Message(val bytes: ByteArray) : IncomingResult
    }

    private val incoming = NfcIncomingAccumulator(maximumMessageBytes + MAXIMUM_DO53_OVERHEAD)
    private var responseLength = 0
    private var outgoing: ByteArray? = null
    private var outgoingOffset = 0

    init {
        require(maximumCommandDataLength in 1..NfcCommandApdu.MAX_RESPONSE_DATA_LENGTH) {
            "Maximum NFC command data length must be in 1..65536"
        }
        require(maximumResponseDataLength in 1..NfcCommandApdu.MAX_RESPONSE_DATA_LENGTH) {
            "Maximum NFC response data length must be in 1..65536"
        }
        require(maximumMessageBytes in 1..NfcDo53.MAXIMUM_SESSION_MESSAGE_LENGTH) {
            "Maximum NFC message size exceeds the supported DO53 length"
        }
    }

    fun accept(command: NfcCommandApdu): IncomingResult {
        require(command.instruction == ENVELOPE_INSTRUCTION) { "ENVELOPE instruction is invalid" }
        require(command.cla.toInt() in setOf(0x00, 0x10)) { "ENVELOPE command class is invalid" }
        require(command.parameter1 == 0.toUByte() && command.parameter2 == 0.toUByte()) {
            "ENVELOPE command parameters are invalid"
        }
        require(command.data.size <= maximumCommandDataLength) { "ENVELOPE command data exceeds the advertised limit" }
        require(command.data.size <= incoming.remainingCapacity) {
            "Chained ENVELOPE data exceeds the configured limit"
        }
        if (command.isChained) {
            require(command.expectedResponseDataLength == null) { "A chained ENVELOPE fragment cannot contain Le" }
            incoming.append(command.data.copy())
            return IncomingResult.Continue(NfcResponseApdu(statusWord = NfcStatusWord.SUCCESS))
        }
        val expectedResponseDataLength = command.expectedResponseDataLength
            ?: throw IllegalArgumentException("Final ENVELOPE must contain Le")
        incoming.append(command.data.copy())
        responseLength = minOf(
            expectedResponseDataLength,
            maximumResponseDataLength,
        )
        val message = NfcDo53.decode(incoming.takeAndReset(), maximumMessageBytes)
        return IncomingResult.Message(message)
    }

    fun stageResponse(message: ByteArray): NfcResponseApdu {
        require(outgoing == null) { "An NFC response is already staged" }
        require(message.size <= maximumMessageBytes) { "NFC response exceeds the configured limit" }
        outgoing = NfcDo53.encode(message)
        outgoingOffset = 0
        return nextResponse(responseLength)
    }

    fun getResponse(command: NfcCommandApdu): NfcResponseApdu {
        require(
            command.cla == 0.toUByte() && command.instruction == GET_RESPONSE_INSTRUCTION &&
                command.parameter1 == 0.toUByte() &&
                command.parameter2 == 0.toUByte() && command.data.size == 0
        ) { "GET RESPONSE command is invalid" }
        val requested = command.expectedResponseDataLength
            ?: throw IllegalArgumentException("GET RESPONSE must contain Le")
        require(outgoing != null) { "No NFC response bytes remain" }
        return nextResponse(minOf(requested, maximumResponseDataLength))
    }

    fun reset() {
        incoming.clear()
        responseLength = 0
        outgoing = null
        outgoingOffset = 0
    }

    val hasOutgoingData: Boolean get() = outgoing != null

    fun setMaximumResponseDataLength(value: Int) {
        require(value in 1..NfcCommandApdu.MAX_RESPONSE_DATA_LENGTH)
        require(outgoing == null) { "Cannot change an NFC response limit while bytes remain staged" }
        maximumResponseDataLength = value
        responseLength = minOf(responseLength, value)
    }

    private fun nextResponse(maximumDataLength: Int): NfcResponseApdu {
        require(maximumDataLength > 0)
        val bytes = checkNotNull(outgoing)
        val end = outgoingOffset + minOf(maximumDataLength, bytes.size - outgoingOffset)
        val chunk = bytes.copyOfRange(outgoingOffset, end)
        outgoingOffset = end
        val remaining = bytes.size - end
        val status = if (remaining == 0) NfcStatusWord.SUCCESS else NfcStatusWord.moreData(remaining)
        if (remaining == 0) {
            outgoing = null
            outgoingOffset = 0
            responseLength = 0
        }
        return NfcResponseApdu(ImmutableBytes.of(chunk), status)
    }

    private companion object {
        const val MAXIMUM_DO53_OVERHEAD: Int = 5
        val ENVELOPE_INSTRUCTION: UByte = 0xc3u
        val GET_RESPONSE_INSTRUCTION: UByte = 0xc0u
    }
}

/** Bounded chunk accumulator that avoids boxing every peer-controlled byte. */
private class NfcIncomingAccumulator(private val maximumSize: Int) {
    private val chunks = mutableListOf<ByteArray>()
    private var size = 0

    val remainingCapacity: Int get() = maximumSize - size

    fun append(bytes: ByteArray) {
        require(bytes.size <= remainingCapacity) { "Chained ENVELOPE data exceeds the configured limit" }
        if (bytes.isNotEmpty()) chunks += bytes.copyOf()
        size += bytes.size
    }

    fun takeAndReset(): ByteArray {
        val result = ByteArray(size)
        var offset = 0
        chunks.forEach { chunk ->
            chunk.copyInto(result, offset)
            offset += chunk.size
        }
        clear()
        return result
    }

    fun clear() {
        chunks.clear()
        size = 0
    }
}
