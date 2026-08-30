@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.mdoc.proximity.mobile

import id.walt.mdoc.objects.engagement.BleCentralMode
import id.walt.mdoc.objects.engagement.BlePeripheralEndpoint
import id.walt.mdoc.objects.engagement.BlePeripheralMode
import id.walt.mdoc.objects.engagement.BlePeripheralServerOptions
import id.walt.mdoc.objects.engagement.DeviceRetrievalMethod
import id.walt.mdoc.proximity.ImmutableBytes
import id.walt.mdoc.proximity.ReaderSelectedTransportOffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class NfcMdocCarrierCodecTest {
    @Test
    fun `conventional NFC carrier uses bounded minimal integer fields`() {
        val method = DeviceRetrievalMethod.Nfc(255u, 65_536u)
        val carrier = NfcMdocCarrierCodec.encode(
            method,
            ImmutableBytes.of("nfc".encodeToByteArray()),
            emptyList(),
            NfcMdocActor.HOLDER,
        )

        assertContentEquals("010201ff0402010000".hexToByteArray(), carrier.carrierRecord.payload.copy())
        val parsed = NfcHandoverCodec.validateSelect(NfcHandoverCodec.encodeSelect(listOf(carrier)))
        assertEquals(method, NfcMdocCarrierCodec.decode(parsed.carriers.single(), NfcMdocActor.HOLDER))
    }

    @Test
    fun `BLE carrier preserves actor role UUID address and PSM`() {
        val uuid = ByteArray(16) { it.toByte() }
        val holderPeripheral = DeviceRetrievalMethod.Ble(
            peripheralMode = BlePeripheralMode(uuid),
            peripheralEndpoint = BlePeripheralEndpoint.Mdoc(
                BlePeripheralServerOptions(
                    deviceAddress = byteArrayOf(1, 2, 3, 4, 5, 6),
                    psm = 0x1001u,
                ),
            ),
        )
        val carrier = NfcMdocCarrierCodec.encode(
            holderPeripheral,
            ImmutableBytes.of("0".encodeToByteArray()),
            emptyList(),
            NfcMdocActor.HOLDER,
        )
        assertContentEquals(
            byteArrayOf(2, 0x1c, 0, 0x11, 0x07) + uuid.reversedArray(),
            carrier.carrierRecord.payload.copy().copyOf(21),
        )
        val parsed = NfcHandoverCodec.validateSelect(NfcHandoverCodec.encodeSelect(listOf(carrier)))

        assertEquals(
            holderPeripheral,
            NfcMdocCarrierCodec.decode(parsed.carriers.single(), NfcMdocActor.HOLDER),
        )
    }

    @Test
    fun `reader and holder roles decode relative to the record origin`() {
        val uuid = ByteArray(16) { (it + 16).toByte() }
        val readerCentral = DeviceRetrievalMethod.Ble(centralMode = BleCentralMode(uuid))
        val carrier = NfcMdocCarrierCodec.encode(
            readerCentral,
            ImmutableBytes.of("0".encodeToByteArray()),
            emptyList(),
            NfcMdocActor.READER,
        )
        val parsed = NfcHandoverCodec.validateRequest(NfcHandoverCodec.encodeRequest(listOf(carrier)))

        assertEquals(readerCentral, NfcMdocCarrierCodec.decode(parsed.carriers.single(), NfcMdocActor.READER))
        assertFailsWith<IllegalArgumentException> {
            NfcMdocCarrierCodec.encode(
                DeviceRetrievalMethod.NfcV2,
                ImmutableBytes.of("v2".encodeToByteArray()),
                emptyList(),
                NfcMdocActor.HOLDER,
            )
        }
    }

    @Test
    fun `reader can offer a holder-owned peripheral endpoint without inventing a UUID`() {
        val uuid = ByteArray(16) { (it + 32).toByte() }
        val offer = DeviceRetrievalMethod.Ble(peripheralMode = BlePeripheralMode(uuid))
        val carrier = NfcMdocCarrierCodec.encode(
            offer,
            ImmutableBytes.of("0".encodeToByteArray()),
            emptyList(),
            NfcMdocActor.READER,
            omitBleUuid = true,
        )
        val parsed = NfcHandoverCodec.validateRequest(NfcHandoverCodec.encodeRequest(listOf(carrier)))

        assertEquals(
            ReaderSelectedTransportOffer.BlePeripheralServer,
            NfcMdocCarrierCodec.decodeReaderOffer(parsed.carriers.single()),
        )
    }

    @Test
    fun `reader central-only offer rejects reader-peripheral endpoint data when UUID is omitted`() {
        val uuid = ByteArray(16) { (it + 48).toByte() }
        val carrier = NfcMdocCarrierCodec.encode(
            DeviceRetrievalMethod.Ble(
                peripheralMode = BlePeripheralMode(uuid),
                peripheralEndpoint = BlePeripheralEndpoint.Mdoc(BlePeripheralServerOptions(psm = 0x1001u)),
            ),
            ImmutableBytes.of("0".encodeToByteArray()),
            emptyList(),
            NfcMdocActor.HOLDER,
            omitBleUuid = true,
        )
        val readerOriginated = carrier.copy(
            carrierRecord = carrier.carrierRecord.copy(
                payload = ImmutableBytes.of(
                    carrier.carrierRecord.payload.copy().also { payload ->
                        payload[2] = 0x01
                    },
                ),
            ),
        )
        val parsed = NfcHandoverCodec.validateRequest(
            NfcHandoverCodec.encodeRequest(listOf(readerOriginated)),
        )

        assertFailsWith<IllegalArgumentException> {
            NfcMdocCarrierCodec.decodeReaderOffer(parsed.carriers.single())
        }
    }

    @Test
    fun `unrelated BLE service data is ignored but duplicate mdoc PSM data is rejected`() {
        val method = DeviceRetrievalMethod.Ble(
            peripheralMode = BlePeripheralMode(ByteArray(16) { it.toByte() }),
            peripheralEndpoint = BlePeripheralEndpoint.Mdoc(BlePeripheralServerOptions(psm = 0x81u)),
        )
        val carrier = NfcMdocCarrierCodec.encode(
            method,
            ImmutableBytes.of("0".encodeToByteArray()),
            emptyList(),
            NfcMdocActor.HOLDER,
        )
        val withUnrelatedServiceData = carrier.copy(
            carrierRecord = carrier.carrierRecord.copy(
                payload = ImmutableBytes.of(
                    carrier.carrierRecord.payload.copy() + byteArrayOf(3, 0x16, 0x34, 0x12),
                ),
            ),
        )
        val parsed = NfcHandoverCodec.validateSelect(
            NfcHandoverCodec.encodeSelect(listOf(withUnrelatedServiceData)),
        )
        assertEquals(method, NfcMdocCarrierCodec.decode(parsed.carriers.single(), NfcMdocActor.HOLDER))

        val withDuplicateMdocServiceData = carrier.copy(
            carrierRecord = carrier.carrierRecord.copy(
                payload = ImmutableBytes.of(
                    carrier.carrierRecord.payload.copy() +
                        byteArrayOf(4, 0x16, 0x01, 0xff.toByte(), 0xa0.toByte()),
                ),
            ),
        )
        val duplicate = NfcHandoverCodec.validateSelect(
            NfcHandoverCodec.encodeSelect(listOf(withDuplicateMdocServiceData)),
        )
        assertFailsWith<IllegalArgumentException> {
            NfcMdocCarrierCodec.decode(duplicate.carriers.single(), NfcMdocActor.HOLDER)
        }
    }

    @Test
    fun `Multipaz reader handover offer preserves its central endpoint and L2CAP PSM`() {
        val encodedRequest = """
            91020a487215d10204616301013000
            1c1e060a69736f2e6f72673a31383031333a726561646572656e676167656d656e74
            6d646f63726561646572a10063312e30
            5a201d016170706c69636174696f6e2f766e642e626c7565746f6f74682e6c652e6f6f62
            30021c001107781cb4a9de25e397924b465dd48f8c1c071601ffa1001882
        """.filterNot(Char::isWhitespace).hexToByteArray()

        val request = NfcHandoverCodec.validateRequest(encodedRequest)
        val offer = assertIs<ReaderSelectedTransportOffer.Method>(
            NfcMdocCarrierCodec.decodeReaderOffer(request.carriers.single()),
        )
        val ble = assertIs<DeviceRetrievalMethod.Ble>(offer.value)

        assertContentEquals(
            "1c8c8fd45d464b9297e325dea9b41c78".hexToByteArray(),
            ble.centralMode?.uuid,
        )
        assertEquals(
            130u,
            assertIs<BlePeripheralEndpoint.Reader>(ble.peripheralEndpoint).options.psm,
        )
    }

    @Test
    fun `Wi-Fi Aware carrier round trip preserves explicit NCS-SK selection and bands`() {
        val method = DeviceRetrievalMethod.WifiAware(
            passphraseInfo = "12345678",
            operatingClass = 81u,
            channelNumber = 6u,
            supportedBands = byteArrayOf(0x14),
        )
        val carrier = NfcMdocCarrierCodec.encode(
            method,
            ImmutableBytes.of("W".encodeToByteArray()),
            emptyList(),
            NfcMdocActor.HOLDER,
        )
        assertContentEquals(
            byteArrayOf(2, 1, 1, 9, 3) + "12345678".encodeToByteArray() + byteArrayOf(2, 4, 0x14, 3, 5, 81, 6),
            carrier.carrierRecord.payload.copy(),
        )

        val parsed = NfcHandoverCodec.validateSelect(NfcHandoverCodec.encodeSelect(listOf(carrier)))
        assertEquals(method, NfcMdocCarrierCodec.decode(parsed.carriers.single(), NfcMdocActor.HOLDER))
    }

    @Test
    fun `Wi-Fi Aware reader offer accepts mandatory shared-key suite and ignores PK-only offer`() {
        val selectedMethod = DeviceRetrievalMethod.WifiAware(
            passphraseInfo = "12345678",
            supportedBands = byteArrayOf(0x04),
        )
        val selectedCarrier = NfcMdocCarrierCodec.encode(
            selectedMethod,
            ImmutableBytes.of("W".encodeToByteArray()),
            emptyList(),
            NfcMdocActor.HOLDER,
        )
        val payload = selectedCarrier.carrierRecord.payload.copy()
        val readerCarrier = selectedCarrier.copy(
            carrierRecord = selectedCarrier.carrierRecord.copy(
                payload = ImmutableBytes.of(payload.copyOfRange(0, 3) + payload.copyOfRange(13, payload.size)),
            ),
        )
        val parsed = NfcHandoverCodec.validateRequest(NfcHandoverCodec.encodeRequest(listOf(readerCarrier)))
        assertEquals(
            ReaderSelectedTransportOffer.Method(
                DeviceRetrievalMethod.WifiAware(
                    passphraseInfo = null,
                    supportedBands = byteArrayOf(0x04),
                ),
            ),
            NfcMdocCarrierCodec.decodeReaderOffer(parsed.carriers.single()),
        )

        val pkOnlyCarrier = readerCarrier.copy(
            carrierRecord = readerCarrier.carrierRecord.copy(
                payload = ImmutableBytes.of(readerCarrier.carrierRecord.payload.copy().also { it[2] = 2 }),
            ),
        )
        val pkOnly = NfcHandoverCodec.validateRequest(NfcHandoverCodec.encodeRequest(listOf(pkOnlyCarrier)))
        assertEquals(null, NfcMdocCarrierCodec.decodeReaderOffer(pkOnly.carriers.single()))

        val requestWithSelectedPassphrase = NfcHandoverCodec.validateRequest(
            NfcHandoverCodec.encodeRequest(listOf(selectedCarrier)),
        )
        assertFailsWith<IllegalArgumentException> {
            NfcMdocCarrierCodec.decodeReaderOffer(requestWithSelectedPassphrase.carriers.single())
        }
    }

    @Test
    fun `Wi-Fi Aware selected carrier rejects missing shared-key passphrase`() {
        val carrier = NfcMdocCarrierCodec.encode(
            DeviceRetrievalMethod.WifiAware("12345678", supportedBands = byteArrayOf(0x04)),
            ImmutableBytes.of("W".encodeToByteArray()),
            emptyList(),
            NfcMdocActor.HOLDER,
        )
        val payload = carrier.carrierRecord.payload.copy()
        val withoutPassphrase = carrier.copy(
            carrierRecord = carrier.carrierRecord.copy(
                payload = ImmutableBytes.of(payload.copyOfRange(0, 3) + payload.copyOfRange(13, payload.size)),
            ),
        )
        val parsed = NfcHandoverCodec.validateSelect(NfcHandoverCodec.encodeSelect(listOf(withoutPassphrase)))

        assertFailsWith<IllegalArgumentException> {
            NfcMdocCarrierCodec.decode(parsed.carriers.single(), NfcMdocActor.HOLDER)
        }
    }
}
