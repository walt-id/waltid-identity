package id.walt.walletdemo.compose.logic

import id.walt.wallet2.mobile.ProximityReaderPolicy
import id.walt.wallet2.mobile.ProximityReaderTrustSettings
import id.walt.wallet2.mobile.ProximityStoredReaderTrustAnchor
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DemoReaderTrustSettingsTest {
    @Test
    fun `cancelled queued import cannot restore a preview and failed save retains policy`() = runTest {
        var saved = ProximityReaderTrustSettings()
        val store = object : DemoReaderTrustSettingsStore {
            override fun load() = saved
            override fun save(settings: ProximityReaderTrustSettings) { error("disk unavailable") }
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val controller = DemoReaderTrustSettingsController(store, this, dispatcher, dispatcher)
        advanceUntilIdle()
        controller.prepareImport("invalid.der", byteArrayOf(1))
        controller.cancelImport()
        advanceUntilIdle()
        assertNull(controller.state.value.pendingImport)
        assertNull(controller.state.value.error)
        controller.setReaderPolicy(ProximityReaderPolicy.RequireTrusted)
        advanceUntilIdle()
        assertEquals(saved, controller.sessionSnapshot())
        assertTrue(controller.state.value.error.orEmpty().contains("disk unavailable"))
    }

    @Test
    fun `persistent store writes and reads canonical settings`() {
        var encoded: String? = null
        val store = PersistentDemoReaderTrustSettingsStore(
            read = { encoded },
            write = { encoded = it },
        )
        val settings = ProximityReaderTrustSettings(
            readerPolicy = ProximityReaderPolicy.RequireTrusted,
        )

        store.save(settings)

        assertEquals(settings, store.load())
        assertTrue(encoded.orEmpty().contains("require_trusted"))
    }

    @Test
    fun `corrupt persisted settings fail safely to defaults with a visible error`() = runTest {
        val store = PersistentDemoReaderTrustSettingsStore(
            read = { "not-json" },
            write = {},
        )
        val controller = DemoReaderTrustSettingsController(
            store = store,
            scope = this,
            dispatcher = StandardTestDispatcher(testScheduler),
            workerDispatcher = StandardTestDispatcher(testScheduler),
        )

        advanceUntilIdle()
        assertEquals(ProximityReaderTrustSettings(), controller.state.value.settings)
        assertTrue(controller.state.value.error.orEmpty().contains("invalid", ignoreCase = true))
    }

    @Test
    fun `policy removal and reset are persisted atomically`() = runTest {
        val store = InMemoryDemoReaderTrustSettingsStore(
            ProximityReaderTrustSettings(
                trustAnchors = listOf(
                    ProximityStoredReaderTrustAnchor("public-certificate", "Reader CA")
                )
            )
        )
        val controller = DemoReaderTrustSettingsController(
            store = store,
            scope = this,
            dispatcher = StandardTestDispatcher(testScheduler),
            workerDispatcher = StandardTestDispatcher(testScheduler),
        )

        controller.setReaderPolicy(ProximityReaderPolicy.RequireTrusted)
        controller.removeReaderAuthority("public-certificate")

        advanceUntilIdle()
        assertEquals(ProximityReaderPolicy.RequireTrusted, store.load().readerPolicy)
        assertTrue(store.load().trustAnchors.isEmpty())
        assertNull(controller.state.value.pendingImport)

        controller.reset()
        advanceUntilIdle()
        assertEquals(ProximityReaderTrustSettings(), store.load())
        assertEquals(store.load(), controller.sessionSnapshot())
    }
}
