package id.walt.mdoc.proximity.mobile

import id.walt.mdoc.proximity.ImmutableBytes
import kotlinx.coroutines.CancellationException

/** Exact conventional NFC Connection Handover result used by the session transcript. */
internal sealed interface NfcConnectionHandover {
    public val handoverSelect: ImmutableBytes

    public data class Static(
        override val handoverSelect: ImmutableBytes,
    ) : NfcConnectionHandover

    public data class Negotiated(
        override val handoverSelect: ImmutableBytes,
        public val handoverRequest: ImmutableBytes,
    ) : NfcConnectionHandover
}

/** Conventional NFC engagement mode. The negotiated callback receives exact Hr and returns exact Hs. */
internal sealed interface NfcEngagementConfiguration {
    public data class Static(public val handoverSelect: ImmutableBytes) : NfcEngagementConfiguration

    public class Negotiated(
        public val select: suspend (handoverRequest: ImmutableBytes) -> ImmutableBytes,
    ) : NfcEngagementConfiguration
}

/** NFC Forum Connection Handover 1.5 envelope validation. */
internal object NfcHandoverCodec {
    public const val VERSION_1_5: UByte = 0x15u
    private val HANDOVER_SELECT_TYPE: ByteArray = "Hs".encodeToByteArray()
    private val HANDOVER_REQUEST_TYPE: ByteArray = "Hr".encodeToByteArray()
    private val ALTERNATIVE_CARRIER_TYPE: ByteArray = "ac".encodeToByteArray()

    public fun validateSelect(encoded: ByteArray, limits: NdefLimits = NdefLimits()): NfcHandoverMessage =
        validate(encoded, HANDOVER_SELECT_TYPE, "Handover Select", limits)

    /**
     * Validates a Handover Request while retaining non-carrier embedded records such as `cr`.
     *
     * The DIS D.3.2 vector contains Collision Resolution, but deployed readers may omit it when the
     * holder has the fixed Handover Selector role. Both forms receive the same carrier-reference
     * validation.
     */
    public fun validateRequest(encoded: ByteArray, limits: NdefLimits = NdefLimits()): NfcHandoverMessage =
        validate(encoded, HANDOVER_REQUEST_TYPE, "Handover Request", limits)

    /** Produces a deterministic Handover Select with exact carrier and auxiliary references. */
    public fun encodeSelect(
        carriers: List<NfcHandoverCarrier>,
        limits: NdefLimits = NdefLimits(),
    ): ByteArray = encode(HANDOVER_SELECT_TYPE, carriers, limits)

    /** Produces a deterministic Handover Request with exact carrier and auxiliary references. */
    public fun encodeRequest(
        carriers: List<NfcHandoverCarrier>,
        limits: NdefLimits = NdefLimits(),
    ): ByteArray = encode(HANDOVER_REQUEST_TYPE, carriers, limits)

    private fun encode(
        type: ByteArray,
        carriers: List<NfcHandoverCarrier>,
        limits: NdefLimits,
    ): ByteArray {
        require(carriers.isNotEmpty()) { "Connection Handover requires at least one carrier" }
        val carrierReferences = carriers.map { it.carrierRecord.identifier }
        require(carrierReferences.distinct().size == carrierReferences.size) {
            "Connection Handover carrier references must be unique"
        }
        val auxiliaryReferences = carriers.flatMap { carrier ->
            carrier.auxiliaryRecords.map { it.identifier }
        }
        require(carrierReferences.none { it in auxiliaryReferences }) {
            "Connection Handover carrier and auxiliary references must be distinct"
        }
        val referencedRecords = linkedMapOf<ImmutableBytes, NdefRecord>()
        carriers.forEach { carrier ->
            (carrier.auxiliaryRecords + carrier.carrierRecord).forEach { record ->
                val previous = referencedRecords.put(record.identifier, record)
                require(previous == null || previous == record) {
                    "Connection Handover record identifiers must resolve unambiguously"
                }
            }
        }
        val embedded = NdefMessage(carriers.map { alternativeCarrierRecord(it) }).encode(limits)
        val handover = NdefRecord(
            typeNameFormat = NdefTypeNameFormat.WELL_KNOWN,
            type = ImmutableBytes.of(type),
            payload = ImmutableBytes.of(byteArrayOf(VERSION_1_5.toByte()) + embedded),
        )
        return NdefMessage(listOf(handover) + referencedRecords.values).encode(limits)
    }

