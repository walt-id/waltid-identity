@file:OptIn(
    kotlinx.coroutines.ExperimentalCoroutinesApi::class,
    kotlinx.serialization.ExperimentalSerializationApi::class,
    kotlin.ExperimentalUnsignedTypes::class,
)

package id.walt.mdoc.proximity.mobile

import id.walt.cose.coseCompliantCbor
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.mdoc.objects.engagement.BleCentralMode
import id.walt.mdoc.objects.engagement.BlePeripheralEndpoint
import id.walt.mdoc.objects.engagement.BlePeripheralServerOptions
import id.walt.mdoc.objects.engagement.DeviceRetrievalMethod
import id.walt.mdoc.objects.engagement.DeviceRetrievalMethodCodec
import id.walt.mdoc.proximity.EngagementContext
import id.walt.mdoc.proximity.FakeProximityLoopback
import id.walt.mdoc.proximity.FakeTransportProvider
import id.walt.mdoc.proximity.ImmutableBytes
import id.walt.mdoc.proximity.MdocEngagementMode
import id.walt.mdoc.proximity.MdocEngagementPreparationContext
import id.walt.mdoc.proximity.MdocProximityLimits
import id.walt.mdoc.proximity.MdocProximityProfile
import id.walt.mdoc.proximity.MdocSessionCapabilities
import id.walt.mdoc.proximity.MdocSessionHandover
import id.walt.mdoc.proximity.PreparedTransport
import id.walt.mdoc.proximity.ProximityCapability
import id.walt.mdoc.proximity.ProximityCloseReason
import id.walt.mdoc.proximity.ProximityError
import id.walt.mdoc.proximity.ProximityException
import id.walt.mdoc.proximity.ProximityTransportKind
import id.walt.mdoc.proximity.ReaderSelectedTransportOffer
import id.walt.mdoc.proximity.ReaderSelectedTransportProvider
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
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
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertFailsWith

class NfcMdocEngagementSourceTest {
    @Test
    fun `NFC processors use the narrower protocol session-message limit`() = runTest {
        withKey { key ->
            val platform = FakeNfcPlatform()
            val prepared = NfcMdocEngagementSource(
                NfcMdocEngagementConfiguration(NfcMdocEngagementScope.QrOnly),
                platform,
                alternateTransportProviders = emptyList(),
            ).prepare(
                context(
                    key = key,
                    limits = MdocProximityLimits(maximumSessionMessageBytes = 16),
                    maximumMessageBytes = 4_096,
                ),
                this,
            )

            assertStatus(NfcStatusWord.SUCCESS, platform.router.process(select(MdocNfcAid.DATA_TRANSFER)))
            assertStatus(
                NfcStatusWord.WRONG_DATA,
                platform.router.process(envelope(NfcDo53.encode(ByteArray(17)))),
            )

            prepared.close(ProximityCloseReason.CANCELLED)
        }
    }

    @Test
    fun `source applies the configured handover limit to negotiated NDEF`() = runTest {
        withKey { key ->
            val platform = FakeNfcPlatform()
            val source = NfcMdocEngagementSource(
                NfcMdocEngagementConfiguration(
                    NfcMdocEngagementScope.NfcOnly(NfcMdocEngagementProfile.Negotiated),
                ),
                platform,
                alternateTransportProviders = emptyList(),
            )
            val prepared = source.prepare(
                context(
                    key,
                    MdocProximityLimits(
                        maximumEngagementOrHandoverBytes = 512,
                        maximumSessionMessageBytes = 4096,
                    ),
                ),
                this,
            )

            assertStatus(NfcStatusWord.SUCCESS, platform.router.process(select(MdocNfcAid.NDEF_APPLICATION)))
            assertStatus(NfcStatusWord.SUCCESS, platform.router.process(selectFile(0xe104)))
            val file = NfcResponseApdu.decode(platform.router.process(readBinary(0, 65_536)).copy()).data.copy()
            val message = NdefMessage.decode(file.copyOfRange(2, file.size))
            val parameters = assertNotNull(NfcTnepCodec.parseServiceParameter(message.records.single()))

            assertEquals(512u.toUShort(), parameters.maximumNdefSize)
            prepared.close(ProximityCloseReason.CANCELLED)
        }
    }

