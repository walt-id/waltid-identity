package id.walt.walletdemo.compose.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.PinSetupStep
import id.walt.walletdemo.compose.logic.WalletAuthState
import id.walt.walletdemo.compose.logic.WalletDemoController
import id.walt.walletdemo.compose.ui.SystemBackHandler
import id.walt.walletdemo.compose.ui.LocalWalletDemoBranding
import id.walt.walletdemo.compose.ui.WalletUiTestTags
import id.walt.walletdemo.compose.ui.components.*
import id.walt.walletdemo.compose.ui.resources.*
import org.jetbrains.compose.resources.stringResource

/** Choose and confirm are separate screens; existing 4–8 digit PINs remain valid for unlock. */
@Composable
internal fun PinScreen(
    controller: WalletDemoController,
    auth: WalletAuthState.PinEntry,
    isBusy: Boolean,
    biometricAvailable: Boolean,
) {
    val setup = auth as? WalletAuthState.Setup
    val login = auth as? WalletAuthState.Login
    val confirming = setup?.step == PinSetupStep.Confirm
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    val inputFocus = remember { FocusRequester() }
    val biometricUnlockEnabled = controller.isBiometricUnlockEnabled()
    val biometricPromptPending = login != null && !login.biometricPromptConsumed &&
        biometricUnlockEnabled && biometricAvailable
    val error = setup?.error ?: login?.error
    val value = if (confirming) setup.confirmation else setup?.pin ?: login?.pin.orEmpty()
    val digits = WalletDemoController.SetupPinLength
    LaunchedEffect(login != null, biometricUnlockEnabled, biometricAvailable) {
        if (login != null && biometricUnlockEnabled && biometricAvailable) controller.unlockWithBiometrics()
    }
    LaunchedEffect(login != null, setup?.step, isBusy, biometricPromptPending, windowFocused) {
        if (isBusy || biometricPromptPending || !windowFocused) {
            focus.clearFocus()
        } else {
            // Let the host attach the secure editor before requesting its input session.
            withFrameNanos { }
            inputFocus.requestFocus()
            keyboard?.show()
        }
    }
    fun submit() {
        if (!isBusy && (setup == null || value.length == digits)) {
            focus.clearFocus()
            controller.submitPin()
        }
    }
    val primary = WalletAction(
        stringResource(when {
            setup == null -> Res.string.pin_unlock
            confirming -> Res.string.pin_confirmation_label
            else -> Res.string.pin_continue
        }),
        onClick = { submit() },
        enabled = !isBusy && if (setup != null) value.length == digits else value.length in 4..8,
        testTag = WalletUiTestTags.PinSubmitButton,
        icon = if (setup != null && !confirming) WalletSymbol.Next else WalletSymbol.Lock,
    )
    SystemBackHandler(enabled = confirming) { if (!isBusy) { focus.clearFocus(); controller.editSetupPin() } }
    val secondary = when {
        confirming -> WalletAction(stringResource(Res.string.pin_back),
            { focus.clearFocus(); controller.editSetupPin() }, !isBusy, WalletUiTestTags.PinBackButton)
        login != null && biometricUnlockEnabled && biometricAvailable ->
            WalletAction(stringResource(Res.string.pin_unlock_biometrics),
                { focus.clearFocus(); controller.unlockWithBiometrics(force = true) }, !isBusy,
                WalletUiTestTags.PinBiometricButton, WalletSymbol.Lock)
        else -> null
    }
    Scaffold(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding().testTag(WalletUiTestTags.PinScreen),
        bottomBar = { WalletActionBar(primary, secondary) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
            .wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 640.dp).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(LocalWalletDemoBranding.current.appTitle, style = MaterialTheme.typography.headlineSmall)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (setup != null) Text(stringResource(if (confirming) Res.string.pin_step_confirm else Res.string.pin_step_choose),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(stringResource(when {
                    setup == null -> Res.string.pin_enter
                    confirming -> Res.string.pin_confirm
                    else -> Res.string.pin_create
                }), style = MaterialTheme.typography.headlineMedium)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WalletPinInput(
                    value = value,
                    onValueChange = if (confirming) controller::updatePinConfirmation else controller::updatePin,
                    label = stringResource(if (confirming) Res.string.pin_confirmation_label else Res.string.pin_label),
                    progressDescription = stringResource(Res.string.pin_digit_progress, value.length, if (setup != null) digits else 8),
                    maxLength = if (setup != null) digits else 8,
                    digitCount = if (setup != null) digits else maxOf(digits, value.length),
                    allowUnicodeDigits = setup == null,
                    enabled = !isBusy,
                    isError = error != null,
                    onSubmit = { submit() },
                    modifier = Modifier.focusRequester(inputFocus).testTag(if (confirming) WalletUiTestTags.PinConfirmationInput else WalletUiTestTags.PinInput),
                )
                if (setup == null) Text(stringResource(Res.string.pin_existing_length),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(stringResource(when {
                setup == null -> Res.string.pin_enter_help
                confirming -> Res.string.pin_confirm_help
                else -> Res.string.pin_create_help
            }), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (confirming && biometricAvailable) SettingsNotice(stringResource(Res.string.pin_biometric_next))
            if (isBusy) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(24.dp))
                Text(stringResource(Res.string.pin_authenticating))
            }
            error?.let { SettingsNotice(it, error = true) }
        }
    }
}
