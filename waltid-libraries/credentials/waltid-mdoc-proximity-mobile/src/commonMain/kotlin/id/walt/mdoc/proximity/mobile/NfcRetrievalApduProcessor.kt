package id.walt.mdoc.proximity.mobile

import id.walt.mdoc.objects.engagement.DeviceRetrievalMethod
import id.walt.mdoc.proximity.ImmutableBytes

/** Observable conventional NFC retrieval lifecycle. */
internal enum class NfcRetrievalState {
    AWAITING_APPLICATION_SELECTION,
    READY,
    RECEIVING_COMMAND,
    AWAITING_WALLET_RESPONSE,
    SENDING_RESPONSE,
    DEACTIVATED,
}

/** Result of processing one reader APDU. */
internal sealed interface NfcRetrievalApduResult {
    public data class Response(public val encoded: ImmutableBytes) : NfcRetrievalApduResult

    /** The platform must defer this APDU's response until [NfcRetrievalApduProcessor.completeResponse]. */
    public data class Request(
        public val identifier: ULong,
        public val sessionMessage: ImmutableBytes,
    ) : NfcRetrievalApduResult
}

/**
 * Bounded conventional mdoc NFC data-retrieval state machine.
 *
 * It owns ISO 7816 command chaining, DO53 encapsulation, exactly one pending wallet response, response
 * chunking, `61xx`, and `GET RESPONSE`. Platform adapters only route APDUs and completion bytes.
 */
internal class NfcRetrievalApduProcessor(
    public val connectionMethod: DeviceRetrievalMethod.Nfc,
    private val maximumSessionMessageBytes: Int,
) {
    public var state: NfcRetrievalState = NfcRetrievalState.AWAITING_APPLICATION_SELECTION
        private set

    private var pendingIdentifier: ULong? = null
    private var nextIdentifier: ULong = 0u
    private val exchange = NfcApduMessageExchange(
        connectionMethod.maximumCommandDataLength.toInt(),
        connectionMethod.maximumResponseDataLength.toInt(),
        maximumSessionMessageBytes,
    )

    init {
        require(maximumSessionMessageBytes > 0)
    }

    public fun process(encodedCommand: ByteArray): NfcRetrievalApduResult {
        if (state == NfcRetrievalState.DEACTIVATED) return response(NfcStatusWord.CONDITIONS_NOT_SATISFIED)
        val command = try {
            NfcCommandApdu.decode(encodedCommand)
        } catch (_: IllegalArgumentException) {
            return response(NfcStatusWord.WRONG_LENGTH)
        }
        return try {
            when (command.instruction.toInt()) {
                SELECT_INSTRUCTION -> select(command)
                ENVELOPE_INSTRUCTION -> envelope(command)
                GET_RESPONSE_INSTRUCTION -> getResponse(command)
                else -> response(NfcStatusWord.INSTRUCTION_NOT_SUPPORTED)
            }
        } catch (_: IllegalArgumentException) {
            fail(NfcStatusWord.WRONG_DATA)
        } catch (_: IllegalStateException) {
            fail(NfcStatusWord.CONDITIONS_NOT_SATISFIED)
        }
    }

    /** Completes the one pending ENVELOPE response and returns its first response APDU. */
    public fun completeResponse(identifier: ULong, sessionMessage: ByteArray): ImmutableBytes {
        check(state == NfcRetrievalState.AWAITING_WALLET_RESPONSE && pendingIdentifier == identifier) {
            "NFC response does not own the current pending request"
        }
        require(sessionMessage.size <= maximumSessionMessageBytes) { "Session response exceeds the configured limit" }
        val response = exchange.stageResponse(sessionMessage)
        pendingIdentifier = null
        state = if (exchange.hasOutgoingData) NfcRetrievalState.SENDING_RESPONSE else NfcRetrievalState.READY
        return ImmutableBytes.of(response.encode())
    }

    internal fun cancelPendingResponse(identifier: ULong) {
        if (state != NfcRetrievalState.AWAITING_WALLET_RESPONSE || pendingIdentifier != identifier) return
        pendingIdentifier = null
        exchange.reset()
        state = NfcRetrievalState.DEACTIVATED
    }

    /** Invalidates command fragments, response ownership, and all future callbacks for this interaction. */
    public fun deactivate() {
        pendingIdentifier = null
        exchange.reset()
        state = NfcRetrievalState.DEACTIVATED
    }

    private fun select(command: NfcCommandApdu): NfcRetrievalApduResult {
        if (state != NfcRetrievalState.AWAITING_APPLICATION_SELECTION) {
            return response(NfcStatusWord.CONDITIONS_NOT_SATISFIED)
        }
        if (command.cla != 0.toUByte()) return response(NfcStatusWord.CLASS_NOT_SUPPORTED)
        if (command.parameter1.toInt() != SELECT_BY_NAME || command.parameter2.toInt() != SELECT_NO_RESPONSE_DATA) {
            return response(NfcStatusWord.INCORRECT_PARAMETERS)
        }
        if (command.expectedResponseDataLength != null) return response(NfcStatusWord.WRONG_LENGTH)
        if (!command.data.contentEquals(MdocNfcAid.DATA_TRANSFER.copy())) {
            return response(NfcStatusWord.FILE_NOT_FOUND)
        }
        state = NfcRetrievalState.READY
        return response(NfcStatusWord.SUCCESS)
    }

    private fun envelope(command: NfcCommandApdu): NfcRetrievalApduResult {
        if (state !in setOf(NfcRetrievalState.READY, NfcRetrievalState.RECEIVING_COMMAND)) {
            return response(NfcStatusWord.CONDITIONS_NOT_SATISFIED)
        }
        if (command.cla.toInt() !in setOf(0x00, 0x10)) return response(NfcStatusWord.CLASS_NOT_SUPPORTED)
        if (command.parameter1 != 0.toUByte() || command.parameter2 != 0.toUByte()) {
            return response(NfcStatusWord.INCORRECT_PARAMETERS)
        }
        return when (val incoming = exchange.accept(command)) {
            is NfcApduMessageExchange.IncomingResult.Continue -> {
                state = NfcRetrievalState.RECEIVING_COMMAND
                NfcRetrievalApduResult.Response(ImmutableBytes.of(incoming.response.encode()))
            }
            is NfcApduMessageExchange.IncomingResult.Message -> {
                val identifier = nextIdentifier
                check(nextIdentifier != ULong.MAX_VALUE) { "NFC pending-response identifier is exhausted" }
                nextIdentifier++
                pendingIdentifier = identifier
                state = NfcRetrievalState.AWAITING_WALLET_RESPONSE
                NfcRetrievalApduResult.Request(identifier, ImmutableBytes.of(incoming.bytes))
            }
        }
    }

    private fun getResponse(command: NfcCommandApdu): NfcRetrievalApduResult {
        if (state != NfcRetrievalState.SENDING_RESPONSE || !exchange.hasOutgoingData) {
            return response(NfcStatusWord.CONDITIONS_NOT_SATISFIED)
        }
        if (command.cla != 0.toUByte()) return response(NfcStatusWord.CLASS_NOT_SUPPORTED)
        if (command.parameter1 != 0.toUByte() || command.parameter2 != 0.toUByte() || command.data.size != 0) {
            return response(NfcStatusWord.INCORRECT_PARAMETERS)
        }
        val response = exchange.getResponse(command)
        state = if (exchange.hasOutgoingData) NfcRetrievalState.SENDING_RESPONSE else NfcRetrievalState.READY
        return NfcRetrievalApduResult.Response(ImmutableBytes.of(response.encode()))
    }

    private fun fail(status: UShort): NfcRetrievalApduResult {
        pendingIdentifier = null
        exchange.reset()
        state = NfcRetrievalState.DEACTIVATED
        return response(status)
    }

    private fun response(status: UShort): NfcRetrievalApduResult.Response = NfcRetrievalApduResult.Response(
        ImmutableBytes.of(NfcResponseApdu(statusWord = status).encode()),
    )

    private companion object {
        const val SELECT_INSTRUCTION: Int = 0xa4
        const val ENVELOPE_INSTRUCTION: Int = 0xc3
        const val GET_RESPONSE_INSTRUCTION: Int = 0xc0
        const val SELECT_BY_NAME: Int = 0x04
        const val SELECT_NO_RESPONSE_DATA: Int = 0x0c
    }
}