    @Test
    fun `field loss fails a pending NFC engagement immediately`() = runTest {
        withKey { key ->
            val platform = FakeNfcPlatform()
            val prepared = NfcMdocEngagementSource(
                NfcMdocEngagementConfiguration(
                    NfcMdocEngagementScope.NfcOnly(NfcMdocEngagementProfile.Static)
                ),
                platform,
                emptyList(),
            ).prepare(context(key), this)
            supervisorScope {
                val selection = async { prepared.awaitConnection() }

                platform.router.deactivate(ProximityCloseReason.PEER_DISCONNECTED)

                val failure = assertFailsWith<ProximityException> { selection.await() }
                assertEquals("nfc_engagement_deactivated", failure.error.code)
            }
            prepared.close(ProximityCloseReason.CANCELLED)
        }
    }

    @Test
    fun `field loss releases direct NFC retrieval after static handover`() = runTest {
        withKey { key ->
            val platform = FakeNfcPlatform()
            val prepared = NfcMdocEngagementSource(
                NfcMdocEngagementConfiguration(
                    NfcMdocEngagementScope.NfcOnly(NfcMdocEngagementProfile.Static)
                ),
                platform,
                emptyList(),
            ).prepare(context(key), this)
            supervisorScope {
                val selection = async { prepared.awaitConnection() }

                assertStatus(NfcStatusWord.SUCCESS, platform.router.process(select(MdocNfcAid.NDEF_APPLICATION)))
                assertStatus(NfcStatusWord.SUCCESS, platform.router.process(selectFile(0xe104)))
                platform.router.process(readBinary(0, 65_536))
                platform.router.deactivate(ProximityCloseReason.PEER_DISCONNECTED)

                val failure = assertFailsWith<ProximityException> { selection.await() }
                assertEquals("connection_failed", failure.error.code)
            }
            prepared.close(ProximityCloseReason.CANCELLED)
        }
    }

    @Test
    fun `combined source routes a direct retrieval selection without NDEF to the QR transcript`() = runTest {
        withKey { key ->
            val platform = FakeNfcPlatform()
            val source = NfcMdocEngagementSource(
                NfcMdocEngagementConfiguration(
                    NfcMdocEngagementScope.QrAndNfc(NfcMdocEngagementProfile.Static)
                ),
                platform,
                alternateTransportProviders = emptyList(),
                qrTransportProviders = emptyList(),
            )
            val prepared = source.prepare(context(key), this)
            assertEquals(setOf(MdocEngagementMode.Qr, MdocEngagementMode.Nfc), prepared.modes)
            assertNotNull(prepared.readiness.qrPayload)

            val selection = async { prepared.awaitConnection() }
            assertStatus(NfcStatusWord.SUCCESS, platform.router.process(select(MdocNfcAid.DATA_TRANSFER)))
            assertStatus(
                NfcStatusWord.CONDITIONS_NOT_SATISFIED,
                platform.router.process(select(MdocNfcAid.NDEF_APPLICATION)),
            )
            val engaged = selection.await()

            assertEquals(MdocEngagementMode.Qr, engaged.engagementMode)
            assertEquals(MdocSessionHandover.Qr, engaged.sessionHandover)
            assertEquals(platform.router.retrievalConnection, engaged.connection)
            assertEquals(emptyList(), platform.closeReasons)
            prepared.close(ProximityCloseReason.COMPLETED)
            assertEquals(listOf(ProximityCloseReason.COMPLETED), platform.closeReasons)
        }
    }

    @Test
    fun `readiness reports a transport available when any prepared path can use it`() = runTest {
        withKey { key ->
            val method = DeviceRetrievalMethod.Ble(
                centralMode = BleCentralMode(
                    "1234567812344abc92341234567890ab".hexToByteArray(),
                ),
            )
            val availableLoopback = FakeProximityLoopback.create()
            val unavailableLoopback = FakeProximityLoopback.create()
            val unavailable = ProximityError.Capability(
                "fake_runtime_unavailable",
                "The fake transport is unavailable on the NFC engagement path",
            )
            val prepared = NfcMdocEngagementSource(
                NfcMdocEngagementConfiguration(
                    NfcMdocEngagementScope.QrAndNfc(NfcMdocEngagementProfile.Static),
                ),
                FakeNfcPlatform(),
                alternateTransportProviders = listOf(
                    FakeTransportProvider(
                        method,
                        unavailableLoopback.holder,
                        ProximityCapability(
                            implemented = true,
                            profilePermitted = true,
                            runtimeAvailable = false,
                            unavailableReason = unavailable,
                        ),
                    )
                ),
                qrTransportProviders = listOf(
                    FakeTransportProvider(method, availableLoopback.holder),
                ),
            ).prepare(context(key), this)

            assertEquals(
                setOf(ProximityTransportKind.NFC, ProximityTransportKind.FAKE),
                prepared.readiness.availableTransports,
            )
            assertNull(prepared.readiness.unavailableTransports[ProximityTransportKind.FAKE])

            prepared.close(ProximityCloseReason.CANCELLED)
            assertNull(availableLoopback.reader.receive())
        }
    }

