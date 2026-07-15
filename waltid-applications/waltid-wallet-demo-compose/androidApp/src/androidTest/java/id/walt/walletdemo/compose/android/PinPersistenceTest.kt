package id.walt.walletdemo.compose.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.launchExpectingSetupAndUnlock
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.relaunchAndUnlock
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PinPersistenceTest {
    @Before
    fun clearPinBeforeTest() = clearPersistedPin()

    @After
    fun clearPinAfterTest() = clearPersistedPin()

    @Test
    fun relaunchShowsLoginAndAcceptsOriginalPin() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)

        launchExpectingSetupAndUnlock(context, device)
        relaunchAndUnlock(context, device)
    }

    private fun clearPersistedPin() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(
            "PIN verifier preferences could not be cleared",
            context.getSharedPreferences(PIN_PREFERENCES_NAME, 0).edit().clear().commit(),
        )
    }

    private companion object {
        const val PIN_PREFERENCES_NAME = "walt_wallet_demo_pin_verifiers"
    }
}
