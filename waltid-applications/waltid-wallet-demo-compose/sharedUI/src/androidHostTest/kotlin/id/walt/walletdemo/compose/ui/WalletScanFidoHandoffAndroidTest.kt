package id.walt.walletdemo.compose.ui

import android.content.ClipData
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import id.walt.walletdemo.compose.logic.ResolvedWalletLink
import id.walt.walletdemo.compose.logic.WalletLinkKind
import id.walt.walletdemo.compose.ui.screens.WalletScanScreen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalTestApi::class)
class WalletScanFidoHandoffAndroidTest {
    @Test
    fun closeBeforeTheLaunchFrameCancelsPendingHandoff() = runComposeUiTest {
        var launches = 0
        var closed = false
        setContent {
            WalletScanScreen(onBack = { closed = true }, onOpen = { _, _ -> error("Not a wallet protocol flow") },
                initialInput = "FIDO:/123", handoffFidoQr = {
                    launches++
                    FidoQrHandoffResult.Opened
                })
        }
        val continueAction = onNodeWithTag(WalletUiTestTags.ScanContinue).fetchSemanticsNode()
            .config[SemanticsActions.OnClick].action!!
        val closeAction = onNodeWithTag(WalletUiTestTags.FlowBack).fetchSemanticsNode()
            .config[SemanticsActions.OnClick].action!!
        // Both user actions occur before the next frame can launch the system handler.
        runOnIdle { continueAction(); closeAction() }
        waitForIdle()
        assertTrue(closed)
        assertEquals(0, launches)
    }

    @Test
    fun scanReleasesCaptureThenLaunchesOnceWithoutOpeningAWalletFlow() = runComposeUiTest {
        var shown by mutableStateOf(true)
        var captureDisposed = false
        var launches = 0
        var walletFlows = 0
        setContent {
            val window = LocalWindowInfo.current
            val focused = remember(window) { object : WindowInfo by window {
                override val isWindowFocused = true
            } }
            CompositionLocalProvider(LocalWindowInfo provides focused) {
                if (shown) WalletScanScreen(
                    onBack = { shown = false }, onOpen = { _, _ -> walletFlows++ },
                    handoffFidoQr = { code ->
                        assertTrue(captureDisposed, "Capture must be disposed before system launch")
                        assertEquals("FIDO:/00123456789", code)
                        launches++
                        FidoQrHandoffResult.Opened
                    },
                    scanner = { modifier, onCode ->
                        DisposableEffect(Unit) { onDispose { captureDisposed = true } }
                        Button(modifier = modifier, onClick = {
                            onCode("FIDO:/00123456789")
                            onCode("FIDO:/00123456789")
                        }) { Text("Scan fixture") }
                    },
                ) else Text("Wallet")
            }
        }
        onNodeWithText("Scan fixture").performClick()
        waitUntil { launches == 1 }
        onNodeWithText("Wallet").assertIsDisplayed()
        assertEquals(1, launches)
        assertEquals(0, walletFlows)
        runOnIdle { shown = true }
        onNodeWithText("Scan fixture").assertIsDisplayed()
        assertEquals(1, launches, "Returning must not replay the previous QR")
    }

    @Test
    fun pasteWaitsForContinueThenClosesTheScanner() = runComposeUiTest {
        var launches = 0
        var closed = false
        val clipboard = object : Clipboard {
            override suspend fun getClipEntry() = ClipEntry(ClipData.newPlainText("Synthetic FIDO", "fido:/00123456789"))
            override suspend fun setClipEntry(clipEntry: ClipEntry?) = Unit
        }
        setContent {
            WalletScanScreen(onBack = { closed = true }, onOpen = { _, _ -> error("Not a wallet protocol flow") },
                initialInput = "Original", clipboard = clipboard, handoffFidoQr = { code ->
                    assertEquals("fido:/00123456789", code)
                    launches++
                    FidoQrHandoffResult.Opened
                })
        }
        onNodeWithTag("wallet.scanPaste").performClick()
        onNodeWithTag(WalletUiTestTags.ScanInput).assertTextContains("fido:/00123456789")
        assertEquals(0, launches)
        onNodeWithTag(WalletUiTestTags.ScanContinue).assertIsEnabled().performClick()
        waitUntil { closed }
        assertEquals(1, launches)
    }

    @Test
    fun failedHandoffCanRetryOrOpenAnOrdinaryOffer() = runComposeUiTest {
        var launches = 0
        var closed = false
        var opened: String? = null
        setContent {
            WalletScanScreen(onBack = { closed = true }, onOpen = { url, _ -> opened = url },
                initialInput = "FIDO:/123", handoffFidoQr = {
                    launches++
                    if (launches == 1) FidoQrHandoffResult.Unavailable else FidoQrHandoffResult.Blocked
                }, resolveLink = { ResolvedWalletLink(it, WalletLinkKind.Offer) })
        }
        onNodeWithTag(WalletUiTestTags.ScanContinue).performClick()
        onNodeWithText("No system handler is available for this FIDO QR code. Try your device's camera or another device.")
            .assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.ScanContinue).assertIsEnabled().performClick()
        waitUntil { launches == 2 }
        onNodeWithText("Your device could not open this FIDO QR code. Try your device's camera or a fresh code.")
            .assertIsDisplayed()
        assertFalse(closed)
        onNodeWithTag(WalletUiTestTags.ScanInput).performTextReplacement("openid-credential-offer://offer")
        onNodeWithTag(WalletUiTestTags.ScanContinue).performClick()
        waitUntil { opened != null }
        assertEquals("openid-credential-offer://offer", opened)
        assertEquals(2, launches)
    }

    @Test
    fun unsupportedPlatformExplainsDigitalCredentialsAndBlocksDirectHandoff() = runComposeUiTest {
        setContent {
            WalletScanScreen(onBack = {}, onOpen = { _, _ -> error("Unsupported FIDO handoff") },
                initialInput = "FIDO:/123", handoffFidoQr = null)
        }
        onNodeWithText("This is a digital credential or passkey request. Scan it with your device's Camera or Code Scanner.")
            .assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.ScanContinue).assertIsNotEnabled()
    }
}
