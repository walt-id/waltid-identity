@file:OptIn(
    kotlinx.serialization.ExperimentalSerializationApi::class,
    kotlin.ExperimentalUnsignedTypes::class,
)

package id.walt.mdoc.proximity.mobile

import id.walt.cose.coseCompliantCbor
import id.walt.mdoc.encoding.ExactCbor
import id.walt.mdoc.objects.engagement.DeviceEngagement
import id.walt.mdoc.objects.engagement.DeviceRetrievalMethod
import id.walt.mdoc.objects.engagement.DeviceRetrievalMethodCodec
import id.walt.mdoc.proximity.ImmutableBytes
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.cbor.CborArray
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.cbor.CborInteger
import kotlinx.serialization.cbor.CborMap
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray

/**
 * Holder SELECT-response maximum command-data size for the provisional NFCv2 application.
 *
 * @property value Number of command-data bytes, in the inclusive range `1..65536`.
 */
public data class NfcV2MaximumCommandDataLength(public val value: Int) {
    init {
        require(value in 1..NfcCommandApdu.MAX_RESPONSE_DATA_LENGTH) {
            "NFCv2 maximum command data length must be in 1..65536"
        }
    }
}

/** Validated exact NFCv2 Handover Request received from the reader. */
internal class NfcV2HandoverRequest(
    exactBytes: ImmutableBytes,
    availableMethods: List<DeviceRetrievalMethod>,
) {
    public val exactBytes: ImmutableBytes = exactBytes
    public val availableMethods: List<DeviceRetrievalMethod> = availableMethods.toList()

    init {
        require(availableMethods.isNotEmpty()) { "NFCv2 Handover Request must offer retrieval methods" }
        require(availableMethods.any { it is DeviceRetrievalMethod.NfcV2 }) {
            "NFCv2 Handover Request must offer NFCv2 retrieval"
        }
        require(availableMethods.distinctBy { it.type to it.version }.size == availableMethods.size) {
            "NFCv2 Handover Request contains a duplicate retrieval method"
        }
    }
}

/** Holder selection used to construct the exact NFCv2 Handover Select. */
internal data class NfcV2HandoverSelection(
    public val selectedMethod: DeviceRetrievalMethod,
    public val deviceEngagement: ExactCbor<DeviceEngagement>,
) {
    init {
        require(deviceEngagement.value.deviceRetrievalMethods == listOf(selectedMethod)) {
            "NFCv2 Device Engagement must contain exactly the selected retrieval method"
        }
    }
}

/** Exact completed provisional NFCv2 handover with its only legal continuation. */
internal sealed interface NfcV2Handover {
    public val handoverSelect: ImmutableBytes
    public val handoverRequest: ImmutableBytes
    public val deviceEngagement: ExactCbor<DeviceEngagement>
    public val selectedMethod: DeviceRetrievalMethod

    /** SessionEstablishment and subsequent messages continue on the selected NFCv2 APDU channel. */
    public class SameChannel internal constructor(
        override val handoverSelect: ImmutableBytes,
        override val handoverRequest: ImmutableBytes,
        override val deviceEngagement: ExactCbor<DeviceEngagement>,
        override val selectedMethod: DeviceRetrievalMethod.NfcV2,
    ) : NfcV2Handover {
        init {
            require(deviceEngagement.value.deviceRetrievalMethods == listOf(selectedMethod)) {
                "Same-channel NFCv2 handover must select only NFCv2 retrieval"
            }
        }
    }

    /** NFCv2 engagement completed and payloads continue over NFC plus one prepared alternate bearer. */
    public class AlternateBearer internal constructor(
        override val handoverSelect: ImmutableBytes,
        override val handoverRequest: ImmutableBytes,
        override val deviceEngagement: ExactCbor<DeviceEngagement>,
        override val selectedMethod: DeviceRetrievalMethod,
    ) : NfcV2Handover {
        init {
            require(selectedMethod !is DeviceRetrievalMethod.NfcV2) {
                "NFCv2 retrieval must use the same-channel handover variant"
            }
            require(deviceEngagement.value.deviceRetrievalMethods == listOf(selectedMethod)) {
                "Alternate-bearer NFCv2 handover must select exactly its alternate retrieval method"
            }
        }
    }
}