    @Test
    fun `combined source routes retrieval after NDEF handover to the NFC transcript`() = runTest {
        withKey { key ->
            val platform = FakeNfcPlatform()
            val source = NfcMdocEngagementSource(
                NfcMdocEngagementConfiguration(
                    NfcMdocEngagementScope.QrAndNfc(NfcMdocEngagementProfile.Static)
                ),
                platform,
                alternateTransportProviders = emptyList(),
                qrTransportProviders = emptyList(),
            )
            val prepared = source.prepare(context(key), this)
            val selection = async { prepared.awaitConnection() }

            assertStatus(NfcStatusWord.SUCCESS, platform.router.process(select(MdocNfcAid.NDEF_APPLICATION)))
            assertStatus(NfcStatusWord.SUCCESS, platform.router.process(selectFile(0xe104)))
            val ndefFile = NfcResponseApdu.decode(
                platform.router.process(readBinary(0, 65_536)).copy()
            ).data.copy()
            val exactSelect = ndefFile.copyOfRange(2, ndefFile.size)
            assertStatus(NfcStatusWord.SUCCESS, platform.router.process(select(MdocNfcAid.DATA_TRANSFER)))
            val engaged = selection.await()

            assertEquals(MdocEngagementMode.Nfc, engaged.engagementMode)
            assertContentEquals(
                exactSelect,
                assertIs<MdocSessionHandover.NfcConnection>(engaged.sessionHandover).handoverSelect.copy(),
            )
            assertEquals(emptyList(), platform.closeReasons)
            prepared.close(ProximityCloseReason.COMPLETED)
            assertEquals(listOf(ProximityCloseReason.COMPLETED), platform.closeReasons)
        }
    }

    @Test
    fun `static handover owns exact Hs and selects same-field conventional retrieval`() = runTest {
        withKey { key ->
            val platform = FakeNfcPlatform()
            val source = NfcMdocEngagementSource(
                NfcMdocEngagementConfiguration(
                    NfcMdocEngagementScope.NfcOnly(NfcMdocEngagementProfile.Static)
                ),
                platform,
                emptyList(),
            )
            val prepared = source.prepare(context(key), this)
            val router = platform.router
            val selection = async { prepared.awaitConnection() }

            assertStatus(NfcStatusWord.SUCCESS, router.process(select(MdocNfcAid.NDEF_APPLICATION)))
            assertStatus(NfcStatusWord.SUCCESS, router.process(selectFile(0xe104)))
            val ndefFile = NfcResponseApdu.decode(router.process(readBinary(0, 65_536)).copy()).data.copy()
            val handoverSelect = ndefFile.copyOfRange(2, ndefFile.size)
            assertStatus(NfcStatusWord.SUCCESS, router.process(select(MdocNfcAid.DATA_TRANSFER)))

            val engaged = selection.await()
            val handover = assertIs<MdocSessionHandover.NfcConnection>(engaged.sessionHandover)
            assertContentEquals(handoverSelect, handover.handoverSelect.copy())
            assertEquals(null, handover.handoverRequest)
            assertEquals(router.retrievalConnection, engaged.connection)
            prepared.close(ProximityCloseReason.COMPLETED)
        }
    }

