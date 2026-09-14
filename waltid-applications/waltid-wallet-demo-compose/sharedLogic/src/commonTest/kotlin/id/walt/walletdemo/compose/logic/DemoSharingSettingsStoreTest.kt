package id.walt.walletdemo.compose.logic

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DemoSharingSettingsStoreTest {
    @Test
    fun missingValueDefaultsToShowingTheDcApiPreview() {
        var stored: Boolean? = null
        val store = PersistentDemoSharingSettingsStore(
            readEnabled = { stored },
            writeEnabled = { stored = it },
        )

        assertTrue(store.showDcApiPresentationPreview())

        store.setShowDcApiPresentationPreview(false)
        assertFalse(store.showDcApiPresentationPreview())

        store.setShowDcApiPresentationPreview(true)
        assertTrue(store.showDcApiPresentationPreview())
    }

    @Test
    fun inMemoryStoreKeepsTheChosenValue() {
        val store = InMemoryDemoSharingSettingsStore()
        assertTrue(store.showDcApiPresentationPreview())

        store.setShowDcApiPresentationPreview(false)
        assertFalse(store.showDcApiPresentationPreview())
    }
}
