package id.walt.walletdemo.compose.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import id.walt.walletdemo.compose.logic.DemoBiometricAvailability
import id.walt.walletdemo.compose.logic.DemoBiometricKind
import id.walt.walletdemo.compose.logic.WalletAccessState
import id.walt.walletdemo.compose.logic.WalletAuthState
import id.walt.walletdemo.compose.ui.screens.WalletPinEntryScreen
import kotlin.test.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WalletBiometricRecoveryAndroidTest {
    @Test
    fun wrongPinRemainsVisibleWhenConfiguredFaceIdIsUnavailable() = runAndroidComposeUiTest<ComponentActivity> {
        setContent { WalletPinEntryScreen(
            WalletAccessState(auth = WalletAuthState.Login(error = "Wrong PIN", biometricPromptConsumed = true),
                biometricEnabled = true, biometricAvailability = DemoBiometricAvailability.Unavailable,
                biometricKind = DemoBiometricKind.FaceId),
            onValueChange = { _, _ -> }, onClear = {}, onBack = null, onRetry = {}, onBiometricSettings = {}) }
        onNodeWithText("Wrong PIN").assertIsDisplayed()
        onNodeWithText("Open Settings").assertIsDisplayed()
        onAllNodesWithText("Face ID is unavailable. Check this app’s Face ID access in Settings.").assertCountEquals(0)
    }
}