    @Test
    fun `NFCv2 selects its distinct method and retains exact CBOR handover bytes`() = runTest {
        withKey { key ->
            val platform = FakeNfcPlatform()
            val source = NfcMdocEngagementSource(
                NfcMdocEngagementConfiguration(
                    NfcMdocEngagementScope.NfcOnly(
                        NfcMdocEngagementProfile.ProvisionalV2(NfcV2MaximumCommandDataLength(65_536))
                    )
                ),
                platform,
                emptyList(),
            )
            val prepared = source.prepare(context(key), this)
            val router = platform.router
            assertStatus(NfcStatusWord.SUCCESS, router.process(select(MdocNfcAid.NFC_V2)))
            val exactRequest = v2Request()
            val response = router.process(envelope(NfcDo53.encode(exactRequest)))

            val engaged = prepared.awaitConnection()
            val handover = assertIs<MdocSessionHandover.ProvisionalNfcV2>(engaged.sessionHandover)
            assertContentEquals(exactRequest, handover.handoverRequest.copy())
            val exactSelect = NfcDo53.decode(
                NfcResponseApdu.decode(response.copy()).data.copy(),
                maximumSessionMessageBytes = 4096,
            )
            assertContentEquals(exactSelect, handover.handoverSelect.copy())
            val selectMap = coseCompliantCbor.decodeFromByteArray<CborMap>(exactSelect)
            val engagement = selectMap[CborInteger(0)] as CborMap
            val methods = engagement[CborInteger(2)] as CborArray
            assertEquals(1, methods.size)
            assertEquals(
                DeviceRetrievalMethod.NfcV2,
                DeviceRetrievalMethodCodec.decode(
                    coseCompliantCbor.encodeToByteArray(CborElement.serializer(), methods.single())
                ),
            )
            assertEquals(router.nfcV2Connection, engaged.connection)
            prepared.close(ProximityCloseReason.COMPLETED)
        }
    }

    @Test
    fun `NFCv2 prepares a reader-selected alternate and binds its exact method into handover`() = runTest {
        withKey { key ->
            val readerMethod = DeviceRetrievalMethod.Ble(
                centralMode = BleCentralMode(
                    "e4eaff772b042453451a6c2abf52f590".hexToByteArray(),
                ),
                peripheralEndpoint = BlePeripheralEndpoint.Reader(
                    BlePeripheralServerOptions(psm = 0x81u),
                ),
            )
            val loopback = FakeProximityLoopback.create()
            val platform = FakeNfcPlatform()
            val source = NfcMdocEngagementSource(
                NfcMdocEngagementConfiguration(
                    NfcMdocEngagementScope.NfcOnly(
                        NfcMdocEngagementProfile.ProvisionalV2(NfcV2MaximumCommandDataLength(65_536))
                    )
                ),
                platform,
                listOf(FakeTransportProvider(readerMethod, loopback.holder)),
            )
            val prepared = source.prepare(context(key), this)
            assertEquals(
                setOf(ProximityTransportKind.NFC, ProximityTransportKind.FAKE),
                prepared.readiness.availableTransports,
            )

            assertStatus(NfcStatusWord.SUCCESS, platform.router.process(select(MdocNfcAid.NFC_V2)))
            val exactRequest = v2Request(DeviceRetrievalMethod.NfcV2, readerMethod)
            val response = platform.router.process(envelope(NfcDo53.encode(exactRequest)))
            val engaged = prepared.awaitConnection()

            assertEquals(ProximityTransportKind.FAKE, engaged.connection.kind)
            assertEquals(emptyList(), platform.closeReasons)
            val handover = assertIs<MdocSessionHandover.ProvisionalNfcV2>(engaged.sessionHandover)
            assertContentEquals(exactRequest, handover.handoverRequest.copy())
            val exactSelect = NfcDo53.decode(
                NfcResponseApdu.decode(response.copy()).data.copy(),
                maximumSessionMessageBytes = 4096,
            )
            val selectMap = coseCompliantCbor.decodeFromByteArray<CborMap>(exactSelect)
            val engagement = selectMap[CborInteger(0)] as CborMap
            val methods = engagement[CborInteger(2)] as CborArray
            assertEquals(
                readerMethod,
                DeviceRetrievalMethodCodec.decode(
                    coseCompliantCbor.encodeToByteArray(CborElement.serializer(), methods.single())
                ),
            )

            val firstRequest = byteArrayOf(1, 2, 3)
            val firstNfcExchange = async {
                platform.router.process(envelope(NfcDo53.encode(firstRequest)))
            }
            assertContentEquals(firstRequest, engaged.connection.receive()!!.copy())
            val firstResponse = ImmutableBytes.of(byteArrayOf(4, 5, 6))
            engaged.connection.send(firstResponse)
            assertContentEquals(
                firstResponse.copy(),
                NfcDo53.decode(
                    NfcResponseApdu.decode(firstNfcExchange.await().copy()).data.copy(),
                    maximumSessionMessageBytes = 4096,
                ),
            )
            assertEquals(firstResponse, loopback.reader.receive())

            loopback.reader.send(ImmutableBytes.of(firstRequest))
            val secondRequest = ImmutableBytes.of(byteArrayOf(7, 8, 9))
            val next = async { engaged.connection.receive() }
            loopback.reader.send(secondRequest)
            assertEquals(secondRequest, next.await())

            val secondResponse = ImmutableBytes.of(byteArrayOf(10, 11))
            engaged.connection.send(secondResponse)
            assertEquals(secondResponse, loopback.reader.receive())
            val duplicateNfcExchange = async {
                platform.router.process(envelope(NfcDo53.encode(secondRequest.copy())))
            }
            assertContentEquals(
                secondResponse.copy(),
                NfcDo53.decode(
                    NfcResponseApdu.decode(duplicateNfcExchange.await().copy()).data.copy(),
                    maximumSessionMessageBytes = 4096,
                ),
            )

            prepared.close(ProximityCloseReason.COMPLETED)
            assertEquals(
                listOf(ProximityCloseReason.COMPLETED),
                platform.closeReasons,
            )
        }
    }

