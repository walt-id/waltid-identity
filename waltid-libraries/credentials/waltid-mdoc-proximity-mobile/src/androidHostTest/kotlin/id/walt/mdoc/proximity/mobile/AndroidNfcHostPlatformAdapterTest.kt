@file:OptIn(
    kotlinx.coroutines.ExperimentalCoroutinesApi::class,
    kotlinx.serialization.ExperimentalSerializationApi::class,
)

package id.walt.mdoc.proximity.mobile

import id.walt.mdoc.objects.engagement.DeviceRetrievalMethod
import id.walt.mdoc.proximity.ProximityCloseReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AndroidNfcHostPlatformAdapterTest {
    @After
    fun cleanRegistry() = kotlinx.coroutines.runBlocking {
        AndroidNfcSessionRegistry.resetForTest()
    }

    @Test
    fun reportsSystemUnavailabilityWithoutRequestingForegroundRouting() = runTest {
        var routeRequests = 0
        val adapter = adapter(
            state = unavailable("nfc_powered_off"),
            route = { routeRequests += 1; RecordingRoute() },
        )

        val availability = assertIs<NfcHostAvailability.Unavailable>(adapter.capability())

        assertEquals("nfc_powered_off", availability.code)
        assertEquals(0, routeRequests)
    }

    @Test
    fun requiresForegroundRoutingWhenTheServiceDoesNotOwnEveryApplication() = runTest {
        val adapter = adapter(AndroidNfcHostSystemState.Ready(allApplicationsRouted = false))

        val availability = assertIs<NfcHostAvailability.Unavailable>(adapter.capability())

        assertEquals("nfc_foreground_routing_required", availability.code)
    }

    @Test
    fun preparesDefaultRoutedServiceWithoutConsultingForegroundActivity() = runTest {
        var routeRequests = 0
        val adapter = adapter(
            state = AndroidNfcHostSystemState.Ready(allApplicationsRouted = true),
            route = { routeRequests += 1; RecordingRoute() },
        )

        val ready = assertIs<NfcHostPreparation.Ready>(adapter.prepare(router(), this))

        assertEquals(0, routeRequests)
        assertTrue(AndroidNfcSessionRegistry.current() != null)
        ready.session.close(ProximityCloseReason.COMPLETED)
        assertNull(AndroidNfcSessionRegistry.current())
    }

    @Test
    fun foregroundRouteIsEnabledAndReleasedExactlyOnce() = runTest {
        val route = RecordingRoute()
        val adapter = adapter(
            state = AndroidNfcHostSystemState.Ready(allApplicationsRouted = false),
            route = { route },
        )

        val ready = assertIs<NfcHostPreparation.Ready>(adapter.prepare(router(), this))
        assertEquals(1, route.enableCalls)

        ready.session.close(ProximityCloseReason.COMPLETED)
        ready.session.close(ProximityCloseReason.CANCELLED)

        assertEquals(1, route.disableCalls)
        assertNull(AndroidNfcSessionRegistry.current())
    }

    @Test
    fun foregroundRouteAcquisitionFailureDoesNotArmAHostSession() = runTest {
        val route = RecordingRoute(enableResult = false)
        val adapter = adapter(
            state = AndroidNfcHostSystemState.Ready(allApplicationsRouted = false),
            route = { route },
        )

        val unavailable = assertIs<NfcHostPreparation.Unavailable>(adapter.prepare(router(), this))

        assertEquals("nfc_foreground_routing_failed", unavailable.availability.code)
        assertEquals(1, route.enableCalls)
        assertEquals(0, route.disableCalls)
        assertNull(AndroidNfcSessionRegistry.current())
    }

    @Test
    fun rejectedConcurrentSessionDoesNotAcquireOrDisturbForegroundRouting() = runTest {
        val first = assertIs<NfcHostPreparation.Ready>(
            adapter(AndroidNfcHostSystemState.Ready(allApplicationsRouted = true)).prepare(router(), this),
        )
        val rejectedRoute = RecordingRoute()
        val secondAdapter = adapter(
            state = AndroidNfcHostSystemState.Ready(allApplicationsRouted = false),
            route = { rejectedRoute },
        )

        val unavailable = assertIs<NfcHostPreparation.Unavailable>(
            secondAdapter.prepare(router(), this),
        )

        assertEquals("nfc_session_already_active", unavailable.availability.code)
        assertEquals(0, rejectedRoute.enableCalls)
        assertEquals(0, rejectedRoute.disableCalls)
        assertTrue(AndroidNfcSessionRegistry.current() != null)
        first.session.close(ProximityCloseReason.COMPLETED)
    }

    @Test
    fun registryDeactivationReleasesTheGenerationOwnedForegroundRoute() = runTest {
        val route = RecordingRoute()
        val ready = assertIs<NfcHostPreparation.Ready>(
            adapter(
                state = AndroidNfcHostSystemState.Ready(allApplicationsRouted = false),
                route = { route },
            ).prepare(router(), this),
        )
        val generation = requireNotNull(AndroidNfcSessionRegistry.current()).generation

        AndroidNfcSessionRegistry.requestDisarm(
            generation,
            ProximityCloseReason.PEER_DISCONNECTED,
        )

        eventually { route.disableCalls == 1 }
        ready.session.close(ProximityCloseReason.CANCELLED)
        assertEquals(1, route.disableCalls)
    }

    @Test
    fun staleForegroundRouteReleaseCannotDisableANewerGeneration() = runTest {
        val first = RecordingRoute()
        val second = RecordingRoute()

        assertTrue(AndroidNfcForegroundRouteRegistry.acquire(1, first))
        assertTrue(AndroidNfcForegroundRouteRegistry.acquire(2, second))
        AndroidNfcForegroundRouteRegistry.release(1)

        assertEquals(0, first.disableCalls)
        assertEquals(0, second.disableCalls)
        AndroidNfcForegroundRouteRegistry.release(2)
        assertEquals(1, second.disableCalls)
    }

    @Test
    fun inactiveSessionScopeDoesNotAcquireForegroundRouting() = runTest {
        val parent = Job().also { it.cancel() }
        val route = RecordingRoute()
        val adapter = adapter(
            state = AndroidNfcHostSystemState.Ready(allApplicationsRouted = false),
            route = { route },
        )

        val unavailable = assertIs<NfcHostPreparation.Unavailable>(
            adapter.prepare(router(), CoroutineScope(parent)),
        )

        assertEquals("nfc_session_scope_inactive", unavailable.availability.code)
        assertEquals(0, route.enableCalls)
        assertNull(AndroidNfcSessionRegistry.current())
        parent.cancelAndJoin()
    }

    @Test
    fun prepareRechecksRuntimeStateAfterAFormerlyAvailableCapability() = runTest {
        val environment = MutableEnvironment(AndroidNfcHostSystemState.Ready(allApplicationsRouted = true))
        val adapter = AndroidNfcHostPlatformAdapter(environment) { null }
        assertEquals(NfcHostAvailability.Available, adapter.capability())
        environment.state = unavailable("nfc_powered_off")

        val unavailable = assertIs<NfcHostPreparation.Unavailable>(adapter.prepare(router(), this))

        assertEquals("nfc_powered_off", unavailable.availability.code)
        assertNull(AndroidNfcSessionRegistry.current())
    }

    private fun adapter(
        state: AndroidNfcHostSystemState,
        route: () -> AndroidNfcForegroundRoute? = { null },
    ) = AndroidNfcHostPlatformAdapter(MutableEnvironment(state), route)

    private fun unavailable(code: String) = AndroidNfcHostSystemState.Unavailable(
        NfcHostAvailability.Unavailable(code, "Unavailable for this test"),
    )

    private fun router() = NfcHostApduRouter(
        engagement = null,
        retrieval = NfcRetrievalApduProcessor(DeviceRetrievalMethod.Nfc(255u, 256u), 1_024),
        nfcV2 = null,
    )

    private suspend fun eventually(condition: () -> Boolean) {
        repeat(100) {
            if (condition()) return
            kotlinx.coroutines.delay(10)
        }
        assertTrue(condition(), "Condition was not satisfied before timeout")
    }

    private class MutableEnvironment(
        var state: AndroidNfcHostSystemState,
    ) : AndroidNfcHostEnvironment {
        override fun inspect(): AndroidNfcHostSystemState = state
    }

    private class RecordingRoute(
        private val enableResult: Boolean = true,
    ) : AndroidNfcForegroundRoute {
        var enableCalls: Int = 0
            private set
        var disableCalls: Int = 0
            private set

        override fun enable(): Boolean = enableResult.also { enableCalls += 1 }

        override fun disable(): Boolean = true.also { disableCalls += 1 }
    }
}
