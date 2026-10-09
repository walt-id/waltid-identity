package id.walt.walletdemo.compose.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.*
import id.walt.walletdemo.compose.ui.SystemBackHandler
import id.walt.walletdemo.compose.ui.WalletUiTestTags
import id.walt.walletdemo.compose.ui.components.*
import id.walt.walletdemo.compose.ui.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun WalletAccessSettingsScreen(controller: WalletDemoController, access: WalletAccessState) {
    DisposableEffect(controller) { onDispose { controller.cancelPinChange() } }
    LaunchedEffect(controller) { controller.refreshBiometricUnlockAvailability() }
    val changing = access.pinChange != null
    SystemBackHandler(changing && WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) == 0) { controller.cancelPinChange() }
    if (changing) {
        WalletPinEntryScreen(access,
            onValueChange = { value, confirmation -> if (confirmation) controller.updatePinConfirmation(value) else controller.updatePin(value) },
            onClear = controller::clearPin,
            onBack = controller::editSetupPin.takeIf { access.pinChange is WalletPinChange.NewPin },
            onRetry = controller::submitPin, onCancel = controller::cancelPinChange)
    } else {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).fillMaxWidth()
            .wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 640.dp).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)) {
            WalletSection {
                SettingsActionRow(stringResource(Res.string.settings_change_pin), controller::startPinChange,
                    Modifier.testTag(WalletUiTestTags.SettingsChangePin), enabled = !access.isBusy)
                SettingsDivider()
                SettingsToggleRow(stringResource(when (access.biometricKind) {
                    DemoBiometricKind.FaceId -> Res.string.settings_face_id_unlock
                    DemoBiometricKind.TouchId -> Res.string.settings_touch_id_unlock
                    DemoBiometricKind.Generic -> Res.string.settings_biometric_unlock
                }), access.biometricEnabled, controller::setBiometricUnlockEnabled,
                    Modifier.testTag(WalletUiTestTags.SettingsBiometricUnlock), enabled = !access.isBusy && (access.biometricAvailable || access.biometricEnabled))
            }
            if (!access.biometricAvailable) BiometricRecoveryNotice(access.biometricAvailability, access.biometricKind)
            access.settingsNotice?.let { SettingsNotice(it.message, error = it.kind == WalletAccessNotice.Kind.Error,
                modifier = Modifier.testTag("wallet.accessNotice")) }
        }
    }
}
