/*
 * Derived from OpenWallet Foundation Multipaz 0.100.0 (Apache-2.0).
 * See README.md and LICENSE in this directory for provenance and the modification ledger.
 */
package id.walt.wallet2.mobile.peer

import org.multipaz.mdoc.transport.*

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.ByteStringBuilder
import kotlinx.io.bytestring.append
import kotlinx.io.bytestring.buildByteString
import org.multipaz.crypto.EcPublicKey
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethodNfc
import org.multipaz.mdoc.role.MdocRole
import org.multipaz.nfc.CommandApdu
import org.multipaz.nfc.Nfc
import org.multipaz.nfc.NfcCommandFailedException
import org.multipaz.nfc.NfcIsoTag
import org.multipaz.nfc.ResponseApdu
import org.multipaz.util.ByteDataReader
import org.multipaz.util.Logger
import org.multipaz.util.appendByteString
import org.multipaz.util.appendUInt16
import org.multipaz.util.appendUInt8
import kotlin.math.min
import kotlin.time.Duration

internal class SelectCorrectedNfcReader(
    override val role: MdocRole,
    private val options: MdocTransportOptions,
    override val connectionMethod: MdocConnectionMethodNfc
) : MdocTransport() {
    companion object {
        private const val TAG = "NfcTransportMdocReader"
    }

    private val mutex = Mutex()

    private lateinit var tag: NfcIsoTag

    private val _state = MutableStateFlow<State>(State.IDLE)
    override val state: StateFlow<State> = _state.asStateFlow()

    override val scanningTime: Duration?
        get() = null

    override suspend fun advertise() {
    }

    private var commandDataFieldMaxLength: Int = connectionMethod.commandDataFieldMaxLength.toInt()
    private var responseDataFieldMaxLength: Int = connectionMethod.responseDataFieldMaxLength.toInt()

    /**
     * Set underlying [NfcIsoTag] to use
     *
     * @param tag the tag to use.
     */
    fun setTag(tag: NfcIsoTag) {
        this.tag = tag
        commandDataFieldMaxLength = min(
            connectionMethod.commandDataFieldMaxLength.toInt(),
            tag.maxTransceiveLength - 7
        )
        responseDataFieldMaxLength = min(
            connectionMethod.responseDataFieldMaxLength.toInt(),
            tag.maxTransceiveLength - 7
        )
    }

    /**
     * Updates the message on the NFC scanning dialog
     *
     * @param message the message to show.
     */
    suspend fun updateDialogMessage(message: String) {
        tag.updateDialogMessage(message)
    }

    private var ioJob: Job? = null

    override suspend fun open(eSenderKey: EcPublicKey) {
        mutex.withLock {
            check(_state.value == State.IDLE) { "Expected state IDLE, got ${_state.value}" }
            try {
                _state.value = State.CONNECTING
                // Selected ISO/IEC DIS 18013-5:2026 clause 11.2, Table 8 requires P2=0C.
                val selected = tag.transceive(CommandApdu(0, Nfc.INS_SELECT, 0x04, 0x0c,
                    Nfc.ISO_MDOC_NFC_DATA_TRANSFER_APPLICATION_ID, 0))
                if (selected.status != Nfc.RESPONSE_STATUS_SUCCESS) {
                    throw NfcCommandFailedException("Retrieval SELECT failed", selected.status)
                }
                _state.value = State.CONNECTED
            } catch (error: Exception) {
                failTransport(error)
                throw error.wrapUnlessCancellationException("Failed while opening transport")
            }
        }

        ioJob = CoroutineScope(currentCoroutineContext()).launch {
            try {
                while (true) {
                    val messageToSend = writingQueue.receive()
                    val responseMessage = nfcTransceive(messageToSend)
                    incomingMessages.send(responseMessage)
                }
            } catch (e: Exception) {
                Logger.i(TAG, "Error while waiting for message to send", e)
                e.printStackTrace()
                mutex.withLock {
                    failTransport(e)
                }
            }
        }
    }

    private val writingQueue = Channel<ByteString>(Channel.UNLIMITED)
    private val incomingMessages = Channel<ByteString>(Channel.UNLIMITED)

    override suspend fun sendMessage(message: ByteArray) {
        mutex.withLock {
            check(_state.value == State.CONNECTED) { "Expected state CONNECTED, got ${_state.value}" }
            Logger.i(TAG, "sendMessage")
        }
        writingQueue.send(ByteString(message))
    }

    private suspend fun nfcTransceive(message: ByteString): ByteString {
        val encMessage = encapsulateInDo53(message)

        val maxChunkSize = commandDataFieldMaxLength
        val offsets = 0 until encMessage.size step maxChunkSize
        var lastEnvelopeResponse: ResponseApdu? = null
        for (offset in offsets) {
            val moreDataComing = (offset != offsets.last)
            val size = min(maxChunkSize, encMessage.size - offset)
            val le = if (!moreDataComing) {
                responseDataFieldMaxLength
            } else {
                0
            }
            val response = tag.transceive(
                CommandApdu(
                    cla = if (moreDataComing) Nfc.CLA_CHAIN_NOT_LAST else Nfc.CLA_CHAIN_LAST,
                    ins = Nfc.INS_ENVELOPE,
                    p1 = 0x00,
                    p2 = 0x00,
                    payload = ByteString(encMessage.toByteArray(), offset, offset + size),
                    le = le
                )
            )
            if (response.status != Nfc.RESPONSE_STATUS_SUCCESS && response.status.and(0xff00) != 0x6100) {
                throw NfcCommandFailedException("Unexpected ENVELOPE status ${response.statusHexString}", response.status)
            }
            lastEnvelopeResponse = response
        }
        Logger.i(TAG, "Successfully sent message, waiting for response")

        check(lastEnvelopeResponse != null)
        val encapsulatedMessageBuilder = ByteStringBuilder()
        encapsulatedMessageBuilder.append(lastEnvelopeResponse.payload)
        if (lastEnvelopeResponse.status == Nfc.RESPONSE_STATUS_SUCCESS) {
            // Woohoo, entire response fits
            Logger.i(TAG, "Entire response fits")
        } else {
            // More bytes are coming, have to use GET RESPONSE to get the rest

            var leForGetResponse = responseDataFieldMaxLength
            if (lastEnvelopeResponse.status.and(0xff) != 0) {
                leForGetResponse = lastEnvelopeResponse.status.and(0xff)
            }
            while (true) {
                Logger.i(TAG, "Sending GET RESPONSE")
                val response = tag.transceive(
                    CommandApdu(
                        cla = 0x00,
                        ins = Nfc.INS_GET_RESPONSE,
                        p1 = 0x00,
                        p2 = 0x00,
                        payload = ByteString(),
                        le = leForGetResponse
                    )
                )
                encapsulatedMessageBuilder.append(response.payload)
                if (response.status == Nfc.RESPONSE_STATUS_SUCCESS) {
                    /* If Le ≥ the number of available bytes, the mdoc shall include
                     * all available bytes in the response and set the status words
                     * to ’90 00’.
                     */
                    break
                } else if (response.status == 0x6100) {
                    /* If the number of available bytes > Le + 255, the mdoc shall
                     * include as many bytes in the response as indicated by Le and
                     * shall set the status words to ’61 00’. The mdoc reader shall
                     * respond with a GET RESPONSE command where Le is set to the
                     * maximum length of the response data field that is supported
                     * by both the mdoc and the mdoc reader.
                     */
                    leForGetResponse = responseDataFieldMaxLength
                } else if (response.status.and(0xff00) == 0x6100) {
                    /* If Le < the number of available bytes ≤ Le + 255, the
                     * mdoc shall include as many bytes in the response as
                     * indicated by Le and shall set the status words to ’61 XX’,
                     * where XX is the number of available bytes remaining. The
                     * mdoc reader shall respond with a GET RESPONSE command where
                     * Le is set to XX.
                     */
                    leForGetResponse = response.status.and(0xff)
                } else {
                    throw NfcCommandFailedException(
                        "Unexpected GET RESPONSE status ${response.statusHexString}",
                        response.status
                    )
                }
            }
        }
        val encapsulatedMessage = encapsulatedMessageBuilder.toByteString()
        val extractedMessage = extractFromDo53(encapsulatedMessage)
        return extractedMessage
    }

    override suspend fun waitForMessage(): ByteArray {
        mutex.withLock {
            check(_state.value == State.CONNECTED) { "Expected state CONNECTED, got ${_state.value}" }
            Logger.i(TAG, "waitForMessage")
        }
        try {
            return incomingMessages.receive().toByteArray()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (_state.value == State.CLOSED) {
                throw MdocTransportClosedException("Transport was closed while waiting for message")
            } else {
                mutex.withLock {
                    failTransport(error)
                }
                throw MdocTransportException("Failed while waiting for message", error)
            }
        }
    }

    override suspend fun close() {
        mutex.withLock {
            if (_state.value == State.FAILED || _state.value == State.CLOSED) {
                return
            }
            Logger.i(TAG, "close")
            incomingMessages.close()
            ioJob?.cancel()
            ioJob = null
            incomingMessages.close()
            if (::tag.isInitialized) {
                tag.close()
            }
            _state.value = State.CLOSED
        }
    }

    private var inError = false

    private fun failTransport(error: Throwable) {
        check(mutex.isLocked) { "failTransport called without holding lock" }
        inError = true
        if (_state.value == State.FAILED || _state.value == State.CLOSED) {
            return
        }
        Logger.w(TAG, "Failing transport with error", error)
        Error().printStackTrace()
        incomingMessages.close(error)
        ioJob?.cancel()
        ioJob = null
        incomingMessages.close()
        _state.value = State.FAILED
    }

}

