package id.walt.walletdemo.compose.android

import android.app.Instrumentation
import android.view.WindowInsets
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
    fun pinStepsKeepConfirmationSeparateAndActionsAboveTheKeyboard() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        WalletComposeE2EHelper.launch(context)
        val input = requireNotNull(WalletComposeE2EHelper.waitForResource(device, "wallet.pinInput", 30_000))
        assertTrue(device.hasObject(By.text("Step 1 of 2")))
        assertTrue(!device.hasObject(By.res("wallet.pinConfirmationInput")))
        assertTrue(!device.hasObject(By.res("wallet.pinBiometricToggle")))
        assertPinSubmitAboveKeyboard(instrumentation, device) // No tap: Choose opens the IME itself.
        input.setText("1234")
        assertPinSubmitAboveKeyboard(instrumentation, device)
        assertTrue(!requireNotNull(device.findObject(By.res("wallet.pinSubmitButton"))).isEnabled)
        input.setText("123456")
        WalletComposeE2EHelper.dismissKeyboard(device)
        WalletComposeE2EHelper.clickByTag(device, "wallet.pinSubmitButton")
        val confirmation = requireNotNull(WalletComposeE2EHelper.waitForResource(device, "wallet.pinConfirmationInput", 10_000))
        assertTrue(!device.hasObject(By.res("wallet.pinInput")))
        assertPinSubmitAboveKeyboard(instrumentation, device) // Confirm also restores a dismissed IME.
        confirmation.setText("123")
        assertPinSubmitAboveKeyboard(instrumentation, device)
        device.pressBack() // Hide the IME first.
        device.pressBack() // The system Back action returns to Choose, rather than exiting.
        assertTrue(device.wait(Until.hasObject(By.text("Step 1 of 2")), 10_000))
        assertPinSubmitAboveKeyboard(instrumentation, device)
        WalletComposeE2EHelper.clickByTag(device, "wallet.pinSubmitButton")
        val retry = requireNotNull(WalletComposeE2EHelper.waitForResource(device, "wallet.pinConfirmationInput", 10_000))
        retry.setText("654321")
        assertTrue(device.wait(Until.hasObject(By.text("PIN confirmation does not match")), 10_000))
        retry.setText("123456")
        WalletComposeE2EHelper.awaitWalletReady(device)
        WalletComposeE2EHelper.launch(context)
        requireNotNull(WalletComposeE2EHelper.waitForResource(device, "wallet.pinInput", 30_000))
        assertTrue(!device.hasObject(By.text("Step 1 of 2")))
        assertPinSubmitAboveKeyboard(instrumentation, device) // PIN-only Unlock focuses without a tap.
        WalletComposeE2EHelper.unlock(device)
    }

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

    private fun assertPinSubmitAboveKeyboard(instrumentation: Instrumentation, device: UiDevice) {
        val deadline = System.currentTimeMillis() + 10_000
        var observed: String? = null
        while (System.currentTimeMillis() < deadline) {
            var keyboardTop: Int? = null
            instrumentation.runOnMainSync {
                val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>().singleOrNull()
                val view = activity?.window?.decorView
                val insets = view?.rootWindowInsets
                if (view != null && insets?.isVisible(WindowInsets.Type.ime()) == true) {
                    val location = IntArray(2)
                    view.getLocationOnScreen(location)
                    keyboardTop = location[1] + view.height - insets.getInsets(WindowInsets.Type.ime()).bottom
                }
            }
            val bounds = device.findObject(By.res("wallet.pinSubmitButton"))?.visibleBounds
            observed = "action=$bounds keyboardTop=$keyboardTop"
            if (keyboardTop != null && bounds != null && bounds.bottom <= keyboardTop) return
            Thread.sleep(100)
        }
        fail("The PIN action must remain above the visible IME: $observed")
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