    private fun validate(
        encoded: ByteArray,
        expectedType: ByteArray,
        name: String,
        limits: NdefLimits,
    ): NfcHandoverMessage {
        val message = NdefMessage.decode(encoded, limits)
        val first = message.records.first()
        require(first.typeNameFormat == NdefTypeNameFormat.WELL_KNOWN && first.type.contentEquals(expectedType)) {
            "$name must be the first NDEF record"
        }
        val payload = first.payload.copy()
        require(payload.isNotEmpty() && payload[0].toUByte() == VERSION_1_5) {
            "$name must use Connection Handover version 1.5"
        }
        require(payload.size > 1) { "$name must contain its embedded NDEF message" }
        val embedded = NdefMessage.decode(payload.copyOfRange(1, payload.size), limits)
        val indexedRecords = linkedMapOf<ImmutableBytes, NdefRecord>()
        message.records.drop(1).forEach { record ->
            if (record.identifier.size == 0) return@forEach
            require(indexedRecords.put(record.identifier, record) == null) {
                "$name contains duplicate record identifiers"
            }
        }
        val alternatives = embedded.records.mapNotNull { record ->
            if (!record.isAlternativeCarrier()) return@mapNotNull null
            parseAlternativeCarrier(record)
        }
        require(alternatives.isNotEmpty()) { "$name must contain at least one Alternative Carrier record" }
        require(alternatives.map { it.carrierDataReference }.distinct().size == alternatives.size) {
            "$name contains duplicate carrier data references"
        }
        val carrierReferences = alternatives.map { it.carrierDataReference }
        require(
            carrierReferences.none { carrierReference ->
                alternatives.any { carrierReference in it.auxiliaryDataReferences }
            },
        ) {
            "$name uses a carrier data reference as an auxiliary data reference"
        }
        val resolved = alternatives.map { alternative ->
            val carrier = indexedRecords[alternative.carrierDataReference]
                ?: throw IllegalArgumentException("$name contains a dangling carrier data reference")
            val auxiliaries = alternative.auxiliaryDataReferences.map { reference ->
                indexedRecords[reference]
                    ?: throw IllegalArgumentException("$name contains a dangling auxiliary data reference")
            }
            NfcResolvedCarrier(alternative, carrier, auxiliaries)
        }
        return NfcHandoverMessage(message, embedded, resolved)
    }

    private fun alternativeCarrierRecord(carrier: NfcHandoverCarrier): NdefRecord {
        val output = MutableByteBuffer()
        output.add(carrier.powerState.code.toInt())
        output.addLengthPrefixed(carrier.carrierRecord.identifier)
        output.add(carrier.auxiliaryRecords.size)
        carrier.auxiliaryRecords.forEach { output.addLengthPrefixed(it.identifier) }
        return NdefRecord(
            typeNameFormat = NdefTypeNameFormat.WELL_KNOWN,
            type = ImmutableBytes.of(ALTERNATIVE_CARRIER_TYPE),
            payload = ImmutableBytes.of(output.toByteArray()),
        )
    }

    private fun parseAlternativeCarrier(record: NdefRecord): NfcAlternativeCarrier {
        val cursor = HandoverCursor(record.payload.copy())
        val powerState = NfcCarrierPowerState.fromCode(cursor.readByte())
        val carrierReference = cursor.readLengthPrefixed("carrier data reference")
        val auxiliaryCount = cursor.readByte()
        val auxiliaries = List(auxiliaryCount) { cursor.readLengthPrefixed("auxiliary data reference") }
        require(cursor.exhausted) { "Alternative Carrier record contains trailing bytes" }
        return NfcAlternativeCarrier(powerState, carrierReference, auxiliaries)
    }

    private fun NdefRecord.isAlternativeCarrier(): Boolean =
        typeNameFormat == NdefTypeNameFormat.WELL_KNOWN && type.contentEquals(ALTERNATIVE_CARRIER_TYPE)

