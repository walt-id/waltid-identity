package id.walt.walletdemo.compose.logic

import id.walt.wallet2.mobile.MobileWalletProximityReaderPolicy
import id.walt.wallet2.mobile.MobileWalletProximityReaderTrustSettings
import id.walt.wallet2.mobile.MobileWalletProximityStoredReaderTrustAnchor
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DemoReaderTrustSettingsTest {
    @Test
    fun `persistent store writes and reads canonical settings`() {
        var encoded: String? = null
        val store = PersistentDemoReaderTrustSettingsStore(
            read = { encoded },
            write = { encoded = it },
        )
        val settings = MobileWalletProximityReaderTrustSettings(
            readerPolicy = MobileWalletProximityReaderPolicy.RequireTrusted,
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
        )

        assertEquals(MobileWalletProximityReaderTrustSettings(), controller.state.value.settings)
        assertTrue(controller.state.value.error.orEmpty().contains("invalid", ignoreCase = true))
    }

    @Test
    fun `policy removal and reset are persisted atomically`() = runTest {
        val store = InMemoryDemoReaderTrustSettingsStore(
            MobileWalletProximityReaderTrustSettings(
                trustAnchors = listOf(
                    MobileWalletProximityStoredReaderTrustAnchor("public-certificate", "Reader CA")
                )
            )
        )
        val controller = DemoReaderTrustSettingsController(
            store = store,
            scope = this,
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        controller.setReaderPolicy(MobileWalletProximityReaderPolicy.RequireTrusted)
        controller.removeReaderAuthority("public-certificate")

        assertEquals(MobileWalletProximityReaderPolicy.RequireTrusted, store.load().readerPolicy)
        assertTrue(store.load().trustAnchors.isEmpty())
        assertNull(controller.state.value.pendingImport)

        controller.reset()
        assertEquals(MobileWalletProximityReaderTrustSettings(), store.load())
        assertEquals(store.load(), controller.sessionSnapshot())
    }
}
