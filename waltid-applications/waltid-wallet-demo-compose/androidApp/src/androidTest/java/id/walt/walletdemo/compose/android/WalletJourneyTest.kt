package id.walt.walletdemo.compose.android

import android.Manifest
import android.graphics.Rect
import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Application-window checks complement the shared renderer's navigation and layout tests. */
@RunWith(AndroidJUnit4::class)
class WalletJourneyTest {
    @Test
    fun scannerModesKeepDraftAndActionsAboveKeyboard() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        // This lane verifies an already-authorized scanner, separately from OS permission UI.
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.CAMERA)
        WalletComposeE2EHelper.launchAndUnlock(context, device)
        WalletComposeE2EHelper.clickByTag(device, "wallet.scanButton")
        assertTrue(device.wait(Until.hasObject(By.text("Scan QR code")), 10_000))
        assertFalse(device.hasObject(By.res("wallet.scanContinue")))
        WalletComposeE2EHelper.clickByTag(device, "wallet.scanMode")
        val input = requireNotNull(WalletComposeE2EHelper.waitForResource(device, "wallet.scanInput", 10_000))
        input.setText("openid4vp://fixture")
        assertTrue(device.wait(Until.hasObject(By.res("wallet.scanPaste")), 10_000))
        assertActionAboveKeyboard(device)
        assertTrue(requireNotNull(device.findObject(By.res("wallet.scanContinue"))).isEnabled)
        val directory = File(context.cacheDir, "wallet-journey-evidence").apply { mkdirs() }
        assertTrue(device.takeScreenshot(File(directory, "scanner-manual-keyboard-clearance.png")))

        WalletComposeE2EHelper.clickByTag(device, "wallet.scanMode")
        assertTrue(device.wait(Until.hasObject(By.text("Scan QR code")), 10_000))
        assertFalse(device.hasObject(By.res("wallet.scanInput")))
        assertTrue(device.wait(Until.gone(By.res("wallet.scanContinue")), 5_000))
        WalletComposeE2EHelper.clickByTag(device, "wallet.scanMode")
        val restored = requireNotNull(WalletComposeE2EHelper.waitForResource(device, "wallet.scanInput", 10_000))
        assertTrue(restored.text == "openid4vp://fixture")
        WalletComposeE2EHelper.clickByTag(device, "wallet.flowBack")
        assertTrue(device.wait(Until.hasObject(By.res("wallet.scanButton")), 10_000))
        assertFalse(device.hasObject(By.res("wallet.presentationInput")))
    }

    private fun assertActionAboveKeyboard(device: UiDevice) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var observed = "IME not visible"
        val deadline = System.nanoTime() + 10_000_000_000L
        while (System.nanoTime() < deadline) {
            val keyboard = instrumentation.uiAutomation.windows
                .firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            val keyboardBounds = Rect()
            keyboard?.getBoundsInScreen(keyboardBounds)
            val actionBounds = device.findObject(By.res("wallet.scanContinue"))?.visibleBounds
            observed = "action=$actionBounds keyboard=$keyboardBounds"
            if (keyboard != null && !keyboardBounds.isEmpty && actionBounds != null &&
                actionBounds.bottom <= keyboardBounds.top) return
            // Bounded condition polling; a delay alone never satisfies the assertion.
            Thread.sleep(100)
        }
        assertTrue("Scanner action must remain above the visible IME: $observed", false)
    }
}
