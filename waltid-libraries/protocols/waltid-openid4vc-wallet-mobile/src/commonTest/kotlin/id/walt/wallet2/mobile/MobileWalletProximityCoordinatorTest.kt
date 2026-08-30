@file:OptIn(ExperimentalSerializationApi::class, ExperimentalCoroutinesApi::class)

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
import id.walt.mdoc.proximity.mobile.WifiAwareProximityAvailability
import id.walt.mdoc.proximity.mobile.WifiAwareProximityTransportConfiguration
import id.walt.mdoc.proximity.mobile.WifiAwareProximityTransportFactory
import id.walt.mdoc.proximity.mobile.WifiAwareSecurityPolicy
import id.walt.wallet2.data.Wallet
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import id.walt.mdoc.proximity.ImmutableBytes
import kotlin.time.TestTimeSource
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.ExperimentalSerializationApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ProximityCoordinatorTest {
    @Test
    fun `prepared deadline and revocation close a permission-blocked session and release admission`() = runTest {
        for (cancel in listOf(false, true)) {
            val wallet = Wallet("prepared-blocked-$cancel")
            val factory = RecordingTransportFactory(BleProximityAvailability.Unavailable("ble_powered_off", "Bluetooth is off"))
            val coordinator = ProximityCoordinator(wallet, factory, sessionDispatcher = StandardTestDispatcher(testScheduler))
            val reference = ProximityElementReference("org.iso.18013.5.1", "given_name")
            val review = ProximityReview(ProximityReviewId(Uuid.random().toString()), 1, listOf(
                ProximityDocumentReview(0, "org.iso.18013.5.1.mDL", listOf(ProximityCredentialOption(
                    "credential", "Identity", null, Instant.DISTANT_FUTURE, ProximityDeviceAuthenticationMethod.Signature,
                    listOf(ProximityRequestedElement(reference.namespace, reference.elementIdentifier, false)),
                ))),
            ), emptyList(), emptyList(), emptyList())
            val digest = ImmutableBytes.of(ByteArray(32))
            val time = TestTimeSource()
            val plan = ProximitySharingPlan(wallet, review, ProximityApprovalScope(ProximityProfile.Iso180135Edition2Dis2026,
                "reader", digest, mapOf("credential" to digest), emptyList()), timeSource = time)
            val sharing = assertIs<ProximityPreparationResult.Prepared>(plan.approve(ProximitySubmission(listOf(
                ProximityDocumentSubmission(0, "credential", setOf(reference)),
            )))).sharing
            val session = coordinator.start(ProximityConfiguration(approval = ProximityApproval.Prepared(sharing)))
            runCurrent()
            assertIs<ProximityState.CheckingPrerequisites>(session.state.value)
            if (cancel) sharing.revoke() else {
                time += 60.seconds
                advanceTimeBy(60_000)
            }
            runCurrent()
            assertEquals(if (cancel) "prepared_sharing_cancelled" else "prepared_sharing_expired",
                assertIs<ProximityState.Failed>(session.state.value).error.code)
            assertTrue(factory.configurations.isEmpty())
            val fresh = coordinator.start(ProximityConfiguration())
            fresh.close()
        }
    }

    @Test
    fun `closing or cancelling before the worker starts releases the wallet for a fresh session`() = runTest {
        for (dispatchCancel in listOf(false, true)) {
            val factory = RecordingTransportFactory(
                BleProximityAvailability.Unavailable("ble_powered_off", "Bluetooth is off"),
            )
            val coordinator = ProximityCoordinator(
                Wallet("cancel-before-worker-$dispatchCancel"), factory,
                sessionDispatcher = StandardTestDispatcher(testScheduler),
            )
            val first = coordinator.start(ProximityConfiguration())
            if (dispatchCancel) {
                assertEquals(ProximityActionResult.Accepted, first.dispatch(ProximityAction.Cancel))
            } else {
                first.close()
            }
            assertIs<ProximityState.Cancelled>(first.state.value)
            val restarted = coordinator.start(ProximityConfiguration())
            assertIs<ProximityState.CheckingPrerequisites>(restarted.state.value)
            restarted.close()
            assertTrue(factory.configurations.isEmpty())
        }
    }

    @Test
    fun `capability preflight is side effect free and maps remediation`() = runTest {
        val factory = RecordingTransportFactory(
            BleProximityAvailability.Unavailable(
                code = "ble_permission_missing",
                message = "Bluetooth permission is missing",
            )
        )
        val coordinator = ProximityCoordinator(Wallet("preflight"), factory)

        val capabilities = coordinator.capabilities(ProximityConfiguration())

        assertEquals(1, factory.capabilityCalls)
        assertTrue(factory.configurations.isEmpty())
        assertFalse(capabilities.mayStart)
        assertEquals("ble_permission_missing", capabilities.bluetoothLowEnergy.unavailable?.code)
        assertEquals(
            listOf(ProximityRemediationAction.RequestBluetoothPermission),
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
        val coordinator = ProximityCoordinator(
            Wallet("nfc-preflight"),
            RecordingTransportFactory(BleProximityAvailability.Available),
            nfc,
        )
        val configuration = ProximityConfiguration(
            session = ProximitySessionConfiguration.ConventionalNfc(
                handover = ProximityNfcHandover.Negotiated,
                retrieval = ProximityRetrievalOptions(
                    nfc = ProximityNfcRetrievalConfiguration(),
                ),
                qrFallback = ProximityRetrievalOptions(
                    nfc = ProximityNfcRetrievalConfiguration(),
                )
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
        assertEquals(ProximityRecovery.None, capabilities.nfcEngagement.unavailable?.recovery)
    }

    @Test
    fun `NFC-only configuration remains blocked when the host is unavailable`() = runTest {
        val nfc = RecordingNfcHostAdapter(
            NfcHostAvailability.Unavailable("nfc_powered_off", "NFC is powered off")
        )
        val coordinator = ProximityCoordinator(Wallet("nfc-only-blocked"), null, nfc)
        val session = coordinator.start(
            ProximityConfiguration(
                session = ProximitySessionConfiguration.ConventionalNfc(
                    handover = ProximityNfcHandover.Static,
                    retrieval = ProximityRetrievalOptions(
                        bluetoothLowEnergy = null,
                        nfc = ProximityNfcRetrievalConfiguration(),
                    )
                ),
            )
        )

        val state = assertIs<ProximityState.CheckingPrerequisites>(session.state.value)
        assertFalse(state.capabilities.mayStart)
        assertEquals(
            listOf(ProximityRemediationAction.EnableNfc),
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
        val coordinator = ProximityCoordinator(Wallet("nfc-v2-capability"), ble, nfc)
        val configuration = ProximityConfiguration(
            session = ProximitySessionConfiguration.ProvisionalNfcV2(bluetoothLowEnergy = null),
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
        val coordinator = ProximityCoordinator(Wallet("qr-nfc"), null, nfc)
        val session = coordinator.start(
            ProximityConfiguration(
                session = ProximitySessionConfiguration.Qr(
                    ProximityRetrievalOptions(
                    bluetoothLowEnergy = null,
                    nfc = ProximityNfcRetrievalConfiguration(),
                )
                ),
            )
        )

        val engagements = session.awaitEngagements()
        assertEquals(1, nfc.prepareCalls)
        assertEquals(1, engagements.size)
        assertIs<ProximityEngagement.Qr>(engagements.single())
        session.close()
    }

    @Test
    fun `combined engagement falls back to QR and BLE when NFC is unavailable`() = runTest {
        val ble = RecordingTransportFactory(BleProximityAvailability.Available)
        val nfc = RecordingNfcHostAdapter(
            NfcHostAvailability.Unavailable("nfc_system_ineligible", "NFC HCE is ineligible")
        )
        val coordinator = ProximityCoordinator(Wallet("combined-fallback"), ble, nfc)
        val session = coordinator.start(
            ProximityConfiguration(
                session = ProximitySessionConfiguration.ConventionalNfc(
                    handover = ProximityNfcHandover.Negotiated,
                    retrieval = ProximityRetrievalOptions(),
                    qrFallback = ProximityRetrievalOptions()
                ),
            )
        )

        val engagements = session.awaitEngagements()
        assertEquals(1, ble.configurations.size)
        assertEquals(0, nfc.prepareCalls)
        assertEquals(1, engagements.size)
        assertIs<ProximityEngagement.Qr>(engagements.single())
        session.close()
    }

    @Test
    fun `unimplemented Wi-Fi Aware remains truthful without blocking or instantiating BLE fallback`() = runTest {
        val ble = RecordingTransportFactory(BleProximityAvailability.Available)
        val wifiAware = RecordingWifiAwareTransportFactory(
            WifiAwareProximityAvailability.Unavailable(
                implemented = false,
                code = "wifi_aware_ios_api_unsupported",
                message = "Wi-Fi Aware is unavailable through public iOS APIs",
            )
        )
        val coordinator = MobileWalletProximityCoordinator(
            wallet = Wallet("wifi-aware-ios-fallback"),
            bleTransportFactory = ble,
            wifiAwareTransportFactory = wifiAware,
        )
        val session = coordinator.start(
            MobileWalletProximityConfiguration(
                retrieval = MobileWalletProximityRetrievalConfiguration.Conventional(
                    wifiAware = MobileWalletProximityWifiAwareConfiguration(),
                )
            )
        )

        val engagements = session.awaitEngagements()
        val capabilities = wifiAware.capabilities.single()
        assertEquals(WifiAwareSecurityPolicy.NcsSk128, capabilities)
        assertTrue(ble.configurations.isNotEmpty())
        assertTrue(wifiAware.configurations.isEmpty())
        assertTrue(engagements.any { it is MobileWalletProximityEngagement.Qr })
        session.close()
    }

    @Test
    fun `Wi-Fi Aware can be the only QR retrieval and receives session-bound key bytes`() = runTest {
        val wifiAware = RecordingWifiAwareTransportFactory(WifiAwareProximityAvailability.Available)
        val coordinator = MobileWalletProximityCoordinator(
            wallet = Wallet("wifi-aware-only"),
            bleTransportFactory = null,
            wifiAwareTransportFactory = wifiAware,
        )
        val session = coordinator.start(
            MobileWalletProximityConfiguration(
                retrieval = MobileWalletProximityRetrievalConfiguration.Conventional(
                    bluetoothLowEnergy = null,
                    wifiAware = MobileWalletProximityWifiAwareConfiguration(),
                )
            )
        )

        val engagements = session.awaitEngagements()
        assertEquals(1, wifiAware.configurations.size)
        assertTrue(wifiAware.configurations.single().eDeviceKeyBytes.size > 0)
        assertIs<MobileWalletProximityEngagement.Qr>(engagements.single())
        session.close()
    }

    @Test
    fun `combined QR and NFC engagement gets distinct BLE transaction configurations`() = runTest {
        val ble = RecordingTransportFactory(BleProximityAvailability.Available)
        val nfc = RecordingNfcHostAdapter(NfcHostAvailability.Available)
        val coordinator = ProximityCoordinator(Wallet("combined-uuids"), ble, nfc)
        val session = coordinator.start(
            ProximityConfiguration(
                session = ProximitySessionConfiguration.ProvisionalNfcV2(
                    bluetoothLowEnergy = ProximityBleConfiguration(),
                    qrFallback = ProximityRetrievalOptions(
                        bluetoothLowEnergy = ProximityBleConfiguration(),
                        nfc = null
                    )
                ),
            )
        )

        val engagements = session.awaitEngagements()
        assertEquals(2, ble.configurations.size)
        assertEquals(1, nfc.prepareCalls)
        assertEquals(2, engagements.size)
        assertTrue(engagements.any { it is ProximityEngagement.Qr })
        assertTrue(engagements.any { it is ProximityEngagement.Nfc })
        assertEquals(
            ble.configurations[0].eDeviceKeyBytes,
            ble.configurations[1].eDeviceKeyBytes,
        )
        assertNotEquals(ble.configurations[0].roles, ble.configurations[1].roles)
        session.close()
    }

    @Test
    fun `static NFC dual-role BLE uses the shared carrier UUID while QR stays distinct`() = runTest {
        val ble = RecordingTransportFactory(BleProximityAvailability.Available)
        val nfc = RecordingNfcHostAdapter(NfcHostAvailability.Available)
        val coordinator = ProximityCoordinator(Wallet("static-dual-role"), ble, nfc)
        val session = coordinator.start(ProximityConfiguration(
            session = ProximitySessionConfiguration.ConventionalNfc(
                handover = ProximityNfcHandover.Static,
                retrieval = ProximityRetrievalOptions(),
                qrFallback = ProximityRetrievalOptions(),
            ),
        ))
        try {
            val engagements = session.awaitEngagements()
            assertEquals(2, engagements.size)
            assertEquals(1, nfc.prepareCalls)
            val nfcRoles = assertIs<BleMdocRoles.Dual>(ble.configurations[0].roles)
            val qrRoles = assertIs<BleMdocRoles.Dual>(ble.configurations[1].roles)
            assertEquals(nfcRoles.readerServiceUuid, nfcRoles.mdocServiceUuid)
            assertNotEquals(qrRoles.readerServiceUuid, qrRoles.mdocServiceUuid)
        } finally { session.close() }
    }

    @Test
    fun `combined NFCv2 session omits an unusable QR path instead of failing preparation`() = runTest {
        val ble = RecordingTransportFactory(
            BleProximityAvailability.Unavailable("ble_powered_off", "Bluetooth is powered off")
        )
        val nfc = RecordingNfcHostAdapter(NfcHostAvailability.Available)
        val coordinator = ProximityCoordinator(Wallet("combined-nfc-v2-only"), ble, nfc)
        val session = coordinator.start(
            ProximityConfiguration(
                session = ProximitySessionConfiguration.ProvisionalNfcV2(
                    bluetoothLowEnergy = ProximityBleConfiguration(),
                    qrFallback = ProximityRetrievalOptions(
                        bluetoothLowEnergy = ProximityBleConfiguration(),
                        nfc = null
                    )
                ),
            )
        )

        val engagements = session.awaitEngagements()

        assertEquals(1, nfc.prepareCalls)
        assertEquals(1, engagements.size)
        assertIs<ProximityEngagement.Nfc>(engagements.single())
        session.close()
    }

    @Test
    fun `wallet admits one active session and rotates session key and UUIDs`() = runTest {
        val factory = RecordingTransportFactory(BleProximityAvailability.Available)
        val coordinator = ProximityCoordinator(Wallet("single-session"), factory)

        val first = coordinator.start(ProximityConfiguration())
        first.awaitConnection()
        assertEquals(ProximityConnectedRoute(
            ProximityEngagementMethod.Qr, ProximityTransport.BluetoothLowEnergy,
        ), first.connectedRoute)
        assertFailsWith<IllegalStateException> {
            coordinator.start(ProximityConfiguration())
        }
        val retainedRoute = first.connectedRoute
        first.close()
        assertEquals(retainedRoute, first.connectedRoute)

        val second = coordinator.start(ProximityConfiguration())
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
        val coordinator = ProximityCoordinator(Wallet("remediation"), factory)
        val session = coordinator.start(ProximityConfiguration())
        val blocked = assertIs<ProximityState.CheckingPrerequisites>(session.state.value)

        assertFalse(blocked.capabilities.mayStart)
        assertTrue(factory.configurations.isEmpty())
        assertIs<ProximityActionResult.Rejected>(
            session.dispatch(
                ProximityAction.ReportRemediation(
                    ProximityRemediationAction.OpenApplicationSettings,
                    ProximityHostActionResult.Completed,
                )
            )
        )
        assertEquals(
            ProximityActionResult.Accepted,
            session.dispatch(
                ProximityAction.ReportRemediation(
                    ProximityRemediationAction.EnableBluetooth,
                    ProximityHostActionResult.Failed,
                )
            )
        )
        assertTrue(factory.configurations.isEmpty())

        factory.availability = BleProximityAvailability.Available
        assertEquals(
            ProximityActionResult.Accepted,
            session.dispatch(
                ProximityAction.ReportRemediation(
                    ProximityRemediationAction.EnableBluetooth,
                    ProximityHostActionResult.Completed,
                )
            )
        )
        session.awaitConnection()
        assertEquals(1, factory.configurations.size)
        session.close()
    }

    @Test
    fun `unavailable NFC cannot lend its BLE bearer to an NFC-only QR fallback`() = runTest {
        val ble = RecordingTransportFactory(BleProximityAvailability.Available)
        val nfc = RecordingNfcHostAdapter(NfcHostAvailability.Unavailable("nfc_powered_off", "NFC is off"))
        val coordinator = ProximityCoordinator(Wallet("crossed-routes"), ble, nfc)
        for (handover in ProximityNfcHandover.entries) {
            val configuration = ProximityConfiguration(
                session = ProximitySessionConfiguration.ConventionalNfc(
                    handover = handover,
                    retrieval = ProximityRetrievalOptions(),
                    qrFallback = ProximityRetrievalOptions(
                        bluetoothLowEnergy = null,
                        nfc = ProximityNfcRetrievalConfiguration(),
                    ),
                ),
            )
            val capabilities = coordinator.capabilities(configuration)
            assertTrue(capabilities.bluetoothLowEnergy.mayStart)
            assertFalse(capabilities.qrMayStart)
            assertFalse(capabilities.nfcMayStart)
            assertFalse(capabilities.mayStart)
            val session = coordinator.start(configuration)
            assertIs<ProximityState.CheckingPrerequisites>(session.state.value)
            session.close()
        }
        assertTrue(ble.configurations.isEmpty())
        assertEquals(0, nfc.prepareCalls)
    }

    @Test
    fun `missing optional BLE factory does not block a usable direct NFC route`() = runTest {
        val nfc = RecordingNfcHostAdapter(NfcHostAvailability.Available)
        val coordinator = ProximityCoordinator(Wallet("nfc-without-ble"), null, nfc)
        for (handover in ProximityNfcHandover.entries) {
            val configuration = ProximityConfiguration(
                session = ProximitySessionConfiguration.ConventionalNfc(
                    handover = handover,
                    retrieval = ProximityRetrievalOptions(
                        nfc = ProximityNfcRetrievalConfiguration(),
                    ),
                    qrFallback = ProximityRetrievalOptions(),
                ),
            )
            val capabilities = coordinator.capabilities(configuration)
            assertFalse(capabilities.qrMayStart)
            assertTrue(capabilities.nfcMayStart)
            val session = coordinator.start(configuration)
            assertIs<ProximityEngagement.Nfc>(session.awaitEngagements().single())
            session.close()
        }
        assertEquals(2, nfc.prepareCalls)
    }

    private suspend fun ProximitySession.awaitConnection() {
        withContext(Dispatchers.Default) {
            withTimeout(5.seconds) {
                state.first { it is ProximityState.Connecting }
            }
        }
    }

    private suspend fun ProximitySession.awaitEngagements(): List<ProximityEngagement> =
        withContext(Dispatchers.Default) {
            withTimeout(5.seconds) {
                when (val current = state.first {
                    it is ProximityState.EngagementReady ||
                        it is ProximityState.Connecting
                }) {
                    is ProximityState.EngagementReady -> current.engagements
                    is ProximityState.Connecting -> current.engagements
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

    private class RecordingWifiAwareTransportFactory(
        var availability: WifiAwareProximityAvailability,
    ) : WifiAwareProximityTransportFactory {
        val capabilities = mutableListOf<WifiAwareSecurityPolicy>()
        val configurations = mutableListOf<WifiAwareProximityTransportConfiguration>()

        override suspend fun capability(
            securityPolicy: WifiAwareSecurityPolicy,
        ): WifiAwareProximityAvailability {
            capabilities += securityPolicy
            return availability
        }

        override fun create(
            configuration: WifiAwareProximityTransportConfiguration,
        ): ReaderSelectedTransportProvider {
            configurations += configuration
            return FakeTransportProvider(
                method = DeviceRetrievalMethod.WifiAware(
                    passphraseInfo = null,
                    supportedBands = byteArrayOf(0x04),
                ),
                connection = FakeProximityLoopback.create().holder,
            )
        }
    }
}