/** Canonical BER-TLV data object `53` encapsulation used by conventional NFC retrieval. */
internal object NfcDo53 {
    /** Largest payload represented by the supported canonical three-byte BER length form. */
    public const val MAXIMUM_SESSION_MESSAGE_LENGTH: Int = 0xff_ffff

    public fun encode(sessionMessage: ByteArray): ByteArray {
        val length = sessionMessage.size
        val lengthBytes = when {
            length < 0x80 -> byteArrayOf(length.toByte())
            length <= 0xff -> byteArrayOf(0x81.toByte(), length.toByte())
            length <= 0xffff -> byteArrayOf(0x82.toByte(), (length ushr 8).toByte(), length.toByte())
            length <= MAXIMUM_SESSION_MESSAGE_LENGTH -> byteArrayOf(
                0x83.toByte(), (length ushr 16).toByte(), (length ushr 8).toByte(), length.toByte(),
            )
            else -> throw IllegalArgumentException("DO53 session message exceeds the supported length")
        }
        return byteArrayOf(0x53) + lengthBytes + sessionMessage
    }

    public fun decode(encoded: ByteArray, maximumSessionMessageBytes: Int): ByteArray {
        require(maximumSessionMessageBytes >= 0) { "Maximum DO53 session message size must not be negative" }
        require(encoded.size >= 2 && encoded[0] == 0x53.toByte()) { "NFC message must use data object 53" }
        val firstLength = encoded[1].toInt() and 0xff
        val (length, headerLength) = when {
            firstLength < 0x80 -> firstLength to 2
            firstLength == 0x81 -> {
                require(encoded.size >= 3)
                val value = encoded[2].toInt() and 0xff
                require(value >= 0x80) { "DO53 length must use minimal encoding" }
                value to 3
            }
            firstLength == 0x82 -> {
                require(encoded.size >= 4)
                val value = ((encoded[2].toInt() and 0xff) shl 8) or (encoded[3].toInt() and 0xff)
                require(value > 0xff) { "DO53 length must use minimal encoding" }
                value to 4
            }
            firstLength == 0x83 -> {
                require(encoded.size >= 5)
                val value = ((encoded[2].toInt() and 0xff) shl 16) or
                    ((encoded[3].toInt() and 0xff) shl 8) or (encoded[4].toInt() and 0xff)
                require(value > 0xffff) { "DO53 length must use minimal encoding" }
                value to 5
            }
            else -> throw IllegalArgumentException("Unsupported or indefinite DO53 length")
        }
        require(length <= maximumSessionMessageBytes) { "DO53 session message exceeds the configured limit" }
        require(encoded.size == headerLength + length) { "DO53 length does not match its payload" }
        return encoded.copyOfRange(headerLength, encoded.size)
    }
}
