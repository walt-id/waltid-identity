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
    fun clearPinBeforeTest() {
        org.junit.Assume.assumeTrue("PIN-reset tests require an isolated emulator",
            android.os.Build.HARDWARE in setOf("ranchu", "goldfish"))
        clearPersistedPin()
    }

    @After
    fun clearPinAfterTest() {
        if (android.os.Build.HARDWARE in setOf("ranchu", "goldfish")) clearPersistedPin()
    }

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
        assertPinActionAboveKeyboard(instrumentation, device) // No tap: Choose opens the IME itself.
        input.setText("123")
        assertPinActionAboveKeyboard(instrumentation, device)
        assertTrue(!device.hasObject(By.res("wallet.pinSubmitButton")))
        val chooseY = input.visibleBounds.centerY()
        input.setText(WalletComposeE2EHelper.PIN)
        val confirmation = requireNotNull(WalletComposeE2EHelper.waitForResource(device, "wallet.pinConfirmationInput", 10_000))
        assertTrue(!device.hasObject(By.res("wallet.pinInput")))
        assertPinActionAboveKeyboard(instrumentation, device)
        assertTrue("The stable editor moved between Choose and Confirm", kotlin.math.abs(confirmation.visibleBounds.centerY() - chooseY) <= 2)
        confirmation.setText("123")
        assertPinActionAboveKeyboard(instrumentation, device)
        WalletComposeE2EHelper.dismissKeyboard(device) // Wait until IME dismissal finishes before system Back.
        device.pressBack() // The system Back action returns to Choose, rather than exiting.
        assertTrue(device.wait(Until.hasObject(By.text("Step 1 of 2")), 10_000))
        assertPinActionAboveKeyboard(instrumentation, device)
        requireNotNull(WalletComposeE2EHelper.waitForResource(device, "wallet.pinInput", 10_000)).setText(WalletComposeE2EHelper.PIN)
        val retry = requireNotNull(WalletComposeE2EHelper.waitForResource(device, "wallet.pinConfirmationInput", 10_000))
        retry.setText("4321")
        assertTrue(device.wait(Until.hasObject(By.text("PIN confirmation does not match")), 10_000))
        retry.setText(WalletComposeE2EHelper.PIN)
        WalletComposeE2EHelper.awaitWalletReady(device)
        WalletComposeE2EHelper.launch(context)
        requireNotNull(WalletComposeE2EHelper.waitForResource(device, "wallet.pinInput", 30_000))
        assertTrue(!device.hasObject(By.text("Step 1 of 2")))
        assertPinActionAboveKeyboard(instrumentation, device) // PIN-only Unlock focuses without a tap.
        requireNotNull(WalletComposeE2EHelper.waitForResource(device, "wallet.pinInput", 10_000)).setText("0000")
        assertTrue(device.wait(Until.hasObject(By.text("Wrong PIN")), 10_000))
        assertTrue(device.findObject(By.res("wallet.pinInput")).text.isEmpty())
        assertPinActionAboveKeyboard(instrumentation, device)
        WalletComposeE2EHelper.unlock(device)
    }

    @Test
    fun walletAccessChangesPinAndPersistsOnlyTheConfirmedReplacement() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        launchExpectingSetupAndUnlock(context, device)
        WalletComposeE2EHelper.clickByTag(device, "wallet.settingsButton")
        WalletComposeE2EHelper.clickByTag(device, "wallet.settingsWalletAccess")
        WalletComposeE2EHelper.clickByTag(device, "wallet.settingsChangePin")
        requireNotNull(WalletComposeE2EHelper.waitForResource(device, "wallet.pinInput", 10_000)).setText("1234")
        assertTrue(device.wait(Until.hasObject(By.text("Choose a new PIN")), 10_000))
        assertPinActionAboveKeyboard(instrumentation, device)
        device.findObject(By.res("wallet.pinInput")).setText("5678")
        val confirmation = requireNotNull(WalletComposeE2EHelper.waitForResource(device, "wallet.pinConfirmationInput", 10_000))
        confirmation.setText("5678")
        assertTrue(device.wait(Until.hasObject(By.text("PIN changed")), 10_000))
        WalletComposeE2EHelper.clickByTag(device, "wallet.settingsBack")
        WalletComposeE2EHelper.clickByTag(device, "wallet.settingsLock")
        val input = requireNotNull(WalletComposeE2EHelper.waitForResource(device, "wallet.pinInput", 10_000))
        input.setText("1234")
        assertTrue(device.wait(Until.hasObject(By.text("Wrong PIN")), 10_000))
        assertTrue(input.text.isEmpty())
        assertPinActionAboveKeyboard(instrumentation, device)
        input.setText("5678")
        WalletComposeE2EHelper.awaitWalletReady(device)
        WalletComposeE2EHelper.launch(context)
        requireNotNull(WalletComposeE2EHelper.waitForResource(device, "wallet.pinInput", 10_000)).setText("5678")
        WalletComposeE2EHelper.awaitWalletReady(device)
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

    private fun assertPinActionAboveKeyboard(instrumentation: Instrumentation, device: UiDevice) {
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
            val bounds = device.findObject(By.res("wallet.pinClearButton"))?.visibleBounds
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
