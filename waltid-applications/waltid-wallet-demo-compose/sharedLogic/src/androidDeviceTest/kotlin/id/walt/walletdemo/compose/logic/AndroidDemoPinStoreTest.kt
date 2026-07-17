package id.walt.walletdemo.compose.logic

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class AndroidDemoPinStoreTest {
    @Test
    fun verifierPersistsAcrossStoreRecreationWithoutStoringPin() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val walletId = "pin-store-test-${System.nanoTime()}"
        val firstStore = createAndroidDemoPinStore(context, walletId)

        assertFalse(firstStore.hasPin())
        firstStore.setPin("1234")

        val recreatedStore = createAndroidDemoPinStore(context, walletId)
        assertTrue(recreatedStore.hasPin())
        assertTrue(recreatedStore.verifyPin("1234"))
        assertFalse(recreatedStore.verifyPin("9999"))

        val storedRecord = context
            .getSharedPreferences("walt_wallet_demo_pin_verifiers", 0)
            .getString("pin.$walletId", null)
        assertTrue(storedRecord?.startsWith("1:210000:") == true)
        assertNotEquals("1234", storedRecord)
    }
}