    @Test
    fun `negotiated handover prepares only the reader-selected bearer and retains exact Hs and Hr`() = runTest {
        withKey { key ->
            val readerMethod = DeviceRetrievalMethod.Ble(
                centralMode = BleCentralMode(
                    "e4eaff772b042453451a6c2abf52f590".hexToByteArray(),
                ),
                peripheralEndpoint = BlePeripheralEndpoint.Reader(
                    BlePeripheralServerOptions(psm = 0x81u),
                ),
            )
            val loopback = FakeProximityLoopback.create()
            val platform = FakeNfcPlatform()
            val source = NfcMdocEngagementSource(
                NfcMdocEngagementConfiguration(
                    NfcMdocEngagementScope.NfcOnly(NfcMdocEngagementProfile.Negotiated),
                    conventionalRetrieval = null,
                ),
                platform,
                listOf(FakeTransportProvider(readerMethod, loopback.holder)),
            )
            val prepared = source.prepare(context(key), this)
            val router = platform.router
            val selection = async { prepared.awaitConnection() }
            val readerCarrier = NfcMdocCarrierCodec.encode(
                readerMethod,
                ImmutableBytes.of("0".encodeToByteArray()),
                emptyList(),
                NfcMdocActor.READER,
            )
            val exactRequest = NfcHandoverCodec.encodeRequest(listOf(readerCarrier))

            assertStatus(NfcStatusWord.SUCCESS, router.process(select(MdocNfcAid.NDEF_APPLICATION)))
            assertStatus(NfcStatusWord.SUCCESS, router.process(selectFile(0xe104)))
            assertStatus(NfcStatusWord.SUCCESS, router.process(updateBinary(0, withNlen(serviceSelect()))))
            assertStatus(NfcStatusWord.SUCCESS, router.process(updateBinary(0, withNlen(exactRequest))))
            val staged = NfcResponseApdu.decode(router.process(readBinary(0, 65_536)).copy()).data.copy()
            val exactSelect = staged.copyOfRange(2, staged.size)
            val engaged = selection.await()

            assertEquals(loopback.holder, engaged.connection)
            assertEquals(
                listOf(ProximityCloseReason.HANDOVER_COMPLETED),
                platform.closeReasons,
            )
            val handover = assertIs<MdocSessionHandover.NfcConnection>(engaged.sessionHandover)
            assertContentEquals(exactRequest, handover.handoverRequest!!.copy())
            assertContentEquals(exactSelect, handover.handoverSelect.copy())
            val selectedCarrier = NfcHandoverCodec.validateSelect(exactSelect).carriers.single()
            assertFailsWith<IllegalArgumentException> {
                NfcMdocCarrierCodec.decode(selectedCarrier, NfcMdocActor.HOLDER)
            }
            assertEquals(
                readerMethod,
                NfcMdocCarrierCodec.decode(
                    selectedCarrier,
                    NfcMdocActor.HOLDER,
                    readerMethod.centralMode!!.uuid,
                ),
            )
            prepared.close(ProximityCloseReason.COMPLETED)
            assertEquals(
                listOf(ProximityCloseReason.HANDOVER_COMPLETED),
                platform.closeReasons,
            )
        }
    }

