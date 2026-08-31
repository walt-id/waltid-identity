package id.walt.walletdemo.compose.logic

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DemoSharingSettingsStoreTest {
    @Test
    fun missingValueDefaultsToShowingTheDcApiPreview() {
        var stored: Boolean? = null
        var storedProfile: String? = null
        val store = PersistentDemoSharingSettingsStore(
            readEnabled = { stored },
            writeEnabled = { stored = it },
            readProximityTransportProfile = { storedProfile },
            writeProximityTransportProfile = { storedProfile = it },
        )

        assertTrue(store.showDcApiPresentationPreview())

        store.setShowDcApiPresentationPreview(false)
        assertFalse(store.showDcApiPresentationPreview())

        store.setShowDcApiPresentationPreview(true)
        assertTrue(store.showDcApiPresentationPreview())

        assertEquals(
            WalletDemoProximityTransportProfile.Default,
            store.proximityTransportProfile(),
        )
        store.setProximityTransportProfile(WalletDemoProximityTransportProfile.ProvisionalNfcV2Hybrid)
        assertEquals(
            WalletDemoProximityTransportProfile.ProvisionalNfcV2Hybrid,
            store.proximityTransportProfile(),
        )
        storedProfile = "unknown_future_profile"
        assertEquals(
            WalletDemoProximityTransportProfile.Default,
            store.proximityTransportProfile(),
        )
    }

    @Test
    fun inMemoryStoreKeepsTheChosenValue() {
        val store = InMemoryDemoSharingSettingsStore()
        assertTrue(store.showDcApiPresentationPreview())

        store.setShowDcApiPresentationPreview(false)
        assertFalse(store.showDcApiPresentationPreview())

        assertEquals(
            WalletDemoProximityTransportProfile.Default,
            store.proximityTransportProfile(),
        )
        store.setProximityTransportProfile(WalletDemoProximityTransportProfile.ProvisionalNfcV2Direct)
        assertEquals(
            WalletDemoProximityTransportProfile.ProvisionalNfcV2Direct,
            store.proximityTransportProfile(),
        )
    }
}
