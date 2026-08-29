@file:OptIn(
    ExperimentalUnsignedTypes::class,
    kotlinx.serialization.ExperimentalSerializationApi::class,
)

package id.walt.mdoc.proximity.mobile

import id.walt.mdoc.objects.engagement.DeviceRetrievalMethod
import id.walt.mdoc.proximity.ImmutableBytes
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class NfcRetrievalApduProcessorTest {
    @Test
    fun `chained envelope yields one request and chunks one response`() {
        val processor = processor()
        assertResponse(NfcStatusWord.SUCCESS, processor.process(selectDataTransferApplication()))
        val request = NfcDo53.encode(byteArrayOf(1, 2, 3, 4, 5))

        assertResponse(NfcStatusWord.SUCCESS, processor.process(envelope(request.copyOfRange(0, 3), chained = true)))
        val pending = assertIs<NfcRetrievalApduResult.Request>(
            processor.process(envelope(request.copyOfRange(3, request.size), responseLength = 4)),
        )
        assertContentEquals(byteArrayOf(1, 2, 3, 4, 5), pending.sessionMessage.copy())
        assertEquals(NfcRetrievalState.AWAITING_WALLET_RESPONSE, processor.state)

        val first = NfcResponseApdu.decode(processor.completeResponse(pending.identifier, byteArrayOf(9, 8, 7, 6, 5)).copy())
        assertEquals(0x6103u, first.statusWord)
        assertContentEquals(NfcDo53.encode(byteArrayOf(9, 8, 7, 6, 5)).copyOfRange(0, 4), first.data.copy())
        val second = response(processor.process(getResponse(4)))
        assertEquals(NfcStatusWord.SUCCESS, second.statusWord)
        assertEquals(NfcRetrievalState.READY, processor.state)
    }

    @Test
    fun `a pending response is owned exactly once`() {
        val processor = processor()
        processor.process(selectDataTransferApplication())
        val pending = assertIs<NfcRetrievalApduResult.Request>(
            processor.process(envelope(NfcDo53.encode(byteArrayOf(1)), responseLength = 256)),
        )
        processor.completeResponse(pending.identifier, byteArrayOf(2))
        assertFailsWith<IllegalStateException> { processor.completeResponse(pending.identifier, byteArrayOf(3)) }
    }

    @Test
    fun `multiple request response cycles reuse selection but not pending ownership`() {
        val processor = processor()
        processor.process(selectDataTransferApplication())
        repeat(2) { value ->
            val pending = assertIs<NfcRetrievalApduResult.Request>(
                processor.process(envelope(NfcDo53.encode(byteArrayOf(value.toByte())), responseLength = 256)),
            )
            val response = NfcResponseApdu.decode(processor.completeResponse(pending.identifier, byteArrayOf(value.toByte())).copy())
            assertEquals(NfcStatusWord.SUCCESS, response.statusWord)
            assertEquals(NfcRetrievalState.READY, processor.state)
        }
    }

    @Test
    fun `malformed do53 and illegal APDU order fail closed`() {
        val processor = processor()
        assertResponse(NfcStatusWord.CONDITIONS_NOT_SATISFIED, processor.process(envelope(byteArrayOf(0x53, 0))))
        assertEquals(NfcRetrievalState.AWAITING_APPLICATION_SELECTION, processor.state)
        processor.process(selectDataTransferApplication())
        assertResponse(
            NfcStatusWord.WRONG_DATA,
            processor.process(envelope(byteArrayOf(0x53, 0x81.toByte(), 0x01, 0x00), responseLength = 256)),
        )
        assertEquals(NfcRetrievalState.DEACTIVATED, processor.state)
    }

    @Test
    fun `data transfer selection requires the standard P2 and absent Le`() {
        val wrongP2 = NfcCommandApdu(
            0u, 0xa4u, 0x04u, 0u, MdocNfcAid.DATA_TRANSFER,
        ).encode()
        assertResponse(NfcStatusWord.INCORRECT_PARAMETERS, processor().process(wrongP2))

        val unexpectedLe = NfcCommandApdu(
            0u, 0xa4u, 0x04u, 0x0cu, MdocNfcAid.DATA_TRANSFER, expectedResponseDataLength = 256,
        ).encode()
        assertResponse(NfcStatusWord.WRONG_LENGTH, processor().process(unexpectedLe))
    }

    @Test
    fun `deactivation invalidates late response and future APDUs`() {
        val processor = processor()
        processor.process(selectDataTransferApplication())
        val pending = assertIs<NfcRetrievalApduResult.Request>(
            processor.process(envelope(NfcDo53.encode(byteArrayOf(1)), responseLength = 256)),
        )
        processor.deactivate()
        assertFailsWith<IllegalStateException> { processor.completeResponse(pending.identifier, byteArrayOf(2)) }
        assertResponse(NfcStatusWord.CONDITIONS_NOT_SATISFIED, processor.process(getResponse(256)))
    }

    @Test
    fun `do53 uses canonical boundaries and rejects trailing or non-minimal forms`() {
        listOf(0, 0x7f, 0x80, 0xff, 0x100, 0xffff, 0x10000).forEach { size ->
            val payload = ByteArray(size) { it.toByte() }
            assertContentEquals(payload, NfcDo53.decode(NfcDo53.encode(payload), size))
        }
        listOf(
            byteArrayOf(0x53, 0x81.toByte(), 0x01, 0),
            byteArrayOf(0x53, 0x82.toByte(), 0, 1, 0),
            byteArrayOf(0x53, 0, 1),
            byteArrayOf(0x54, 0),
        ).forEach { assertFailsWith<IllegalArgumentException> { NfcDo53.decode(it, 1_024) } }
        assertFailsWith<IllegalArgumentException> { NfcDo53.decode(byteArrayOf(0x53, 0), -1) }
    }

    @Test
    fun `invalid envelope metadata cannot contaminate the next message`() {
        val exchange = NfcApduMessageExchange(255, 256, 1_024)
        assertFailsWith<IllegalArgumentException> {
            exchange.accept(
                NfcCommandApdu(
                    0x10u,
                    0xc3u,
                    0u,
                    0u,
                    ImmutableBytes.of(byteArrayOf(0x53)),
                    expectedResponseDataLength = 256,
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            exchange.accept(
                NfcCommandApdu(
                    0u,
                    0xc3u,
                    0u,
                    0u,
                    ImmutableBytes.of(byteArrayOf(0x53, 0x02, 0x01)),
                    expectedResponseDataLength = 256,
                ),
            )
        }

        val message = byteArrayOf(1, 2, 3)
        val accepted = assertIs<NfcApduMessageExchange.IncomingResult.Message>(
            exchange.accept(
                NfcCommandApdu(
                    0u,
                    0xc3u,
                    0u,
                    0u,
                    ImmutableBytes.of(NfcDo53.encode(message)),
                    expectedResponseDataLength = 256,
                ),
            ),
        )
        assertContentEquals(message, accepted.bytes)
        assertFailsWith<IllegalArgumentException> {
            exchange.accept(
                NfcCommandApdu(
                    0u,
                    0xc2u,
                    0u,
                    0u,
                    ImmutableBytes.of(NfcDo53.encode(message)),
                    expectedResponseDataLength = 256,
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> { NfcApduMessageExchange(0, 256, 1_024) }
        assertFailsWith<IllegalArgumentException> { NfcApduMessageExchange(255, 0, 1_024) }
        assertFailsWith<IllegalArgumentException> { NfcApduMessageExchange(255, 256, 0) }
        assertFailsWith<IllegalArgumentException> {
            NfcApduMessageExchange(255, 256, NfcDo53.MAXIMUM_SESSION_MESSAGE_LENGTH + 1)
        }
    }

    private fun processor(maximumResponseLength: UInt = 256u): NfcRetrievalApduProcessor =
        NfcRetrievalApduProcessor(DeviceRetrievalMethod.Nfc(255u, maximumResponseLength), 128 * 1_024)

    private fun selectDataTransferApplication(): ByteArray = NfcCommandApdu(
        0u, 0xa4u, 0x04u, 0x0cu, MdocNfcAid.DATA_TRANSFER,
    ).encode()

    private fun envelope(data: ByteArray, chained: Boolean = false, responseLength: Int? = null): ByteArray =
        NfcCommandApdu(
            if (chained) 0x10u else 0u,
            0xc3u,
            0u,
            0u,
            ImmutableBytes.of(data),
            responseLength,
        ).encode()

    private fun getResponse(length: Int): ByteArray = NfcCommandApdu(
        0u, 0xc0u, 0u, 0u, expectedResponseDataLength = length,
    ).encode()

    private fun assertResponse(status: UShort, result: NfcRetrievalApduResult) {
        assertEquals(status, response(result).statusWord)
    }

    private fun response(result: NfcRetrievalApduResult): NfcResponseApdu = NfcResponseApdu.decode(
        assertIs<NfcRetrievalApduResult.Response>(result).encoded.copy(),
    )
}
