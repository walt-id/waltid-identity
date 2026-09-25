package id.walt.walletdemo.compose.android

import android.app.Instrumentation
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.assertResourceVisibleAfterScrolling
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.launchExpectingSetupAndUnlock
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.relaunchAndUnlock
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

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
            waitForLandscape(device)
            assertTrue(device.wait(Until.hasObject(By.res("wallet.settingsReaderPolicyAllowUntrusted")), 10_000))
            assertTrue(!device.hasObject(By.res("wallet.pinInput")))
            recreateResumedMainActivity(instrumentation)
            instrumentation.waitForIdleSync()
            assertTrue(device.wait(Until.hasObject(By.res("wallet.settingsReaderPolicyAllowUntrusted")), 10_000))
            assertTrue(!device.hasObject(By.res("wallet.pinInput")))
            WalletComposeE2EHelper.clickByTag(device, "wallet.settingsBack")
            // Small landscape viewports require scrolling, and Compose may still be
            // composing the restored back stack after rotation + recreate.
            assertResourceVisibleAfterScrolling(
                device,
                "wallet.settingsConnectionMethod",
                "Nearby sharing was not restored",
            )
            WalletComposeE2EHelper.clickByTag(device, "wallet.settingsBack")
            assertResourceVisibleAfterScrolling(
                device,
                "wallet.settingsSigningKey",
                "Settings root was not restored",
            )
        } finally {
            device.setOrientationNatural()
            device.unfreezeRotation()
        }
    }

    private fun waitForLandscape(device: UiDevice) {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            if (device.displayWidth > device.displayHeight) return
            Thread.sleep(100)
        }
        fail("Emulator did not enter landscape after rotation")
    }

    private fun recreateResumedMainActivity(instrumentation: Instrumentation) {
        val previous = AtomicReference<MainActivity>()
        instrumentation.runOnMainSync {
            val resumed = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<MainActivity>()
                .single()
            previous.set(resumed)
            resumed.recreate()
        }
        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            val ready = AtomicBoolean(false)
            instrumentation.runOnMainSync {
                val resumed = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>()
                ready.set(resumed.any { it !== previous.get() })
            }
            if (ready.get()) return
            Thread.sleep(100)
        }
        fail("MainActivity did not resume after recreate")
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