    @Test
    fun `negotiated handover bounds alternate preparation and falls back to offered NFC retrieval`() = runTest {
        withKey { key ->
            val readerMethod = DeviceRetrievalMethod.Ble(
                centralMode = BleCentralMode(
                    "1234567812344abc92341234567890ab".hexToByteArray(),
                ),
            )
            val platform = FakeNfcPlatform()
            val source = NfcMdocEngagementSource(
                NfcMdocEngagementConfiguration(
                    NfcMdocEngagementScope.NfcOnly(NfcMdocEngagementProfile.Negotiated),
                ),
                platform,
                listOf(HangingReaderSelectedProvider(readerMethod)),
            )
            val prepared = source.prepare(context(key), this)
            val selection = async { prepared.awaitConnection() }
            val exactRequest = NfcHandoverCodec.encodeRequest(
                listOf(
                    NfcMdocCarrierCodec.encode(
                        readerMethod,
                        ImmutableBytes.of("0".encodeToByteArray()),
                        emptyList(),
                        NfcMdocActor.READER,
                    ),
                    NfcMdocCarrierCodec.encode(
                        DeviceRetrievalMethod.Nfc(65_535u, 65_536u),
                        ImmutableBytes.of("nfc".encodeToByteArray()),
                        emptyList(),
                        NfcMdocActor.READER,
                    ),
                )
            )

            assertStatus(NfcStatusWord.SUCCESS, platform.router.process(select(MdocNfcAid.NDEF_APPLICATION)))
            assertStatus(NfcStatusWord.SUCCESS, platform.router.process(selectFile(0xe104)))
            assertStatus(NfcStatusWord.SUCCESS, platform.router.process(updateBinary(0, withNlen(serviceSelect()))))
            val beforeSelection = testScheduler.currentTime
            assertStatus(NfcStatusWord.SUCCESS, platform.router.process(updateBinary(0, withNlen(exactRequest))))
            assertEquals(2_000, testScheduler.currentTime - beforeSelection)
            assertStatus(NfcStatusWord.SUCCESS, platform.router.process(select(MdocNfcAid.DATA_TRANSFER)))

            val engaged = selection.await()
            assertEquals(ProximityTransportKind.NFC, engaged.connection.kind)
            val selected = NfcHandoverCodec.validateSelect(
                assertIs<MdocSessionHandover.NfcConnection>(engaged.sessionHandover).handoverSelect.copy()
            )
            assertEquals(
                DeviceRetrievalMethod.Nfc(65_535u, 65_536u),
                NfcMdocCarrierCodec.decode(selected.carriers.single(), NfcMdocActor.HOLDER),
            )
            prepared.close(ProximityCloseReason.COMPLETED)
        }
    }

    @Test
    fun `NFCv2 bounds alternate preparation and retains its same-channel fallback`() = runTest {
        withKey { key ->
            val readerMethod = DeviceRetrievalMethod.Ble(
                centralMode = BleCentralMode(
                    "1234567812344abc92341234567890ab".hexToByteArray(),
                ),
            )
            val platform = FakeNfcPlatform()
            val source = NfcMdocEngagementSource(
                NfcMdocEngagementConfiguration(
                    NfcMdocEngagementScope.NfcOnly(
                        NfcMdocEngagementProfile.ProvisionalV2(NfcV2MaximumCommandDataLength(65_536))
                    )
                ),
                platform,
                listOf(HangingReaderSelectedProvider(readerMethod)),
            )
            val prepared = source.prepare(context(key), this)
            assertStatus(NfcStatusWord.SUCCESS, platform.router.process(select(MdocNfcAid.NFC_V2)))
            val beforeSelection = testScheduler.currentTime
            val response = platform.router.process(
                envelope(NfcDo53.encode(v2Request(DeviceRetrievalMethod.NfcV2, readerMethod)))
            )
            assertEquals(2_000, testScheduler.currentTime - beforeSelection)

            val engaged = prepared.awaitConnection()
            assertEquals(platform.router.nfcV2Connection, engaged.connection)
            val exactSelect = NfcDo53.decode(
                NfcResponseApdu.decode(response.copy()).data.copy(),
                maximumSessionMessageBytes = 4096,
            )
            val selectMap = coseCompliantCbor.decodeFromByteArray<CborMap>(exactSelect)
            val engagement = selectMap[CborInteger(0)] as CborMap
            val methods = engagement[CborInteger(2)] as CborArray
            assertEquals(
                DeviceRetrievalMethod.NfcV2,
                DeviceRetrievalMethodCodec.decode(
                    coseCompliantCbor.encodeToByteArray(CborElement.serializer(), methods.single())
                ),
            )
            prepared.close(ProximityCloseReason.COMPLETED)
        }
    }

