@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class, ExperimentalUnsignedTypes::class)

package id.walt.mdoc.proximity.mobile

import id.walt.cose.Cose
import id.walt.cose.CoseKey
import id.walt.cose.coseCompliantCbor
import id.walt.mdoc.encoding.ByteStringWrapper
import id.walt.mdoc.encoding.ExactCbor
import id.walt.mdoc.objects.engagement.BleCentralMode
import id.walt.mdoc.objects.engagement.BlePeripheralEndpoint
import id.walt.mdoc.objects.engagement.BlePeripheralMode
import id.walt.mdoc.objects.engagement.BlePeripheralServerOptions
import id.walt.mdoc.objects.engagement.DeviceEngagement
import id.walt.mdoc.objects.engagement.DeviceEngagementSecurity
import id.walt.mdoc.objects.engagement.DeviceRetrievalMethod
import id.walt.mdoc.objects.engagement.DeviceRetrievalMethodCodec
import id.walt.mdoc.proximity.ImmutableBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.cbor.CborArray
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.cbor.CborInteger
import kotlinx.serialization.cbor.CborMap
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class NfcV2ApduProcessorTest {
    private val publicKey = CoseKey(
        kty = Cose.KeyTypes.EC2,
        crv = Cose.EllipticCurves.P_256,
        x = ByteArray(32) { it.toByte() },
        y = ByteArray(32) { (it + 1).toByte() },
    )

    @Test
    fun `select and NFC-only handover match the pinned provisional shape`() = runTest {
        val nfcV2 = DeviceRetrievalMethod.NfcV2
        val exactRequest = request(nfcV2)
        assertContentEquals("a100a10281830501a0".hexToByteArray(), exactRequest)
        val handovers = mutableListOf<NfcV2Handover>()
        val processor = NfcV2ApduProcessor(
            NfcV2MaximumCommandDataLength(65_536),
            128 * 1_024,
            select = { request ->
                assertContentEquals(exactRequest, request.exactBytes.copy())
                selection(nfcV2)
            },
            onHandover = handovers::add,
        )

        val selectResponse = response(processor.process(selectNfcV2()))
        assertEquals(NfcStatusWord.SUCCESS, selectResponse.statusWord)
        assertContentEquals("a1001a00010000".hexToByteArray(), selectResponse.data.copy())

        val handoverResponse = response(
            processor.process(envelope(NfcDo53.encode(exactRequest), responseLength = 65_536)),
        )
        val exactSelect = NfcDo53.decode(handoverResponse.data.copy(), 128 * 1_024)
        assertContentEquals(exactSelect, handovers.single().handoverSelect.copy())
        assertIs<NfcV2Handover.SameChannel>(handovers.single())
        assertEquals(NfcV2State.AWAITING_PAYLOAD, processor.state)

        val sessionRequest = assertIs<NfcV2ApduResult.Request>(
            processor.process(envelope(NfcDo53.encode(byteArrayOf(1, 2, 3)), responseLength = 256)),
        )
        assertContentEquals(byteArrayOf(1, 2, 3), sessionRequest.sessionMessage.copy())
        val sessionResponse = NfcResponseApdu.decode(
            processor.completeResponse(sessionRequest.identifier, byteArrayOf(4, 5)).copy(),
        )
        assertContentEquals(byteArrayOf(4, 5), NfcDo53.decode(sessionResponse.data.copy(), 128 * 1_024))
        assertEquals(NfcV2State.AWAITING_PAYLOAD, processor.state)
    }

    @Test
    fun `alternate bearer handover retains the NFC payload path`() = runTest {
        val nfcV2 = DeviceRetrievalMethod.NfcV2
        val ble = DeviceRetrievalMethod.Ble(centralMode = BleCentralMode(ByteArray(16)))
        val handovers = mutableListOf<NfcV2Handover>()
        val processor = NfcV2ApduProcessor(
            NfcV2MaximumCommandDataLength(4_096),
            128 * 1_024,
            select = { selection(ble) },
            onHandover = handovers::add,
        )
        processor.process(selectNfcV2())
        response(processor.process(envelope(NfcDo53.encode(request(nfcV2, ble)), responseLength = 65_536)))

        assertEquals(NfcV2State.AWAITING_PAYLOAD, processor.state)
        assertEquals(ble, assertIs<NfcV2Handover.AlternateBearer>(handovers.single()).selectedMethod)
        val request = assertIs<NfcV2ApduResult.Request>(
            processor.process(envelope(NfcDo53.encode(byteArrayOf(1)), responseLength = 256)),
        )
        assertContentEquals(byteArrayOf(1), request.sessionMessage.copy())
    }

    @Test
    fun `APDU response length bounds handover and session response chunks`() = runTest {
        val nfcV2 = DeviceRetrievalMethod.NfcV2
        val processor = NfcV2ApduProcessor(
            NfcV2MaximumCommandDataLength(4_096),
            128 * 1_024,
            select = { selection(nfcV2) },
        )
        processor.process(selectNfcV2())

        val firstHandover = response(
            processor.process(envelope(NfcDo53.encode(request(nfcV2)), responseLength = 8)),
        )
        assertEquals(8, firstHandover.data.size)
        assertEquals(0x61, firstHandover.statusByte1.toInt())
        drainResponse(processor, firstHandover, responseLength = 8)
        assertEquals(NfcV2State.AWAITING_PAYLOAD, processor.state)

        val pending = assertIs<NfcV2ApduResult.Request>(
            processor.process(envelope(NfcDo53.encode(byteArrayOf(1)), responseLength = 8)),
        )
        val firstSessionResponse = NfcResponseApdu.decode(
            processor.completeResponse(pending.identifier, ByteArray(32) { it.toByte() }).copy(),
        )
        assertEquals(8, firstSessionResponse.data.size)
        assertEquals(0x61, firstSessionResponse.statusByte1.toInt())
        drainResponse(processor, firstSessionResponse, responseLength = 8)
        assertEquals(NfcV2State.AWAITING_PAYLOAD, processor.state)
    }

    @Test
    fun `oversized wallet response retains pending ownership for a valid retry`() = runTest {
        val nfcV2 = DeviceRetrievalMethod.NfcV2
        val processor = NfcV2ApduProcessor(
            NfcV2MaximumCommandDataLength(4_096),
            128,
            select = { selection(nfcV2) },
        )
        processor.process(selectNfcV2())
        response(processor.process(envelope(NfcDo53.encode(request(nfcV2)), responseLength = 65_536)))
        val pending = assertIs<NfcV2ApduResult.Request>(
            processor.process(envelope(NfcDo53.encode(byteArrayOf(1)), responseLength = 256)),
        )

        assertFailsWith<IllegalArgumentException> {
            processor.completeResponse(pending.identifier, ByteArray(129))
        }
        assertEquals(NfcV2State.AWAITING_WALLET_RESPONSE, processor.state)
        assertEquals(
            NfcStatusWord.SUCCESS,
            NfcResponseApdu.decode(processor.completeResponse(pending.identifier, byteArrayOf(2)).copy()).statusWord,
        )
    }

    @Test
    fun `handover is not published when its exact select exceeds the configured limit`() = runTest {
        val nfcV2 = DeviceRetrievalMethod.NfcV2
        val handovers = mutableListOf<NfcV2Handover>()
        val processor = NfcV2ApduProcessor(
            NfcV2MaximumCommandDataLength(4_096),
            32,
            select = { selection(nfcV2) },
            onHandover = handovers::add,
        )
        processor.process(selectNfcV2())

        assertEquals(
            NfcStatusWord.WRONG_DATA,
            response(processor.process(envelope(NfcDo53.encode(request(nfcV2)), responseLength = 65_536))).statusWord,
        )
        assertEquals(0, handovers.size)
        assertEquals(NfcV2State.DEACTIVATED, processor.state)
    }

    @Test
    fun `handover selection cancellation propagates and deactivates the processor`() = runTest {
        val nfcV2 = DeviceRetrievalMethod.NfcV2
        val processor = NfcV2ApduProcessor(
            NfcV2MaximumCommandDataLength(4_096),
            128,
            select = { throw CancellationException("cancelled") },
        )
        processor.process(selectNfcV2())

        assertFailsWith<CancellationException> {
            processor.process(envelope(NfcDo53.encode(request(nfcV2)), responseLength = 65_536))
        }
        assertEquals(NfcV2State.DEACTIVATED, processor.state)
    }

    @Test
    fun `dual BLE offer can be narrowed to either exact holder role`() = runTest {
        val readerPeripheralUuid = ByteArray(16) { it.toByte() }
        val holderPeripheralUuid = ByteArray(16) { (it + 16).toByte() }
        val readerOptions = BlePeripheralServerOptions(psm = 37u)
        val offered = DeviceRetrievalMethod.Ble(
            peripheralMode = BlePeripheralMode(holderPeripheralUuid),
            centralMode = BleCentralMode(readerPeripheralUuid),
            peripheralEndpoint = BlePeripheralEndpoint.Reader(readerOptions),
        )
        val selections = listOf(
            DeviceRetrievalMethod.Ble(
                centralMode = BleCentralMode(readerPeripheralUuid),
                peripheralEndpoint = BlePeripheralEndpoint.Reader(readerOptions),
            ),
            DeviceRetrievalMethod.Ble(
                peripheralMode = BlePeripheralMode(holderPeripheralUuid),
                peripheralEndpoint = BlePeripheralEndpoint.Mdoc(
                    BlePeripheralServerOptions(psm = 41u),
                ),
            ),
        )

        selections.forEach { selected ->
            val handovers = mutableListOf<NfcV2Handover>()
            val processor = NfcV2ApduProcessor(
                NfcV2MaximumCommandDataLength(4_096),
                128 * 1_024,
                select = { selection(selected) },
                onHandover = handovers::add,
            )

            processor.process(selectNfcV2())
            val result = response(
                processor.process(
                    envelope(NfcDo53.encode(request(DeviceRetrievalMethod.NfcV2, offered)), 65_536),
                ),
            )

            assertEquals(NfcStatusWord.SUCCESS, result.statusWord)
            assertEquals(selected, assertIs<NfcV2Handover.AlternateBearer>(handovers.single()).selectedMethod)
        }
    }

    @Test
    fun `Multipaz hybrid reader offer with NFC fallback is accepted`() = runTest {
        val sharedUuid = ByteArray(16) { it.toByte() }
        val readerOptions = BlePeripheralServerOptions(psm = 183u)
        val offeredBle = DeviceRetrievalMethod.Ble(
            peripheralMode = BlePeripheralMode(sharedUuid),
            centralMode = BleCentralMode(sharedUuid),
            peripheralEndpoint = BlePeripheralEndpoint.Reader(readerOptions),
        )
        val selectedBle = DeviceRetrievalMethod.Ble(
            centralMode = BleCentralMode(sharedUuid),
            peripheralEndpoint = BlePeripheralEndpoint.Reader(readerOptions),
        )
        val exactRequest = request(
            DeviceRetrievalMethod.NfcV2,
            offeredBle,
            DeviceRetrievalMethod.Nfc(65_535u, 65_536u),
        )
        val failures = mutableListOf<Throwable>()
        val handovers = mutableListOf<NfcV2Handover>()
        val processor = NfcV2ApduProcessor(
            NfcV2MaximumCommandDataLength(65_536),
            128 * 1_024,
            select = { selection(selectedBle) },
            onHandover = handovers::add,
            onFailure = failures::add,
        )

        processor.process(selectNfcV2())
        val command = envelope(NfcDo53.encode(exactRequest), responseLength = 65_279)
        val result = response(processor.process(command))

        assertEquals(NfcStatusWord.SUCCESS, result.statusWord, failures.singleOrNull()?.message)
        assertEquals(0, failures.size)
        assertEquals(selectedBle, assertIs<NfcV2Handover.AlternateBearer>(handovers.single()).selectedMethod)
    }

    @Test
    fun `BLE selection must retain the exact reader-offered endpoint`() = runTest {
        val offered = DeviceRetrievalMethod.Ble(
            centralMode = BleCentralMode(ByteArray(16) { it.toByte() }),
            peripheralEndpoint = BlePeripheralEndpoint.Reader(BlePeripheralServerOptions(psm = 37u)),
        )
        val unoffered = DeviceRetrievalMethod.Ble(
            centralMode = BleCentralMode(ByteArray(16) { (it + 1).toByte() }),
            peripheralEndpoint = offered.peripheralEndpoint,
        )
        val processor = NfcV2ApduProcessor(
            NfcV2MaximumCommandDataLength(4_096),
            128 * 1_024,
            select = { selection(unoffered) },
        )

        processor.process(selectNfcV2())
        val result = response(
            processor.process(
                envelope(NfcDo53.encode(request(DeviceRetrievalMethod.NfcV2, offered)), 65_536),
            ),
        )

        assertEquals(NfcStatusWord.WRONG_DATA, result.statusWord)
        assertEquals(NfcV2State.DEACTIVATED, processor.state)
    }

    @Test
    fun `handover without NFCv2 fails closed`() = runTest {
        val ble = DeviceRetrievalMethod.Ble(centralMode = BleCentralMode(ByteArray(16)))
        val processor = NfcV2ApduProcessor(
            NfcV2MaximumCommandDataLength(4_096),
            128 * 1_024,
            select = { selection(ble) },
        )
        processor.process(selectNfcV2())
        val result = response(
            processor.process(envelope(NfcDo53.encode(request(ble)), responseLength = 256)),
        )

        assertEquals(NfcStatusWord.WRONG_DATA, result.statusWord)
        assertEquals(NfcV2State.DEACTIVATED, processor.state)
    }

    private fun selection(method: DeviceRetrievalMethod): NfcV2HandoverSelection {
        val encodedKey = coseCompliantCbor.encodeToByteArray(CoseKey.serializer(), publicKey)
        val engagement = DeviceEngagement(
            DeviceEngagement.VERSION_1_0,
            DeviceEngagementSecurity(1u, ByteStringWrapper(publicKey, encodedKey)),
            deviceRetrievalMethods = listOf(method),
        )
        return NfcV2HandoverSelection(
            method,
            ExactCbor.of(engagement, coseCompliantCbor.encodeToByteArray(DeviceEngagement.serializer(), engagement)),
        )
    }

    private fun request(vararg methods: DeviceRetrievalMethod): ByteArray {
        val methodElements = methods.map { method ->
            coseCompliantCbor.decodeFromByteArray<CborElement>(
                DeviceRetrievalMethodCodec.encodeReaderEngagement(method),
            )
        }
        val readerEngagement = CborMap(mapOf(CborInteger(2) to CborArray(methodElements)))
        return coseCompliantCbor.encodeToByteArray(
            CborElement.serializer(),
            CborMap(mapOf(CborInteger(0) to readerEngagement)),
        )
    }

    private fun selectNfcV2(): ByteArray = NfcCommandApdu(
        0u, 0xa4u, 0x04u, 0u, MdocNfcAid.NFC_V2,
    ).encode()

    private fun envelope(data: ByteArray, responseLength: Int): ByteArray = NfcCommandApdu(
        0u, 0xc3u, 0u, 0u, ImmutableBytes.of(data), responseLength,
    ).encode()

    private fun response(result: NfcV2ApduResult): NfcResponseApdu = NfcResponseApdu.decode(
        assertIs<NfcV2ApduResult.Response>(result).encoded.copy(),
    )

    private suspend fun drainResponse(
        processor: NfcV2ApduProcessor,
        first: NfcResponseApdu,
        responseLength: Int = 65_536,
    ): ByteArray {
        val chunks = mutableListOf(first.data.copy())
        var current = first
        while (current.statusByte1 == 0x61.toUByte()) {
            current = response(
                processor.process(
                    NfcCommandApdu(
                        0u,
                        0xc0u,
                        0u,
                        0u,
                        expectedResponseDataLength = responseLength,
                    ).encode(),
                ),
            )
            assertEquals(true, current.data.size <= responseLength)
            chunks += current.data.copy()
        }
        assertEquals(NfcStatusWord.SUCCESS, current.statusWord)
        val size = chunks.sumOf(ByteArray::size)
        return ByteArray(size).also { output ->
            var offset = 0
            chunks.forEach { chunk ->
                chunk.copyInto(output, offset)
                offset += chunk.size
            }
        }
    }
}
