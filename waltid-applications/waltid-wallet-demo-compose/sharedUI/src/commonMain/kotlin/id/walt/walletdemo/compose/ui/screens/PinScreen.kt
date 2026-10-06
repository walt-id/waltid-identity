package id.walt.walletdemo.compose.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.PinSetupStep
import id.walt.walletdemo.compose.logic.WalletAuthState
import id.walt.walletdemo.compose.logic.WalletDemoController
import id.walt.walletdemo.compose.ui.SystemBackHandler
import id.walt.walletdemo.compose.ui.LocalWalletVisualPreferences
import id.walt.walletdemo.compose.ui.LocalWalletDemoBranding
import id.walt.walletdemo.compose.ui.WalletUiTestTags
import id.walt.walletdemo.compose.ui.components.*
import id.walt.walletdemo.compose.ui.resources.*
import org.jetbrains.compose.resources.stringResource

/** Choose, confirm and unlock use the same fixed four-digit PIN input. */
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
    val biometricUnlockEnabled = controller.isBiometricUnlockEnabled()
    val biometricPromptPending = login != null && !login.biometricPromptConsumed &&
        biometricUnlockEnabled && biometricAvailable
    val error = setup?.error ?: login?.error
    val value = if (confirming) setup.confirmation else setup?.pin ?: login?.pin.orEmpty()
    val digits = WalletDemoController.PinLength
    LaunchedEffect(login != null, biometricUnlockEnabled, biometricAvailable) {
        if (login != null && biometricUnlockEnabled && biometricAvailable) controller.unlockWithBiometrics()
    }
    fun submit() {
        if (!isBusy && value.length == digits) {
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
        enabled = !isBusy && value.length == digits,
        testTag = WalletUiTestTags.PinSubmitButton,
    )
    SystemBackHandler(enabled = confirming) { if (!isBusy) { focus.clearFocus(); controller.editSetupPin() } }
    val secondary = when {
        confirming -> WalletAction(stringResource(Res.string.pin_back),
            { focus.clearFocus(); controller.editSetupPin() }, !isBusy, WalletUiTestTags.PinBackButton)
        login != null && biometricUnlockEnabled && biometricAvailable ->
            WalletAction(stringResource(Res.string.pin_unlock_biometrics),
                { focus.clearFocus(); controller.unlockWithBiometrics(force = true) }, !isBusy,
                WalletUiTestTags.PinBiometricButton)
        else -> null
    }
    val reduceMotion = LocalWalletVisualPreferences.current.reduceMotion
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    ReviewScaffold(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().testTag(WalletUiTestTags.PinScreen)
            .wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 640.dp),
        actions = { WalletActions(primary, secondary) },
        feedback = if (isBusy || error != null) ({
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (isBusy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(if (isBusy) stringResource(Res.string.pin_authenticating) else error.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (!isBusy && error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }) else null,
    ) {
        AnimatedContent(confirming, modifier = Modifier.fillMaxWidth(),
            transitionSpec = { walletNavigationMotion(targetState, reduceMotion, rtl) }, label = "PIN step") { displayedConfirm ->
            val active = displayedConfirm == confirming
            val inputFocus = remember { FocusRequester() }
            val displayedValue = if (displayedConfirm) setup?.confirmation.orEmpty() else setup?.pin ?: login?.pin.orEmpty()
            LaunchedEffect(active, isBusy, biometricPromptPending, windowFocused) {
                if (active && !isBusy && !biometricPromptPending && windowFocused) {
                    withFrameNanos { }
                    inputFocus.requestFocus()
                    keyboard?.show()
                } else if (active) focus.clearFocus()
            }
            Column(Modifier.fillMaxWidth().walletNavigationBackground(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(LocalWalletDemoBranding.current.appTitle, style = MaterialTheme.typography.headlineSmall)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (setup != null) Text(stringResource(if (displayedConfirm) Res.string.pin_step_confirm else Res.string.pin_step_choose),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(when {
                        setup == null -> Res.string.pin_enter
                        displayedConfirm -> Res.string.pin_confirm
                        else -> Res.string.pin_create
                    }), style = MaterialTheme.typography.headlineMedium)
                }
                WalletPinInput(
                    value = displayedValue,
                    onValueChange = { input -> if (active) {
                        if (displayedConfirm) controller.updatePinConfirmation(input) else controller.updatePin(input)
                    } },
                    label = stringResource(if (displayedConfirm) Res.string.pin_confirmation_label else Res.string.pin_label),
                    progressDescription = stringResource(Res.string.pin_digit_progress, displayedValue.length, digits),
                    digitCount = digits, enabled = active && !isBusy, isError = active && error != null,
                    onSubmit = { if (active) submit() },
                    modifier = Modifier.focusRequester(inputFocus).testTag(if (displayedConfirm) WalletUiTestTags.PinConfirmationInput else WalletUiTestTags.PinInput),
                )
            }
        }
    }
}