    @Test
    fun `platform capability and preparation failures are normalized without raw diagnostics`() = runTest {
        withKey { key ->
            val configuration = NfcMdocEngagementConfiguration(
                NfcMdocEngagementScope.NfcOnly(NfcMdocEngagementProfile.Static)
            )
            val capabilityFailure = assertFailsWith<ProximityException> {
                NfcMdocEngagementSource(
                    configuration,
                    FakeNfcPlatform(capabilityFailure = IllegalStateException("private capability detail")),
                    emptyList(),
                ).prepare(context(key), this)
            }
            assertEquals("nfc_capability_check_failed", capabilityFailure.error.code)
            assertEquals(
                "NFC host-card presentation capability could not be determined",
                capabilityFailure.error.message,
            )

            val preparationFailure = assertFailsWith<ProximityException> {
                NfcMdocEngagementSource(
                    configuration,
                    FakeNfcPlatform(prepareFailure = IllegalStateException("private preparation detail")),
                    emptyList(),
                ).prepare(context(key), this)
            }
            assertEquals("nfc_host_prepare_failed", preparationFailure.error.code)
            assertEquals(
                "NFC host-card presentation could not be prepared",
                preparationFailure.error.message,
            )

            val unavailablePreparation = assertFailsWith<ProximityException> {
                NfcMdocEngagementSource(
                    configuration,
                    FakeNfcPlatform(
                        prepareUnavailable = NfcHostAvailability.Unavailable(
                            "nfc_session_already_active",
                            "An NFC card presentation is already active",
                        )
                    ),
                    emptyList(),
                ).prepare(context(key), this)
            }
            assertEquals("nfc_session_already_active", unavailablePreparation.error.code)
            assertEquals("An NFC card presentation is already active", unavailablePreparation.error.message)

            val loopback = FakeProximityLoopback.create()
            assertFailsWith<ProximityException> {
                NfcMdocEngagementSource(
                    configuration,
                    FakeNfcPlatform(prepareFailure = IllegalStateException("private preparation detail")),
                    listOf(
                        FakeTransportProvider(
                            DeviceRetrievalMethod.Ble(
                                centralMode = BleCentralMode(
                                    "1234567812344abc92341234567890ab".hexToByteArray(),
                                )
                            ),
                            loopback.holder,
                        )
                    ),
                ).prepare(context(key), this)
            }
            assertNull(loopback.reader.receive())
        }
    }

