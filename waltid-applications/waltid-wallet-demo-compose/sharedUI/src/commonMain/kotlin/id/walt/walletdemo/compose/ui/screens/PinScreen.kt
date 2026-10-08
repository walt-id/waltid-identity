package id.walt.walletdemo.compose.ui.screens

import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalWindowInfo
import id.walt.walletdemo.compose.logic.*
import id.walt.walletdemo.compose.ui.SystemBackHandler
import id.walt.walletdemo.compose.ui.rememberBiometricSettingsLauncher

@Composable
internal fun PinScreen(controller: WalletDemoController, access: WalletAccessState) {
    val openSettings = rememberBiometricSettingsLauncher(access.biometricAvailability)
    val login = access.auth as? WalletAuthState.Login
    val focused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(login != null, focused, access.biometricAvailable, access.biometricEnabled) {
        if (focused && login != null) controller.unlockWithBiometrics()
    }
    val confirming = (access.auth as? WalletAuthState.Setup)?.step == PinSetupStep.Confirm
    SystemBackHandler(enabled = confirming && WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) == 0) { if (!access.isBusy) controller.editSetupPin() }
    WalletPinEntryScreen(access,
        onValueChange = { value, confirmation -> if (confirmation) controller.updatePinConfirmation(value) else controller.updatePin(value) },
        onClear = controller::clearPin,
        onBack = controller::editSetupPin.takeIf { confirming },
        onRetry = controller::submitPin,
        onBiometrics = { controller.unlockWithBiometrics(force = true) }.takeIf { login != null && access.biometricAvailable && access.biometricEnabled },
        onBiometricSettings = openSettings.takeIf { login != null && !access.biometricAvailable && access.biometricEnabled })
}
