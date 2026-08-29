package id.walt.mdoc.proximity.mobile

import id.walt.mdoc.proximity.ImmutableBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class NfcEngagementApduProcessorTest {
    @Test
    fun `static handover serves exact select and completes after the NDEF file is read`() = runTest {
        val handoverSelect = handoverMessage("Hs")
        val completed = mutableListOf<NfcConnectionHandover>()
        val processor = NfcEngagementApduProcessor(
            NfcEngagementConfiguration.Static(ImmutableBytes.of(handoverSelect)),
            onHandover = completed::add,
        )

        assertStatus(NfcStatusWord.SUCCESS, processor.process(selectAid(MdocNfcAid.NDEF_APPLICATION)))
        assertStatus(NfcStatusWord.SUCCESS, processor.process(selectFile(0xe104)))
        val response = NfcResponseApdu.decode(processor.process(readBinary(0, 65_536)))

        assertEquals(NfcStatusWord.SUCCESS, response.statusWord)
        assertContentEquals(withNlen(handoverSelect), response.data.copy())
        assertContentEquals(handoverSelect, assertIs<NfcConnectionHandover.Static>(completed.single()).handoverSelect.copy())
    }

    @Test
    fun `static handover completes only after every NDEF file byte has been read`() = runTest {
        val handoverSelect = handoverMessage("Hs")
        val ndefFile = withNlen(handoverSelect)
        val completed = mutableListOf<NfcConnectionHandover>()
        val processor = NfcEngagementApduProcessor(
            NfcEngagementConfiguration.Static(ImmutableBytes.of(handoverSelect)),
            onHandover = completed::add,
        )
        processor.process(selectAid(MdocNfcAid.NDEF_APPLICATION))
        processor.process(selectFile(0xe104))

        processor.process(readBinary(ndefFile.lastIndex, 1))
        processor.process(readBinary(0, 2))
        assertEquals(0, completed.size)

        processor.process(readBinary(2, ndefFile.size - 3))
        assertEquals(1, completed.size)
    }

    @Test
    fun `negotiated handover enforces service selection and retains exact request and select`() = runTest {
        val handoverRequest = handoverMessage("Hr")
        val handoverSelect = handoverMessage("Hs")
        val completed = mutableListOf<NfcConnectionHandover>()
        val processor = NfcEngagementApduProcessor(
            NfcEngagementConfiguration.Negotiated { exactRequest ->
                assertContentEquals(handoverRequest, exactRequest.copy())
                ImmutableBytes.of(handoverSelect)
            },
            onHandover = completed::add,
        )
        processor.process(selectAid(MdocNfcAid.NDEF_APPLICATION))
        processor.process(selectFile(0xe104))

        val serviceSelect = NdefMessage(
            listOf(
                NdefRecord(
                    NdefTypeNameFormat.WELL_KNOWN,
                    ImmutableBytes.of("Ts".encodeToByteArray()),
                    payload = ImmutableBytes.of(
                        byteArrayOf(NfcTnepCodec.CONNECTION_HANDOVER_SERVICE.length.toByte()) +
                            NfcTnepCodec.CONNECTION_HANDOVER_SERVICE.encodeToByteArray(),
                    ),
                )
            )
        ).encode()
        assertStatus(NfcStatusWord.SUCCESS, processor.process(updateBinary(0, withNlen(serviceSelect))))
        assertStatus(NfcStatusWord.SUCCESS, processor.process(updateBinary(0, withNlen(handoverRequest))))

        val result = assertIs<NfcConnectionHandover.Negotiated>(completed.single())
        assertContentEquals(handoverRequest, result.handoverRequest.copy())
        assertContentEquals(handoverSelect, result.handoverSelect.copy())
        val staged = NfcResponseApdu.decode(processor.process(readBinary(0, 65_536)))
        assertContentEquals(withNlen(handoverSelect), staged.data.copy())
    }

    @Test
    fun `negotiated multi-command write must be sequential and exact length`() = runTest {
        val handoverRequest = handoverMessage("Hr")
        val processor = NfcEngagementApduProcessor(
            NfcEngagementConfiguration.Negotiated { ImmutableBytes.of(handoverMessage("Hs")) },
        )
        processor.process(selectAid(MdocNfcAid.NDEF_APPLICATION))
        processor.process(selectFile(0xe104))

        assertStatus(NfcStatusWord.SUCCESS, processor.process(updateBinary(0, byteArrayOf(0, 0))))
        assertStatus(NfcStatusWord.INCORRECT_PARAMETERS, processor.process(updateBinary(3, byteArrayOf(1))))
        assertStatus(NfcStatusWord.SUCCESS, processor.process(updateBinary(2, handoverRequest.copyOfRange(0, 4))))
        assertStatus(NfcStatusWord.SUCCESS, processor.process(updateBinary(6, handoverRequest.copyOfRange(4, handoverRequest.size))))
        assertStatus(
            NfcStatusWord.WRONG_LENGTH,
            processor.process(updateBinary(0, byteArrayOf(0, (handoverRequest.size - 1).toByte()))),
        )
    }

    @Test
    fun `application reselection discards an incomplete negotiated transaction`() = runTest {
        val handoverRequest = handoverMessage("Hr")
        val processor = NfcEngagementApduProcessor(
            NfcEngagementConfiguration.Negotiated { ImmutableBytes.of(handoverMessage("Hs")) },
        )
        processor.process(selectAid(MdocNfcAid.NDEF_APPLICATION))
        processor.process(selectFile(0xe104))
        processor.process(updateBinary(0, byteArrayOf(0, 0)))
        processor.process(updateBinary(2, handoverRequest.copyOfRange(0, 4)))

        assertStatus(NfcStatusWord.SUCCESS, processor.process(selectAid(MdocNfcAid.NDEF_APPLICATION)))
        assertStatus(NfcStatusWord.SUCCESS, processor.process(selectFile(0xe104)))
        assertStatus(
            NfcStatusWord.CONDITIONS_NOT_SATISFIED,
            processor.process(updateBinary(6, handoverRequest.copyOfRange(4, handoverRequest.size))),
        )
    }

    @Test
    fun `negotiated callback cancellation propagates and invalidates the transaction`() = runTest {
        val processor = NfcEngagementApduProcessor(
            NfcEngagementConfiguration.Negotiated { throw CancellationException("cancelled") },
        )
        processor.process(selectAid(MdocNfcAid.NDEF_APPLICATION))
        processor.process(selectFile(0xe104))
        val serviceSelect = NdefMessage(
            listOf(
                NdefRecord(
                    NdefTypeNameFormat.WELL_KNOWN,
                    ImmutableBytes.of("Ts".encodeToByteArray()),
                    payload = ImmutableBytes.of(
                        byteArrayOf(NfcTnepCodec.CONNECTION_HANDOVER_SERVICE.length.toByte()) +
                            NfcTnepCodec.CONNECTION_HANDOVER_SERVICE.encodeToByteArray(),
                    ),
                ),
            ),
        ).encode()
        processor.process(updateBinary(0, withNlen(serviceSelect)))

        assertFailsWith<CancellationException> {
            processor.process(updateBinary(0, withNlen(handoverMessage("Hr"))))
        }
        assertStatus(NfcStatusWord.CONDITIONS_NOT_SATISFIED, processor.process(readBinary(0, 1)))
    }

    @Test
    fun `deactivation rejects stale file operations until application is selected again`() = runTest {
        val processor = NfcEngagementApduProcessor(
            NfcEngagementConfiguration.Static(ImmutableBytes.of(handoverMessage("Hs"))),
        )
        processor.process(selectAid(MdocNfcAid.NDEF_APPLICATION))
        processor.process(selectFile(0xe104))
        processor.deactivate()

        assertStatus(NfcStatusWord.CONDITIONS_NOT_SATISFIED, processor.process(readBinary(0, 16)))
        assertStatus(NfcStatusWord.CONDITIONS_NOT_SATISFIED, processor.process(selectFile(0xe104)))
    }

    @Test
    fun `TNEP service parameters and status round trip exactly`() {
        val parameters = NfcTnepCodec.ServiceParameters(
            waitingTimeExponent = 5u,
            maximumWaitExtensions = 3u,
            maximumNdefSize = 4096u,
        )
        val encodedParameters = NfcTnepCodec.serviceParameter(parameters)
        val encodedStatus = NfcTnepCodec.status(1u)

        assertEquals(parameters, NfcTnepCodec.parseServiceParameter(encodedParameters))
        assertEquals(1u, NfcTnepCodec.parseStatus(encodedStatus))
        assertEquals(null, NfcTnepCodec.parseStatus(encodedParameters))
        assertFailsWith<IllegalArgumentException> {
            NfcTnepCodec.parseServiceParameter(
                encodedParameters.copy(payload = ImmutableBytes.of(encodedParameters.payload.copy().dropLast(1).toByteArray()))
            )
        }
    }

    @Test
    fun `handover carrier references resolve exactly and reject ambiguity`() {
        val deviceEngagement = NdefRecord(
            NdefTypeNameFormat.EXTERNAL,
            ImmutableBytes.of("iso.org:18013:deviceengagement".encodeToByteArray()),
            ImmutableBytes.of("mdoc".encodeToByteArray()),
            ImmutableBytes.of(byteArrayOf(1, 2, 3)),
        )
        val first = carrier("0", listOf(deviceEngagement))
        val second = carrier("nfc", listOf(deviceEngagement))
        val encoded = NfcHandoverCodec.encodeSelect(listOf(first, second))

        val parsed = NfcHandoverCodec.validateSelect(encoded)

        assertEquals(2, parsed.carriers.size)
        assertContentEquals("0".encodeToByteArray(), parsed.carriers[0].alternative.carrierDataReference.copy())
        assertEquals(deviceEngagement, parsed.carriers[1].auxiliaryRecords.single())

        val message = NdefMessage.decode(encoded)
        val dangling = NdefMessage(message.records.dropLast(1)).encode()
        assertFailsWith<IllegalArgumentException> { NfcHandoverCodec.validateSelect(dangling) }
        val duplicate = NdefMessage(message.records + message.records[1]).encode()
        assertFailsWith<IllegalArgumentException> { NfcHandoverCodec.validateSelect(duplicate) }
    }

    @Test
    fun `carrier identifiers cannot be reused as auxiliary references across alternatives`() {
        val firstCarrier = carrier("0").carrierRecord
        val secondCarrier = carrier("1").carrierRecord

        assertFailsWith<IllegalArgumentException> {
            NfcHandoverCodec.encodeSelect(
                listOf(
                    NfcHandoverCarrier(carrierRecord = firstCarrier),
                    NfcHandoverCarrier(carrierRecord = secondCarrier, auxiliaryRecords = listOf(firstCarrier)),
                ),
            )
        }

        val firstAlternative = alternativeCarrier("0")
        val secondAlternative = alternativeCarrier("1", auxiliaryReferences = listOf("0"))
        val embedded = NdefMessage(listOf(firstAlternative, secondAlternative)).encode()
        val handoverSelect = NdefRecord(
            NdefTypeNameFormat.WELL_KNOWN,
            ImmutableBytes.of("Hs".encodeToByteArray()),
            payload = ImmutableBytes.of(byteArrayOf(NfcHandoverCodec.VERSION_1_5.toByte()) + embedded),
        )
        val ambiguous = NdefMessage(listOf(handoverSelect, firstCarrier, secondCarrier)).encode()

        assertFailsWith<IllegalArgumentException> { NfcHandoverCodec.validateSelect(ambiguous) }
    }

    @Test
    fun `DIS request with collision resolution and Multipaz-compatible request both validate`() {
        val disD32Request = (
            "91022548721591020263720102110204616301013000110206616301036e6663005102046163010157001a201e" +
                "016170706c69636174696f6e2f766e642e626c7565746f6f74682e6c652e6f6f6230081b28078080bf2801021c" +
                "021107c832fff6d26fa0beb34dfcd555d4823a1c11010369736f2e6f72673a31383031333a6e66636e6663015a" +
                "172b016170706c69636174696f6e2f766e642e7766612e6e616e57030101032302001324fec9a70b97ac9684a4" +
                "e326176ef5b981c5e8533e5f00298cfccbc35e700a6b020414"
            ).hexToByteArray()

        val dis = NfcHandoverCodec.validateRequest(disD32Request)
        assertEquals(3, dis.carriers.size)
        assertContentEquals("cr".encodeToByteArray(), dis.embeddedMessage.records.first().type.copy())
        assertContentEquals(disD32Request, dis.outerMessage.encode())

        val multipazCompatible = NfcHandoverCodec.validateRequest(handoverMessage("Hr"))
        assertEquals(1, multipazCompatible.carriers.size)
        assertEquals(1, multipazCompatible.embeddedMessage.records.size)
        assertContentEquals(
            "ac".encodeToByteArray(),
            multipazCompatible.embeddedMessage.records.single().type.copy(),
        )
    }

    private fun handoverMessage(type: String): ByteArray {
        val carrier = carrier("0")
        return if (type == "Hs") {
            NfcHandoverCodec.encodeSelect(listOf(carrier))
        } else {
            NfcHandoverCodec.encodeRequest(listOf(carrier))
        }
    }

    private fun carrier(identifier: String, auxiliary: List<NdefRecord> = emptyList()): NfcHandoverCarrier =
        NfcHandoverCarrier(
            carrierRecord = NdefRecord(
                NdefTypeNameFormat.MIME_MEDIA,
                ImmutableBytes.of("application/example".encodeToByteArray()),
                ImmutableBytes.of(identifier.encodeToByteArray()),
                ImmutableBytes.of(byteArrayOf(1)),
            ),
            auxiliaryRecords = auxiliary,
        )

    private fun alternativeCarrier(
        carrierReference: String,
        auxiliaryReferences: List<String> = emptyList(),
    ): NdefRecord {
        val payload = buildList {
            add(NfcCarrierPowerState.ACTIVE.code.toByte())
            add(carrierReference.length.toByte())
            addAll(carrierReference.encodeToByteArray().asList())
            add(auxiliaryReferences.size.toByte())
            auxiliaryReferences.forEach { reference ->
                add(reference.length.toByte())
                addAll(reference.encodeToByteArray().asList())
            }
        }.toByteArray()
        return NdefRecord(
            NdefTypeNameFormat.WELL_KNOWN,
            ImmutableBytes.of("ac".encodeToByteArray()),
            payload = ImmutableBytes.of(payload),
        )
    }

    private fun selectAid(aid: ImmutableBytes): ByteArray = NfcCommandApdu(
        0u, 0xa4u, 0x04u, 0u, aid,
    ).encode()

    private fun selectFile(identifier: Int): ByteArray = NfcCommandApdu(
        0u, 0xa4u, 0u, 0x0cu,
        ImmutableBytes.of(byteArrayOf((identifier ushr 8).toByte(), identifier.toByte())),
    ).encode()

    private fun readBinary(offset: Int, length: Int): ByteArray = NfcCommandApdu(
        0u, 0xb0u, (offset ushr 8).toUByte(), offset.toUByte(), expectedResponseDataLength = length,
    ).encode()

    private fun updateBinary(offset: Int, data: ByteArray): ByteArray = NfcCommandApdu(
        0u, 0xd6u, (offset ushr 8).toUByte(), offset.toUByte(), ImmutableBytes.of(data),
    ).encode()

    private fun withNlen(message: ByteArray): ByteArray =
        byteArrayOf((message.size ushr 8).toByte(), message.size.toByte()) + message

    private fun assertStatus(expected: UShort, encoded: ByteArray) {
        assertEquals(expected, NfcResponseApdu.decode(encoded).statusWord)
    }
}