    private class MutableByteBuffer {
        private val bytes = mutableListOf<Byte>()
        fun add(value: Int) {
            require(value in 0..UByte.MAX_VALUE.toInt())
            bytes += value.toByte()
        }
        fun addLengthPrefixed(value: ImmutableBytes) {
            require(value.size in 1..UByte.MAX_VALUE.toInt()) { "NDEF record reference must contain 1..255 bytes" }
            add(value.size)
            value.copy().forEach(bytes::add)
        }
        fun toByteArray(): ByteArray = bytes.toByteArray()
    }

    private class HandoverCursor(private val bytes: ByteArray) {
        private var offset = 0
        val exhausted: Boolean get() = offset == bytes.size
        fun readByte(): Int {
            require(offset < bytes.size) { "Truncated Alternative Carrier record" }
            return bytes[offset++].toInt() and 0xff
        }
        fun readLengthPrefixed(field: String): ImmutableBytes {
            val length = readByte()
            require(length > 0) { "Alternative Carrier $field is empty" }
            require(length <= bytes.size - offset) { "Truncated Alternative Carrier $field" }
            return ImmutableBytes.of(bytes.copyOfRange(offset, offset + length)).also { offset += length }
        }
    }
}

/** NFC Forum Carrier Power State advertised by an Alternative Carrier record. */
internal enum class NfcCarrierPowerState(val code: UByte) {
    INACTIVE(0u),
    ACTIVE(1u),
    ACTIVATING(2u),
    UNKNOWN(3u),
    ;

    public companion object {
        internal fun fromCode(code: Int): NfcCarrierPowerState = entries.firstOrNull { it.code.toInt() == code }
            ?: throw IllegalArgumentException("Alternative Carrier power state is reserved")
    }
}

/** Validated Alternative Carrier references from an embedded Hs/Hr NDEF message. */
internal data class NfcAlternativeCarrier(
    public val powerState: NfcCarrierPowerState,
    public val carrierDataReference: ImmutableBytes,
    public val auxiliaryDataReferences: List<ImmutableBytes>,
) {
    init {
        require(carrierDataReference.size in 1..UByte.MAX_VALUE.toInt())
        require(auxiliaryDataReferences.all { it.size in 1..UByte.MAX_VALUE.toInt() })
        require(auxiliaryDataReferences.distinct().size == auxiliaryDataReferences.size) {
            "Alternative Carrier auxiliary references must be unique"
        }
        require(carrierDataReference !in auxiliaryDataReferences) {
            "A carrier data reference cannot also be an auxiliary reference"
        }
    }
}

/** One carrier and its exact auxiliary records used to construct Hs/Hr. */
internal data class NfcHandoverCarrier(
    public val powerState: NfcCarrierPowerState = NfcCarrierPowerState.ACTIVE,
    public val carrierRecord: NdefRecord,
    public val auxiliaryRecords: List<NdefRecord> = emptyList(),
) {
    init {
        require(carrierRecord.identifier.size in 1..UByte.MAX_VALUE.toInt()) {
            "A handover carrier record requires an identifier"
        }
        require(auxiliaryRecords.all { it.identifier.size in 1..UByte.MAX_VALUE.toInt() }) {
            "Every auxiliary handover record requires an identifier"
        }
        require(auxiliaryRecords.map { it.identifier }.distinct().size == auxiliaryRecords.size) {
            "Auxiliary handover record identifiers must be unique"
        }
        require(carrierRecord.identifier !in auxiliaryRecords.map { it.identifier }) {
            "Carrier and auxiliary record identifiers must be distinct"
        }
    }
}

/** A validated carrier with all Alternative Carrier references resolved exactly once. */
internal data class NfcResolvedCarrier(
    public val alternative: NfcAlternativeCarrier,
    public val carrierRecord: NdefRecord,
    public val auxiliaryRecords: List<NdefRecord>,
)

/** Parsed conventional Connection Handover message retaining both outer and embedded NDEF. */
internal data class NfcHandoverMessage(
    public val outerMessage: NdefMessage,
    public val embeddedMessage: NdefMessage,
    public val carriers: List<NfcResolvedCarrier>,
)