internal enum class NfcV2State {
    AWAITING_APPLICATION_SELECTION,
    AWAITING_HANDOVER_REQUEST,
    SENDING_HANDOVER_RESPONSE,
    AWAITING_PAYLOAD,
    AWAITING_WALLET_RESPONSE,
    SENDING_PAYLOAD_RESPONSE,
    DEACTIVATED,
}

internal sealed interface NfcV2ApduResult {
    public data class Response(public val encoded: ImmutableBytes) : NfcV2ApduResult
    public data class Request(public val identifier: ULong, public val sessionMessage: ImmutableBytes) : NfcV2ApduResult
}

/** Common provisional NFCv2 handover and same-channel APDU state machine. */
internal class NfcV2ApduProcessor(
    public val maximumCommandDataLength: NfcV2MaximumCommandDataLength,
    private val maximumSessionMessageBytes: Int,
    private val maximumHandoverBytes: Int = maximumSessionMessageBytes,
    private val select: suspend (NfcV2HandoverRequest) -> NfcV2HandoverSelection,
    private val onHandover: suspend (NfcV2Handover) -> Unit = {},
    private val onFailure: (Throwable) -> Unit = {},
) {
    public var state: NfcV2State = NfcV2State.AWAITING_APPLICATION_SELECTION
        private set

    private val exchange = NfcApduMessageExchange(
        maximumCommandDataLength.value,
        NfcCommandApdu.MAX_RESPONSE_DATA_LENGTH,
        maxOf(maximumSessionMessageBytes, maximumHandoverBytes),
    )
    private var afterOutgoing: NfcV2State? = null
    private var pendingIdentifier: ULong? = null
    private var nextIdentifier: ULong = 0u

    init {
        require(maximumSessionMessageBytes > 0)
        require(maximumHandoverBytes > 0)
    }

    public suspend fun process(encodedCommand: ByteArray): NfcV2ApduResult {
        if (state == NfcV2State.DEACTIVATED) return response(NfcStatusWord.CONDITIONS_NOT_SATISFIED)
        val command = try {
            NfcCommandApdu.decode(encodedCommand)
        } catch (_: IllegalArgumentException) {
            return response(NfcStatusWord.WRONG_LENGTH)
        }
        return try {
            when (command.instruction.toInt()) {
                SELECT_INSTRUCTION -> selectApplication(command)
                ENVELOPE_INSTRUCTION -> envelope(command)
                GET_RESPONSE_INSTRUCTION -> getResponse(command)
                else -> response(NfcStatusWord.INSTRUCTION_NOT_SUPPORTED)
            }
        } catch (cancelled: CancellationException) {
            deactivate()
            throw cancelled
        } catch (failure: IllegalArgumentException) {
            fail(NfcStatusWord.WRONG_DATA).also { onFailure(failure) }
        } catch (failure: IllegalStateException) {
            fail(NfcStatusWord.CONDITIONS_NOT_SATISFIED).also { onFailure(failure) }
        } catch (failure: Exception) {
            fail(NfcStatusWord.UNKNOWN_ERROR).also { onFailure(failure) }
        }
    }

    public fun completeResponse(identifier: ULong, sessionMessage: ByteArray): ImmutableBytes {
        check(state == NfcV2State.AWAITING_WALLET_RESPONSE && pendingIdentifier == identifier) {
            "NFCv2 response does not own the current pending request"
        }
        require(sessionMessage.size <= maximumSessionMessageBytes) {
            "NFCv2 session response exceeds the configured limit"
        }
        val first = exchange.stageResponse(sessionMessage)
        pendingIdentifier = null
        afterOutgoing = NfcV2State.AWAITING_PAYLOAD
        state = if (exchange.hasOutgoingData) NfcV2State.SENDING_PAYLOAD_RESPONSE else NfcV2State.AWAITING_PAYLOAD
        return ImmutableBytes.of(first.encode())
    }

    internal fun cancelPendingResponse(identifier: ULong) {
        if (state != NfcV2State.AWAITING_WALLET_RESPONSE || pendingIdentifier != identifier) return
        pendingIdentifier = null
        afterOutgoing = null
        exchange.reset()
        state = NfcV2State.DEACTIVATED
    }

    public fun deactivate() {
        pendingIdentifier = null
        afterOutgoing = null
        exchange.reset()
        state = NfcV2State.DEACTIVATED
    }

    private fun selectApplication(command: NfcCommandApdu): NfcV2ApduResult {
        if (state != NfcV2State.AWAITING_APPLICATION_SELECTION) return response(NfcStatusWord.CONDITIONS_NOT_SATISFIED)
        if (command.cla != 0.toUByte()) return response(NfcStatusWord.CLASS_NOT_SUPPORTED)
        if (command.parameter1.toInt() != SELECT_BY_NAME || command.parameter2 != 0.toUByte()) {
            return response(NfcStatusWord.INCORRECT_PARAMETERS)
        }
        if (command.expectedResponseDataLength != null) return response(NfcStatusWord.WRONG_LENGTH)
        if (!command.data.contentEquals(MdocNfcAid.NFC_V2.copy())) return response(NfcStatusWord.FILE_NOT_FOUND)
        val selectPayload = coseCompliantCbor.encodeToByteArray(
            CborElement.serializer(),
            CborMap(mapOf(CborInteger(0) to CborInteger(maximumCommandDataLength.value.toULong()))),
        )
        state = NfcV2State.AWAITING_HANDOVER_REQUEST
        return NfcV2ApduResult.Response(
            ImmutableBytes.of(NfcResponseApdu(ImmutableBytes.of(selectPayload), NfcStatusWord.SUCCESS).encode())
        )
    }

    private suspend fun envelope(command: NfcCommandApdu): NfcV2ApduResult {
        if (state !in setOf(NfcV2State.AWAITING_HANDOVER_REQUEST, NfcV2State.AWAITING_PAYLOAD)) {
            return response(NfcStatusWord.CONDITIONS_NOT_SATISFIED)
        }
        if (command.cla.toInt() !in setOf(0x00, 0x10)) return response(NfcStatusWord.CLASS_NOT_SUPPORTED)
        if (command.parameter1 != 0.toUByte() || command.parameter2 != 0.toUByte()) {
            return response(NfcStatusWord.INCORRECT_PARAMETERS)
        }
        return when (val incoming = exchange.accept(command)) {
            is NfcApduMessageExchange.IncomingResult.Continue ->
                NfcV2ApduResult.Response(ImmutableBytes.of(incoming.response.encode()))
            is NfcApduMessageExchange.IncomingResult.Message -> when (state) {
                NfcV2State.AWAITING_HANDOVER_REQUEST -> completeHandover(incoming.bytes)
                NfcV2State.AWAITING_PAYLOAD -> {
                    require(incoming.bytes.size <= maximumSessionMessageBytes) {
                        "NFCv2 session request exceeds the configured limit"
                    }
                    val identifier = nextIdentifier
                    check(nextIdentifier != ULong.MAX_VALUE) { "NFCv2 pending-response identifier is exhausted" }
                    nextIdentifier++
                    pendingIdentifier = identifier
                    state = NfcV2State.AWAITING_WALLET_RESPONSE
                    NfcV2ApduResult.Request(identifier, ImmutableBytes.of(incoming.bytes))
                }
                else -> error("Unexpected NFCv2 ENVELOPE state")
            }
        }
    }

    private suspend fun completeHandover(exactRequest: ByteArray): NfcV2ApduResult {
        val request = parseHandoverRequest(exactRequest)
        val nfcV2Method = request.availableMethods.filterIsInstance<DeviceRetrievalMethod.NfcV2>().single()
        exchange.setMaximumResponseDataLength(nfcV2Method.maximumResponseDataLength.toInt())
        val selection = select(request)
        require(request.availableMethods.any { offered -> selectionWasOffered(selection.selectedMethod, offered) }) {
            "NFCv2 holder selected a retrieval method not offered by the reader"
        }
        val deviceEngagementElement = coseCompliantCbor.decodeFromByteArray<CborElement>(
            selection.deviceEngagement.encodedCopy(),
        )
        val exactSelect = coseCompliantCbor.encodeToByteArray(
            CborElement.serializer(),
            CborMap(mapOf(CborInteger(0) to deviceEngagementElement)),
        )
        require(exactSelect.size <= maximumHandoverBytes) {
            "NFCv2 Handover Select exceeds the configured limit"
        }
        val completedHandover = if (selection.selectedMethod is DeviceRetrievalMethod.NfcV2) {
            NfcV2Handover.SameChannel(
                ImmutableBytes.of(exactSelect),
                request.exactBytes,
                selection.deviceEngagement,
                selection.selectedMethod,
            )
        } else {
            NfcV2Handover.AlternateBearer(
                ImmutableBytes.of(exactSelect),
                request.exactBytes,
                selection.deviceEngagement,
                selection.selectedMethod,
            )
        }
        val first = exchange.stageResponse(exactSelect)
        afterOutgoing = NfcV2State.AWAITING_PAYLOAD
        state = if (exchange.hasOutgoingData) NfcV2State.SENDING_HANDOVER_RESPONSE else checkNotNull(afterOutgoing)
        if (!exchange.hasOutgoingData) afterOutgoing = null
        onHandover(completedHandover)
        return NfcV2ApduResult.Response(ImmutableBytes.of(first.encode()))
    }

    private fun getResponse(command: NfcCommandApdu): NfcV2ApduResult {
        if (state !in setOf(NfcV2State.SENDING_HANDOVER_RESPONSE, NfcV2State.SENDING_PAYLOAD_RESPONSE)) {
            return response(NfcStatusWord.CONDITIONS_NOT_SATISFIED)
        }
        if (command.cla != 0.toUByte()) return response(NfcStatusWord.CLASS_NOT_SUPPORTED)
        if (command.parameter1 != 0.toUByte() || command.parameter2 != 0.toUByte() || command.data.size != 0) {
            return response(NfcStatusWord.INCORRECT_PARAMETERS)
        }
        val value = exchange.getResponse(command)
        if (!exchange.hasOutgoingData) {
            state = checkNotNull(afterOutgoing)
            afterOutgoing = null
        }
        return NfcV2ApduResult.Response(ImmutableBytes.of(value.encode()))
    }

    private fun parseHandoverRequest(encoded: ByteArray): NfcV2HandoverRequest {
        require(encoded.size <= maximumHandoverBytes) { "NFCv2 Handover Request exceeds the configured limit" }
        val request = coseCompliantCbor.decodeFromByteArray<CborMap>(encoded)
        val readerEngagement = request[CborInteger(0)] as? CborMap
            ?: throw IllegalArgumentException("NFCv2 Handover Request is missing Reader Engagement")
        val methods = readerEngagement[CborInteger(2)] as? CborArray
            ?: throw IllegalArgumentException("NFCv2 Reader Engagement is missing retrieval methods")
        val decoded = methods.map { element ->
            DeviceRetrievalMethodCodec.decodeReaderEngagement(
                coseCompliantCbor.encodeToByteArray(CborElement.serializer(), element),
            )
        }
        return NfcV2HandoverRequest(ImmutableBytes.of(encoded), decoded)
    }

    private fun fail(status: UShort): NfcV2ApduResult {
        pendingIdentifier = null
        afterOutgoing = null
        exchange.reset()
        state = NfcV2State.DEACTIVATED
        return response(status)
    }

    private fun response(status: UShort): NfcV2ApduResult.Response = NfcV2ApduResult.Response(
        ImmutableBytes.of(NfcResponseApdu(statusWord = status).encode()),
    )

    private fun selectionWasOffered(
        selected: DeviceRetrievalMethod,
        offered: DeviceRetrievalMethod,
    ): Boolean {
        if (selected == offered) return true
        if (selected !is DeviceRetrievalMethod.Ble || offered !is DeviceRetrievalMethod.Ble) return false
        if (selected.extensions != offered.extensions) return false
        return when {
            selected.centralMode != null && selected.peripheralMode == null ->
                selected.centralMode == offered.centralMode &&
                    selected.peripheralEndpoint == offered.peripheralEndpoint
            selected.peripheralMode != null && selected.centralMode == null ->
                selected.peripheralMode == offered.peripheralMode
            else -> false
        }
    }

    private companion object {
        const val SELECT_INSTRUCTION: Int = 0xa4
        const val ENVELOPE_INSTRUCTION: Int = 0xc3
        const val GET_RESPONSE_INSTRUCTION: Int = 0xc0
        const val SELECT_BY_NAME: Int = 0x04
    }
}