    private suspend fun <T> withKey(block: suspend (id.walt.crypto2.keys.Key) -> T): T {
        val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
        val key = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                KeyId("nfc-engagement-test"),
                KeySpec.Ec(EcCurve.P256),
                setOf(KeyUsage.KEY_AGREEMENT),
            )
        )
        return try {
            block(key)
        } finally {
            key.capabilities.deleter?.delete()
            runtime.close()
        }
    }

    private fun context(
        key: id.walt.crypto2.keys.Key,
        limits: MdocProximityLimits = MdocProximityLimits(maximumSessionMessageBytes = 4096),
        maximumMessageBytes: Int = 4096,
    ): MdocEngagementPreparationContext {
        val profile = MdocProximityProfile.ISO_18013_5_ED2_DIS_2026
        return MdocEngagementPreparationContext(
            key,
            EngagementContext(profile, maximumMessageBytes, MdocEngagementMode.Nfc),
            MdocSessionCapabilities.forSession(profile, key, emptySet()),
            limits,
        )
    }

    private fun v2Request(vararg methods: DeviceRetrievalMethod): ByteArray {
        val requested = methods.takeIf { it.isNotEmpty() } ?: arrayOf(DeviceRetrievalMethod.NfcV2)
        val methodElements = requested.map { method ->
            coseCompliantCbor.decodeFromByteArray<CborElement>(
                DeviceRetrievalMethodCodec.encodeReaderEngagement(method),
            )
        }
        return coseCompliantCbor.encodeToByteArray(
            CborElement.serializer(),
            CborMap(
                mapOf(
                    CborInteger(0) to CborMap(
                        mapOf(CborInteger(2) to CborArray(methodElements))
                    )
                )
            ),
        )
    }

    private fun serviceSelect(): ByteArray = NdefMessage(
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

    private fun withNlen(message: ByteArray): ByteArray =
        byteArrayOf((message.size ushr 8).toByte(), message.size.toByte()) + message

    private fun select(aid: ImmutableBytes): ByteArray = NfcCommandApdu(
        0u,
        0xa4u,
        0x04u,
        if (aid == MdocNfcAid.DATA_TRANSFER) 0x0cu else 0u,
        aid,
    ).encode()

    private fun selectFile(identifier: Int): ByteArray = NfcCommandApdu(
        0u,
        0xa4u,
        0u,
        0x0cu,
        ImmutableBytes.of(byteArrayOf((identifier ushr 8).toByte(), identifier.toByte())),
    ).encode()

    private fun readBinary(offset: Int, length: Int): ByteArray = NfcCommandApdu(
        0u, 0xb0u, (offset ushr 8).toUByte(), offset.toUByte(), expectedResponseDataLength = length,
    ).encode()

    private fun updateBinary(offset: Int, data: ByteArray): ByteArray = NfcCommandApdu(
        0u, 0xd6u, (offset ushr 8).toUByte(), offset.toUByte(), ImmutableBytes.of(data),
    ).encode()

    private fun envelope(payload: ByteArray): ByteArray = NfcCommandApdu(
        0u, 0xc3u, 0u, 0u, ImmutableBytes.of(payload), expectedResponseDataLength = 65_536,
    ).encode()

    private fun assertStatus(expected: UShort, response: ImmutableBytes) {
        assertEquals(expected, NfcResponseApdu.decode(response.copy()).statusWord)
    }

    private class FakeNfcPlatform(
        private val capabilityFailure: Throwable? = null,
        private val prepareFailure: Throwable? = null,
        private val prepareUnavailable: NfcHostAvailability.Unavailable? = null,
    ) : NfcHostPlatformAdapter {
        lateinit var router: NfcHostApduRouter
        val closeReasons = mutableListOf<ProximityCloseReason>()
        override suspend fun capability(): NfcHostAvailability {
            capabilityFailure?.let { throw it }
            return NfcHostAvailability.Available
        }

        override suspend fun prepare(
            router: NfcHostApduRouter,
            sessionScope: CoroutineScope,
        ): NfcHostPreparation {
            prepareFailure?.let { throw it }
            prepareUnavailable?.let { return NfcHostPreparation.Unavailable(it) }
            this.router = router
            return NfcHostPreparation.Ready(
                object : PreparedNfcHostSession {
                    private var closed = false

                    override suspend fun close(reason: ProximityCloseReason) {
                        if (closed) return
                        closed = true
                        closeReasons += reason
                    }
                }
            )
        }
    }

    private class HangingReaderSelectedProvider(
        private val method: DeviceRetrievalMethod,
    ) : ReaderSelectedTransportProvider {
        override val kind: ProximityTransportKind = ProximityTransportKind.FAKE

        override suspend fun capability(context: EngagementContext): ProximityCapability =
            ProximityCapability(true, true, true, sessionSelected = true)

        override suspend fun prepare(
            context: EngagementContext,
            sessionScope: CoroutineScope,
        ): PreparedTransport = awaitCancellation()

        override fun acceptsReaderOffer(offer: ReaderSelectedTransportOffer): Boolean =
            offer is ReaderSelectedTransportOffer.Method && offer.value == method

        override suspend fun prepareReaderSelected(
            offer: ReaderSelectedTransportOffer,
            context: EngagementContext,
            sessionScope: CoroutineScope,
        ): PreparedTransport = awaitCancellation()
    }
}
