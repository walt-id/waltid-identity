package id.walt.walletdemo.compose.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
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

    @Test
    fun settingsSurviveRotationAndActivityRecreationWithoutLocking() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        launchExpectingSetupAndUnlock(instrumentation.targetContext, device)
        WalletComposeE2EHelper.clickByTag(device, "wallet.settingsButton")
        WalletComposeE2EHelper.clickByTag(device, "wallet.settingsProximityPresentation")
        WalletComposeE2EHelper.clickByTag(device, "wallet.settingsReaderAuthentication")
        try {
            device.setOrientationLeft()
            assertTrue(device.wait(Until.hasObject(By.res("wallet.settingsReaderPolicyAllowUntrusted")), 10_000))
            assertTrue(!device.hasObject(By.res("wallet.pinInput")))
            instrumentation.runOnMainSync {
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>().single().recreate()
            }
            instrumentation.waitForIdleSync()
            assertTrue(device.wait(Until.hasObject(By.res("wallet.settingsReaderPolicyAllowUntrusted")), 10_000))
            assertTrue(!device.hasObject(By.res("wallet.pinInput")))
            WalletComposeE2EHelper.clickByTag(device, "wallet.settingsBack")
            assertTrue(device.wait(Until.hasObject(By.res("wallet.settingsConnectionMethod")), 5_000))
            WalletComposeE2EHelper.clickByTag(device, "wallet.settingsBack")
            assertTrue(device.wait(Until.hasObject(By.res("wallet.settingsSigningKey")), 5_000))
        } finally {
            device.setOrientationNatural()
            device.unfreezeRotation()
        }
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