/** NFC Forum TNEP records used by conventional Negotiated Handover. */
internal object NfcTnepCodec {
    public const val CONNECTION_HANDOVER_SERVICE: String = "urn:nfc:sn:handover"
    private const val VERSION_1_0: Int = 0x10
    private val SERVICE_PARAMETER_TYPE: ByteArray = "Tp".encodeToByteArray()
    private val SERVICE_SELECT_TYPE: ByteArray = "Ts".encodeToByteArray()
    private val STATUS_TYPE: ByteArray = "Te".encodeToByteArray()

    public data class ServiceParameters(
        public val waitingTimeExponent: UByte,
        public val maximumWaitExtensions: UByte,
        public val maximumNdefSize: UShort,
    ) {
        init {
            require(waitingTimeExponent < 64u) { "TNEP waiting time exponent must be below 64" }
            require(maximumWaitExtensions < 16u) { "TNEP maximum wait extensions must be below 16" }
            require(maximumNdefSize > 0u) { "TNEP maximum NDEF size must be positive" }
        }
    }

    public fun serviceParameter(parameters: ServiceParameters): NdefRecord {
        val service = CONNECTION_HANDOVER_SERVICE.encodeToByteArray()
        val maximum = parameters.maximumNdefSize.toInt()
        val payload = byteArrayOf(VERSION_1_0.toByte(), service.size.toByte()) + service + byteArrayOf(
            0,
            parameters.waitingTimeExponent.toByte(),
            parameters.maximumWaitExtensions.toByte(),
            (maximum ushr 8).toByte(),
            maximum.toByte(),
        )
        return wellKnown(SERVICE_PARAMETER_TYPE, payload)
    }

    public fun parseServiceParameter(record: NdefRecord): ServiceParameters? {
        if (!record.isWellKnown(SERVICE_PARAMETER_TYPE)) return null
        val payload = record.payload.copy()
        require(payload.size >= 7 && payload[0].toInt() and 0xff == VERSION_1_0) {
            "TNEP Service Parameter version or length is invalid"
        }
        val serviceLength = payload[1].toInt() and 0xff
        require(serviceLength > 0 && payload.size == serviceLength + 7) {
            "TNEP Service Parameter length is invalid"
        }
        val service = payload.copyOfRange(2, 2 + serviceLength).decodeToString(throwOnInvalidSequence = true)
        require(service == CONNECTION_HANDOVER_SERVICE) { "Unexpected TNEP service name" }
        val tail = 2 + serviceLength
        require(payload[tail] == 0.toByte()) { "Only TNEP single-response communication mode is supported" }
        return ServiceParameters(
            waitingTimeExponent = payload[tail + 1].toUByte(),
            maximumWaitExtensions = payload[tail + 2].toUByte(),
            maximumNdefSize = (((payload[tail + 3].toInt() and 0xff) shl 8) or
                (payload[tail + 4].toInt() and 0xff)).toUShort(),
        )
    }

    public fun parseServiceSelect(record: NdefRecord): String? {
        if (!record.isWellKnown(SERVICE_SELECT_TYPE)) return null
        val payload = record.payload.copy()
        require(payload.isNotEmpty()) { "TNEP Service Select payload is empty" }
        val length = payload[0].toInt() and 0xff
        require(payload.size == length + 1) { "TNEP Service Select length is invalid" }
        return payload.copyOfRange(1, payload.size).decodeToString(throwOnInvalidSequence = true)
    }

    public fun status(status: UByte = 0u): NdefRecord = wellKnown(STATUS_TYPE, byteArrayOf(status.toByte()))

    public fun parseStatus(record: NdefRecord): UByte? {
        if (!record.isWellKnown(STATUS_TYPE)) return null
        val payload = record.payload.copy()
        require(payload.size == 1) { "TNEP Status must contain exactly one byte" }
        return payload.single().toUByte()
    }

    private fun wellKnown(type: ByteArray, payload: ByteArray): NdefRecord = NdefRecord(
        NdefTypeNameFormat.WELL_KNOWN,
        ImmutableBytes.of(type),
        payload = ImmutableBytes.of(payload),
    )

    private fun NdefRecord.isWellKnown(type: ByteArray): Boolean =
        typeNameFormat == NdefTypeNameFormat.WELL_KNOWN && this.type.contentEquals(type)
}

