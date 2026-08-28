@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.wallet2.mobile

import id.walt.mdoc.objects.engagement.BleCentralMode
import id.walt.mdoc.objects.engagement.DeviceRetrievalMethod
import id.walt.mdoc.proximity.FakeProximityLoopback
import id.walt.mdoc.proximity.FakeTransportProvider
import id.walt.mdoc.proximity.ProximityTransportProvider
import id.walt.mdoc.proximity.mobile.BleMdocRoles
import id.walt.mdoc.proximity.mobile.BleMdocRoleSelection
import id.walt.mdoc.proximity.mobile.BleProximityAvailability
import id.walt.mdoc.proximity.mobile.BleProximityTransportConfiguration
import id.walt.mdoc.proximity.mobile.BleProximityTransportFactory
import id.walt.wallet2.data.Wallet
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

    private class RecordingTransportFactory(
        var availability: BleProximityAvailability,
    ) : BleProximityTransportFactory {
        var capabilityCalls: Int = 0
        val configurations: MutableList<BleProximityTransportConfiguration> = mutableListOf()

        override suspend fun capability(roles: BleMdocRoleSelection): BleProximityAvailability {
            capabilityCalls++
            return availability
        }

        override fun create(configuration: BleProximityTransportConfiguration): ProximityTransportProvider {
            configurations += configuration
            val loopback = FakeProximityLoopback.create()
            return FakeTransportProvider(
                method = DeviceRetrievalMethod.Ble(
                    centralMode = BleCentralMode(ByteArray(16) { 1 }),
                ),
                connection = loopback.holder,
            )
        }
    }
}
