@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.wallet2.mobile

import id.walt.mdoc.objects.engagement.BleCentralMode
import id.walt.mdoc.objects.engagement.BlePeripheralMode
import id.walt.mdoc.objects.engagement.DeviceRetrievalMethod
import id.walt.mdoc.proximity.FakeProximityLoopback
import id.walt.mdoc.proximity.FakeTransportProvider
import id.walt.mdoc.proximity.ProximityCloseReason
import id.walt.mdoc.proximity.ReaderSelectedTransportProvider
import id.walt.mdoc.proximity.mobile.BleMdocRoles
import id.walt.mdoc.proximity.mobile.BleMdocRoleSelection
import id.walt.mdoc.proximity.mobile.BleProximityAvailability
import id.walt.mdoc.proximity.mobile.BleProximityTransportConfiguration
import id.walt.mdoc.proximity.mobile.BleProximityTransportFactory
import id.walt.mdoc.proximity.mobile.NfcHostApduRouter
import id.walt.mdoc.proximity.mobile.NfcHostAvailability
import id.walt.mdoc.proximity.mobile.NfcHostPlatformAdapter
import id.walt.mdoc.proximity.mobile.NfcHostPreparation
import id.walt.mdoc.proximity.mobile.PreparedNfcHostSession
import id.walt.wallet2.data.Wallet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class MobileWalletProximityCoordinatorTest {
    @Test
    fun `capability preflight is side effect free and maps remediation`() = runTest {
        val factory = RecordingTransportFactory(
            BleProximityAvailability.Unavailable(
                code = "ble_permission_missing",
                message = "Bluetooth permission is missing",
            )
        )
        val coordinator = MobileWalletProximityCoordinator(Wallet("preflight"), factory)

        val capabilities = coordinator.capabilities(MobileWalletProximityConfiguration())

        assertEquals(1, factory.capabilityCalls)
        assertTrue(factory.configurations.isEmpty())
        assertFalse(capabilities.mayStart)
        assertEquals("ble_permission_missing", capabilities.bluetoothLowEnergy.unavailable?.code)
        assertEquals(
            listOf(MobileWalletProximityRemediationAction.RequestBluetoothPermission),
            capabilities.bluetoothLowEnergy.remediationActions,
        )
    }

    @Test
    fun `NFC capability preflight is side effect free and does not invent entitlement remediation`() = runTest {
        val nfc = RecordingNfcHostAdapter(
            NfcHostAvailability.Unavailable(
                code = "nfc_entitlement_missing",
                message = "The signed application is missing the managed HCE entitlement",
            )
        )
        val coordinator = MobileWalletProximityCoordinator(
            Wallet("nfc-preflight"),
            RecordingTransportFactory(BleProximityAvailability.Available),
            nfc,
        )
        val configuration = MobileWalletProximityConfiguration(
            engagement = MobileWalletProximityEngagementConfiguration.QrAndNfc(
                MobileWalletProximityNfcEngagementMode.Negotiated
            ),
            retrieval = MobileWalletProximityRetrievalConfiguration.Conventional(
                nfc = MobileWalletProximityNfcRetrievalConfiguration(),
            ),
        )

        val capabilities = coordinator.capabilities(configuration)

        assertTrue(capabilities.mayStart)
        assertEquals(1, nfc.capabilityCalls)
        assertEquals(0, nfc.prepareCalls)
        assertFalse(capabilities.nfcEngagement.mayStart)
        assertFalse(capabilities.nfcRetrieval.mayStart)
        assertEquals("nfc_entitlement_missing", capabilities.nfcEngagement.unavailable?.code)
        assertTrue(capabilities.nfcEngagement.remediationActions.isEmpty())
        assertEquals(false, capabilities.nfcEngagement.unavailable?.recoverable)
    }

    @Test
    fun `NFC-only configuration remains blocked when the host is unavailable`() = runTest {
        val nfc = RecordingNfcHostAdapter(
            NfcHostAvailability.Unavailable("nfc_powered_off", "NFC is powered off")
        )
        val coordinator = MobileWalletProximityCoordinator(Wallet("nfc-only-blocked"), null, nfc)
        val session = coordinator.start(
            MobileWalletProximityConfiguration(
                engagement = MobileWalletProximityEngagementConfiguration.NfcOnly(
                    MobileWalletProximityNfcEngagementMode.Static
                ),
                retrieval = MobileWalletProximityRetrievalConfiguration.Conventional(
                    bluetoothLowEnergy = null,
                    nfc = MobileWalletProximityNfcRetrievalConfiguration(),
                ),
            )
        )

        val state = assertIs<MobileWalletProximityState.CheckingPrerequisites>(session.state.value)
        assertFalse(state.capabilities.mayStart)
        assertEquals(
            listOf(MobileWalletProximityRemediationAction.EnableNfc),
            state.capabilities.remediationActions,
        )
        assertEquals(0, nfc.prepareCalls)
        session.close()
    }

    @Test
    fun `NFCv2-only session reports its same-channel retrieval independently of conventional NFC`() = runTest {
        val ble = RecordingTransportFactory(
            BleProximityAvailability.Unavailable("ble_powered_off", "Bluetooth is powered off")
        )
        val nfc = RecordingNfcHostAdapter(NfcHostAvailability.Available)
        val coordinator = MobileWalletProximityCoordinator(Wallet("nfc-v2-capability"), ble, nfc)
        val configuration = MobileWalletProximityConfiguration(
            engagement = MobileWalletProximityEngagementConfiguration.NfcOnly(
                MobileWalletProximityNfcEngagementMode.ProvisionalV2()
            ),
            retrieval = MobileWalletProximityRetrievalConfiguration.ProvisionalNfcV2(),
        )

        val capabilities = coordinator.capabilities(configuration)

        assertTrue(capabilities.mayStart)
        assertTrue(capabilities.nfcEngagement.mayStart)
        assertTrue(capabilities.nfcV2Retrieval.mayStart)
        assertFalse(capabilities.nfcRetrieval.selected)
        assertFalse(capabilities.nfcRetrieval.mayStart)
        assertFalse(capabilities.bluetoothLowEnergy.selected)
        assertFalse(capabilities.bluetoothLowEnergy.mayStart)
        assertEquals(0, ble.capabilityCalls)
    }

    @Test
    fun `QR engagement with NFC retrieval arms one NFC host without exposing NFC engagement`() = runTest {
        val nfc = RecordingNfcHostAdapter(NfcHostAvailability.Available)
        val coordinator = MobileWalletProximityCoordinator(Wallet("qr-nfc"), null, nfc)
        val session = coordinator.start(
            MobileWalletProximityConfiguration(
                engagement = MobileWalletProximityEngagementConfiguration.QrOnly,
                retrieval = MobileWalletProximityRetrievalConfiguration.Conventional(
                    bluetoothLowEnergy = null,
                    nfc = MobileWalletProximityNfcRetrievalConfiguration(),
                ),
            )
        )

        val engagements = session.awaitEngagements()
        assertEquals(1, nfc.prepareCalls)
        assertEquals(1, engagements.size)
        assertIs<MobileWalletProximityEngagement.Qr>(engagements.single())
        session.close()
    }

    @Test
    fun `combined engagement falls back to QR and BLE when NFC is unavailable`() = runTest {
        val ble = RecordingTransportFactory(BleProximityAvailability.Available)
        val nfc = RecordingNfcHostAdapter(
            NfcHostAvailability.Unavailable("nfc_system_ineligible", "NFC HCE is ineligible")
        )
        val coordinator = MobileWalletProximityCoordinator(Wallet("combined-fallback"), ble, nfc)
        val session = coordinator.start(
            MobileWalletProximityConfiguration(
                engagement = MobileWalletProximityEngagementConfiguration.QrAndNfc(
                    MobileWalletProximityNfcEngagementMode.Negotiated
                ),
            )
        )

        val engagements = session.awaitEngagements()
        assertEquals(1, ble.configurations.size)
        assertEquals(0, nfc.prepareCalls)
        assertEquals(1, engagements.size)
        assertIs<MobileWalletProximityEngagement.Qr>(engagements.single())
        session.close()
    }

    @Test
    fun `combined QR and NFC engagement gets distinct BLE transaction configurations`() = runTest {
        val ble = RecordingTransportFactory(BleProximityAvailability.Available)
        val nfc = RecordingNfcHostAdapter(NfcHostAvailability.Available)
        val coordinator = MobileWalletProximityCoordinator(Wallet("combined-uuids"), ble, nfc)
        val session = coordinator.start(
            MobileWalletProximityConfiguration(
                engagement = MobileWalletProximityEngagementConfiguration.QrAndNfc(
                    MobileWalletProximityNfcEngagementMode.ProvisionalV2()
                ),
                retrieval = MobileWalletProximityRetrievalConfiguration.ProvisionalNfcV2(
                    bluetoothLowEnergy = MobileWalletProximityBleConfiguration(),
                ),
            )
        )

        val engagements = session.awaitEngagements()
        assertEquals(2, ble.configurations.size)
        assertEquals(1, nfc.prepareCalls)
        assertEquals(2, engagements.size)
        assertTrue(engagements.any { it is MobileWalletProximityEngagement.Qr })
        assertTrue(engagements.any { it is MobileWalletProximityEngagement.Nfc })
        assertEquals(
            ble.configurations[0].eDeviceKeyBytes,
            ble.configurations[1].eDeviceKeyBytes,
        )
        assertNotEquals(ble.configurations[0].roles, ble.configurations[1].roles)
        session.close()
    }

    @Test
    fun `combined NFCv2 session omits an unusable QR path instead of failing preparation`() = runTest {
        val ble = RecordingTransportFactory(
            BleProximityAvailability.Unavailable("ble_powered_off", "Bluetooth is powered off")
        )
        val nfc = RecordingNfcHostAdapter(NfcHostAvailability.Available)
        val coordinator = MobileWalletProximityCoordinator(Wallet("combined-nfc-v2-only"), ble, nfc)
        val session = coordinator.start(
            MobileWalletProximityConfiguration(
                engagement = MobileWalletProximityEngagementConfiguration.QrAndNfc(
                    MobileWalletProximityNfcEngagementMode.ProvisionalV2()
                ),
                retrieval = MobileWalletProximityRetrievalConfiguration.ProvisionalNfcV2(
                    bluetoothLowEnergy = MobileWalletProximityBleConfiguration(),
                ),
            )
        )

        val engagements = session.awaitEngagements()

        assertEquals(1, nfc.prepareCalls)
        assertEquals(1, engagements.size)
        assertIs<MobileWalletProximityEngagement.Nfc>(engagements.single())
        session.close()
    }

    @Test
    fun `wallet admits one active session and rotates session key and UUIDs`() = runTest {
        val factory = RecordingTransportFactory(BleProximityAvailability.Available)
        val coordinator = MobileWalletProximityCoordinator(Wallet("single-session"), factory)

        val first = coordinator.start(MobileWalletProximityConfiguration())
        first.awaitConnection()
        assertFailsWith<IllegalStateException> {
            coordinator.start(MobileWalletProximityConfiguration())
        }
        first.close()

        val second = coordinator.start(MobileWalletProximityConfiguration())
        second.awaitConnection()
        second.close()

        assertEquals(2, factory.configurations.size)
        val firstConfiguration = factory.configurations[0]
        val secondConfiguration = factory.configurations[1]
        assertFalse(
            firstConfiguration.eDeviceKeyBytes.copy()
                .contentEquals(secondConfiguration.eDeviceKeyBytes.copy())
        )
        val firstRoles = assertIs<BleMdocRoles.Dual>(firstConfiguration.roles)
        val secondRoles = assertIs<BleMdocRoles.Dual>(secondConfiguration.roles)
        assertNotEquals(firstRoles.readerServiceUuid, secondRoles.readerServiceUuid)
        assertNotEquals(firstRoles.mdocServiceUuid, secondRoles.mdocServiceUuid)
        assertNotEquals(firstRoles.readerServiceUuid, firstRoles.mdocServiceUuid)
        assertTrue(firstConfiguration.eDeviceKeyBytes.size > 0)
    }

    @Test
    fun `unavailable session accepts only current remediation and retries without session material`() = runTest {
        val factory = RecordingTransportFactory(
            BleProximityAvailability.Unavailable(
                code = "ble_powered_off",
                message = "Bluetooth is powered off",
            )
        )
        val coordinator = MobileWalletProximityCoordinator(Wallet("remediation"), factory)
        val session = coordinator.start(MobileWalletProximityConfiguration())
        val blocked = assertIs<MobileWalletProximityState.CheckingPrerequisites>(session.state.value)

        assertFalse(blocked.capabilities.mayStart)
        assertTrue(factory.configurations.isEmpty())
        assertIs<MobileWalletProximityActionResult.Rejected>(
            session.dispatch(
                MobileWalletProximityAction.ReportRemediation(
                    MobileWalletProximityRemediationAction.OpenApplicationSettings,
                    MobileWalletProximityHostActionResult.Completed,
                )
            )
        )
        assertEquals(
            MobileWalletProximityActionResult.Accepted,
            session.dispatch(
                MobileWalletProximityAction.ReportRemediation(
                    MobileWalletProximityRemediationAction.EnableBluetooth,
                    MobileWalletProximityHostActionResult.Failed,
                )
            )
        )
        assertTrue(factory.configurations.isEmpty())

        factory.availability = BleProximityAvailability.Available
        assertEquals(
            MobileWalletProximityActionResult.Accepted,
            session.dispatch(
                MobileWalletProximityAction.ReportRemediation(
                    MobileWalletProximityRemediationAction.EnableBluetooth,
                    MobileWalletProximityHostActionResult.Completed,
                )
            )
        )
        session.awaitConnection()
        assertEquals(1, factory.configurations.size)
        session.close()
    }

    private suspend fun MobileWalletProximitySession.awaitConnection() {
        withContext(Dispatchers.Default) {
            withTimeout(5.seconds) {
                state.first { it is MobileWalletProximityState.Connecting }
            }
        }
    }

    private suspend fun MobileWalletProximitySession.awaitEngagements(): List<MobileWalletProximityEngagement> =
        withContext(Dispatchers.Default) {
            withTimeout(5.seconds) {
                when (val current = state.first {
                    it is MobileWalletProximityState.EngagementReady ||
                        it is MobileWalletProximityState.Connecting
                }) {
                    is MobileWalletProximityState.EngagementReady -> current.engagements
                    is MobileWalletProximityState.Connecting -> current.engagements
                    else -> error("Unexpected proximity state $current")
                }
            }
        }

    private class RecordingNfcHostAdapter(
        var availability: NfcHostAvailability,
    ) : NfcHostPlatformAdapter {
        var capabilityCalls: Int = 0
        var prepareCalls: Int = 0
        val routers: MutableList<NfcHostApduRouter> = mutableListOf()

        override suspend fun capability(): NfcHostAvailability {
            capabilityCalls++
            return availability
        }

        override suspend fun prepare(
            router: NfcHostApduRouter,
            sessionScope: CoroutineScope,
        ): NfcHostPreparation {
            prepareCalls++
            routers += router
            return NfcHostPreparation.Ready(
                object : PreparedNfcHostSession {
                    override suspend fun close(reason: ProximityCloseReason) = Unit
                }
            )
        }
    }

    private class RecordingTransportFactory(
        var availability: BleProximityAvailability,
    ) : BleProximityTransportFactory {
        var capabilityCalls: Int = 0
        val configurations: MutableList<BleProximityTransportConfiguration> = mutableListOf()

        override suspend fun capability(roles: BleMdocRoleSelection): BleProximityAvailability {
            capabilityCalls++
            return availability
        }

        override fun create(configuration: BleProximityTransportConfiguration): ReaderSelectedTransportProvider {
            configurations += configuration
            val loopback = FakeProximityLoopback.create()
            return FakeTransportProvider(
                method = when (val roles = configuration.roles) {
                    is BleMdocRoles.CentralClient -> DeviceRetrievalMethod.Ble(
                        centralMode = BleCentralMode(roles.readerServiceUuid.encoded().copy()),
                    )
                    is BleMdocRoles.PeripheralServer -> DeviceRetrievalMethod.Ble(
                        peripheralMode = BlePeripheralMode(roles.mdocServiceUuid.encoded().copy()),
                    )
                    is BleMdocRoles.Dual -> DeviceRetrievalMethod.Ble(
                        centralMode = BleCentralMode(roles.readerServiceUuid.encoded().copy()),
                        peripheralMode = BlePeripheralMode(roles.mdocServiceUuid.encoded().copy()),
                    )
                },
                connection = loopback.holder,
            )
        }
    }
}