/** A bounded NFC Forum Type 4 Tag state machine for Static and Negotiated Handover. */
internal class NfcEngagementApduProcessor(
    private val configuration: NfcEngagementConfiguration,
    private val limits: NdefLimits = NdefLimits(),
    private val onHandover: suspend (NfcConnectionHandover) -> Unit = {},
    private val onFailure: (Throwable) -> Unit = {},
) {
    private enum class SelectedFile { CAPABILITY_CONTAINER, NDEF }
    private enum class NegotiatedPhase { EXPECT_SERVICE_SELECT, EXPECT_HANDOVER_REQUEST, COMPLETE }

    private var applicationSelected: Boolean = false
    private var selectedFile: SelectedFile? = null
    private var selectedFileBytes: ByteArray = byteArrayOf()
    private var readCoverage: BooleanArray = booleanArrayOf()
    private var staticHandoverReported: Boolean = false
    private var negotiatedPhase: NegotiatedPhase? = null
    private var writeBuffer: MutableList<Byte>? = null

    init {
        if (configuration is NfcEngagementConfiguration.Static) {
            NfcHandoverCodec.validateSelect(configuration.handoverSelect.copy(), limits)
        }
    }

    /** Processes one APDU. Protocol errors become status words; cancellation still propagates. */
    public suspend fun process(encodedCommand: ByteArray): ByteArray {
        val command = try {
            NfcCommandApdu.decode(encodedCommand)
        } catch (_: IllegalArgumentException) {
            return NfcResponseApdu(statusWord = NfcStatusWord.WRONG_LENGTH).encode()
        }
        if (command.cla != 0.toUByte() || command.isChained) {
            return NfcResponseApdu(statusWord = NfcStatusWord.CLASS_NOT_SUPPORTED).encode()
        }
        return try {
            when (command.instruction.toInt()) {
                SELECT_INSTRUCTION -> select(command)
                READ_BINARY_INSTRUCTION -> readBinary(command)
                UPDATE_BINARY_INSTRUCTION -> updateBinary(command)
                else -> response(NfcStatusWord.INSTRUCTION_NOT_SUPPORTED)
            }
        } catch (cancelled: CancellationException) {
            deactivate()
            throw cancelled
        } catch (failure: IllegalArgumentException) {
            deactivate()
            onFailure(failure)
            response(NfcStatusWord.WRONG_DATA)
        } catch (failure: IllegalStateException) {
            deactivate()
            onFailure(failure)
            response(NfcStatusWord.CONDITIONS_NOT_SATISFIED)
        } catch (failure: Exception) {
            deactivate()
            onFailure(failure)
            response(NfcStatusWord.UNKNOWN_ERROR)
        }
    }

    /** Invalidates all per-field state. A new field interaction starts from application selection. */
    public fun deactivate() {
        applicationSelected = false
        selectedFile = null
        selectedFileBytes = byteArrayOf()
        readCoverage = booleanArrayOf()
        staticHandoverReported = false
        negotiatedPhase = null
        writeBuffer = null
    }

    private suspend fun select(command: NfcCommandApdu): ByteArray {
        return when (command.parameter1.toInt()) {
            SELECT_BY_NAME -> {
                if (command.parameter2 != 0.toUByte()) return response(NfcStatusWord.INCORRECT_PARAMETERS)
                if (command.expectedResponseDataLength != null) return response(NfcStatusWord.WRONG_LENGTH)
                if (!command.data.contentEquals(MdocNfcAid.NDEF_APPLICATION.copy())) return response(NfcStatusWord.FILE_NOT_FOUND)
                resetApplicationTransaction()
                applicationSelected = true
                response(NfcStatusWord.SUCCESS)
            }
            SELECT_BY_FILE_ID -> {
                if (command.parameter2.toInt() != SELECT_FIRST_OR_ONLY) {
                    return response(NfcStatusWord.INCORRECT_PARAMETERS)
                }
                if (!applicationSelected) return response(NfcStatusWord.CONDITIONS_NOT_SATISFIED)
                val identifier = command.data.copy()
                if (identifier.size != 2) return response(NfcStatusWord.WRONG_LENGTH)
                when (unsignedShort(identifier, 0)) {
                    CAPABILITY_CONTAINER_FILE_ID -> stage(SelectedFile.CAPABILITY_CONTAINER, capabilityContainer())
                    NDEF_FILE_ID -> stage(SelectedFile.NDEF, initialNdefFile())
                    else -> return response(NfcStatusWord.FILE_NOT_FOUND)
                }
                response(NfcStatusWord.SUCCESS)
            }
            else -> response(NfcStatusWord.INCORRECT_PARAMETERS)
        }
    }

    private suspend fun readBinary(command: NfcCommandApdu): ByteArray {
        val file = selectedFile ?: return response(NfcStatusWord.CONDITIONS_NOT_SATISFIED)
        if (command.data.size != 0) return response(NfcStatusWord.INCORRECT_PARAMETERS)
        val requested = command.expectedResponseDataLength ?: return response(NfcStatusWord.WRONG_LENGTH)
        val offset = command.parameter1.toInt() shl 8 or command.parameter2.toInt()
        if (offset > selectedFileBytes.size) return response(NfcStatusWord.INCORRECT_PARAMETERS)
        val end = minOf(selectedFileBytes.size, offset + requested)
        for (index in offset until end) readCoverage[index] = true
        val bytes = selectedFileBytes.copyOfRange(offset, end)
        if (file == SelectedFile.NDEF && configuration is NfcEngagementConfiguration.Static &&
            !staticHandoverReported && readCoverage.all { it }
        ) {
            staticHandoverReported = true
            onHandover(NfcConnectionHandover.Static(configuration.handoverSelect))
        }
        return NfcResponseApdu(ImmutableBytes.of(bytes), NfcStatusWord.SUCCESS).encode()
    }

    private suspend fun updateBinary(command: NfcCommandApdu): ByteArray {
        if (configuration !is NfcEngagementConfiguration.Negotiated || selectedFile != SelectedFile.NDEF) {
            return response(NfcStatusWord.CONDITIONS_NOT_SATISFIED)
        }
        if (command.expectedResponseDataLength != null) return response(NfcStatusWord.INCORRECT_PARAMETERS)
        val offset = command.parameter1.toInt() shl 8 or command.parameter2.toInt()
        val data = command.data.copy()
        if (offset == 0) {
            if (data.size == 2) {
                val length = unsignedShort(data, 0)
                if (length == 0) {
                    writeBuffer = mutableListOf()
                    return response(NfcStatusWord.SUCCESS)
                }
                val complete = writeBuffer ?: return response(NfcStatusWord.CONDITIONS_NOT_SATISFIED)
                if (complete.size != length) return response(NfcStatusWord.WRONG_LENGTH)
                writeBuffer = null
                return completeWrite(complete.toByteArray())
            }
            if (data.size > 2 && writeBuffer == null) {
                val declared = unsignedShort(data, 0)
                if (declared != data.size - 2) return response(NfcStatusWord.WRONG_LENGTH)
                return completeWrite(data.copyOfRange(2, data.size))
            }
            return response(NfcStatusWord.WRONG_DATA)
        }
        val buffer = writeBuffer ?: return response(NfcStatusWord.CONDITIONS_NOT_SATISFIED)
        if (offset != buffer.size + 2) return response(NfcStatusWord.INCORRECT_PARAMETERS)
        if (data.size > limits.maximumMessageBytes - buffer.size) return response(NfcStatusWord.WRONG_LENGTH)
        data.forEach(buffer::add)
        return response(NfcStatusWord.SUCCESS)
    }

    private suspend fun completeWrite(encodedNdef: ByteArray): ByteArray {
        val message = NdefMessage.decode(encodedNdef, limits)
        val response = when (negotiatedPhase) {
            NegotiatedPhase.EXPECT_SERVICE_SELECT -> {
                require(message.records.size == 1) { "Service Select message must contain one record" }
                require(NfcTnepCodec.parseServiceSelect(message.records.single()) == NfcTnepCodec.CONNECTION_HANDOVER_SERVICE) {
                    "Reader selected an unsupported TNEP service"
                }
                negotiatedPhase = NegotiatedPhase.EXPECT_HANDOVER_REQUEST
                NdefMessage(listOf(NfcTnepCodec.status())).encode(limits)
            }
            NegotiatedPhase.EXPECT_HANDOVER_REQUEST -> {
                NfcHandoverCodec.validateRequest(encodedNdef, limits)
                val exactRequest = ImmutableBytes.of(encodedNdef)
                val negotiated = configuration as? NfcEngagementConfiguration.Negotiated
                    ?: error("Negotiated handover configuration is required")
                val exactSelect = negotiated.select(exactRequest)
                NfcHandoverCodec.validateSelect(exactSelect.copy(), limits)
                negotiatedPhase = NegotiatedPhase.COMPLETE
                onHandover(NfcConnectionHandover.Negotiated(exactSelect, exactRequest))
                exactSelect.copy()
            }
            else -> error("No NDEF write is expected in the current negotiated-handover phase")
        }
        stage(SelectedFile.NDEF, withNlen(response))
        return response(NfcStatusWord.SUCCESS)
    }

    private fun initialNdefFile(): ByteArray = when (configuration) {
        is NfcEngagementConfiguration.Static -> withNlen(configuration.handoverSelect.copy())
        is NfcEngagementConfiguration.Negotiated -> {
            negotiatedPhase = NegotiatedPhase.EXPECT_SERVICE_SELECT
            val record = NfcTnepCodec.serviceParameter(
                NfcTnepCodec.ServiceParameters(0u, 15u, limits.maximumMessageBytes.coerceAtMost(65_535).toUShort()),
            )
            withNlen(NdefMessage(listOf(record)).encode(limits))
        }
    }

    private fun capabilityContainer(): ByteArray {
        val maximum = limits.maximumMessageBytes.coerceAtMost(0x7fff)
        return byteArrayOf(
            0x00, 0x0f, 0x20,
            (maximum ushr 8).toByte(), maximum.toByte(),
            (maximum ushr 8).toByte(), maximum.toByte(),
            0x04, 0x06,
            (NDEF_FILE_ID ushr 8).toByte(), NDEF_FILE_ID.toByte(),
            (maximum ushr 8).toByte(), maximum.toByte(),
            0x00,
            if (configuration is NfcEngagementConfiguration.Negotiated) 0x00 else 0xff.toByte(),
        )
    }

    private fun stage(file: SelectedFile, bytes: ByteArray) {
        selectedFile = file
        selectedFileBytes = bytes
        readCoverage = BooleanArray(bytes.size)
    }

    private fun resetApplicationTransaction() {
        selectedFile = null
        selectedFileBytes = byteArrayOf()
        readCoverage = booleanArrayOf()
        staticHandoverReported = false
        negotiatedPhase = null
        writeBuffer = null
    }

    private fun withNlen(message: ByteArray): ByteArray {
        require(message.size <= 65_535)
        return byteArrayOf((message.size ushr 8).toByte(), message.size.toByte()) + message
    }

    private fun response(status: UShort): ByteArray = NfcResponseApdu(statusWord = status).encode()

    private fun unsignedShort(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 8) or (bytes[offset + 1].toInt() and 0xff)

    private companion object {
        const val SELECT_INSTRUCTION: Int = 0xa4
        const val READ_BINARY_INSTRUCTION: Int = 0xb0
        const val UPDATE_BINARY_INSTRUCTION: Int = 0xd6
        const val SELECT_BY_NAME: Int = 0x04
        const val SELECT_BY_FILE_ID: Int = 0x00
        const val SELECT_FIRST_OR_ONLY: Int = 0x0c
        const val CAPABILITY_CONTAINER_FILE_ID: Int = 0xe103
        const val NDEF_FILE_ID: Int = 0xe104
    }
}

/** ISO and NFC Forum application identifiers used by the holder. */
internal object MdocNfcAid {
    public val NDEF_APPLICATION: ImmutableBytes = ImmutableBytes.of(
        byteArrayOf(0xd2.toByte(), 0x76, 0x00, 0x00, 0x85.toByte(), 0x01, 0x01),
    )
    public val DATA_TRANSFER: ImmutableBytes = ImmutableBytes.of(
        byteArrayOf(0xa0.toByte(), 0x00, 0x00, 0x02, 0x48, 0x04, 0x00),
    )
    public val NFC_V2: ImmutableBytes = ImmutableBytes.of(
        byteArrayOf(0xa0.toByte(), 0x00, 0x00, 0x02, 0x48, 0x04, 0x01),
    )
}