internal fun extractFromDo53(encapsulatedData: ByteString): ByteString {
    check(encapsulatedData.size >= 2) {
        "DO53 length ${encapsulatedData.size}, expected at least 2"
    }
    with (ByteDataReader(encapsulatedData)) {
        val tag = getUInt8()
        check(tag == 0x53.toUByte()) { "DO53 first byte is $tag, expected 0x53" }

        val length = getUInt8()
        check(length <= 0x83u) {
            "DO53 first byte of length is $length"
        }
        val newLength: Int = when (length.toUInt()) {
            0x80u -> throw IllegalStateException("DO53 first byte of length is 0x80")
            0x81u -> getUInt8().toInt()
            0x82u -> getUInt16().toInt()
            0x83u -> (getUInt8() * 0x10000u + getUInt16()).toInt()
            else -> length.toInt()
        }
        if (newLength == 0) {
            return ByteString()
        }
        if (newLength != numBytesRemaining()) {
            throw IllegalStateException("Malformed BER-TLV encoding. " +
                    "Data length expected: $newLength, actual: ${numBytesRemaining()}")
        }
        return getByteString(newLength)
    }
}

internal fun encapsulateInDo53(data: ByteString): ByteString {
    return buildByteString {
        appendUInt8(0x53)
        if (data.size < 0x80) {
            appendUInt8(data.size)
        } else if (data.size < 0x100) {
            appendUInt8(0x81)
            appendUInt8(data.size)
        } else if (data.size < 0x10000) {
            appendUInt8(0x82)
            appendUInt16(data.size)
        } else if (data.size < 0x1000000) {
            appendUInt8(0x83)
            appendUInt8(data.size / 0x10000)
            appendUInt16(data.size.and(0xFFFF))
        } else {
            throw IllegalStateException("Data length cannot be bigger than 0x1000000")
        }
        appendByteString(data)
    }
}
