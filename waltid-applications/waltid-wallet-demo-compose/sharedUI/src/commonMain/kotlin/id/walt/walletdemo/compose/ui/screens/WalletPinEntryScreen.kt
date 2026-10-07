package id.walt.walletdemo.compose.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.*
import id.walt.walletdemo.compose.ui.LocalWalletDemoBranding
import id.walt.walletdemo.compose.ui.LocalWalletVisualPreferences
import id.walt.walletdemo.compose.ui.WalletUiTestTags
import id.walt.walletdemo.compose.ui.components.*
import id.walt.walletdemo.compose.ui.resources.*
import org.jetbrains.compose.resources.stringResource

/** One editor survives creation steps, unlock retries, and Change PIN. */
@Composable
internal fun WalletPinEntryScreen(
    access: WalletAccessState,
    onValueChange: (String, Boolean) -> Unit,
    onClear: () -> Unit,
    onBack: (() -> Unit)?,
    onRetry: () -> Unit,
    onBiometrics: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
) {
    val entry = access.pinEntry ?: return
    val setup = entry as? WalletAuthState.Setup
    val login = entry as? WalletAuthState.Login
    val confirming = setup?.step == PinSetupStep.Confirm
    val changing = access.pinChange != null
    val value = if (confirming) setup.confirmation else setup?.pin ?: login?.pin.orEmpty()
    val error = (access.operation as? WalletAccessOperation.RetryPin)?.message ?: setup?.error ?: login?.error ?: login?.biometricOutcome?.fallbackMessage()
    val inputFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    val pendingBiometrics = !changing && login != null && !login.biometricPromptConsumed && access.biometricAvailable && access.biometricEnabled
    val biometricActive = access.operation == WalletAccessOperation.Biometrics || pendingBiometrics
    var focusRequest by remember { mutableIntStateOf(0) }
    val step = when { !changing && login != null -> 0; changing && login != null -> 1; confirming -> 3; else -> 2 }
    LaunchedEffect(step, biometricActive, windowFocused, focusRequest) {
        if (windowFocused && !biometricActive) {
            withFrameNanos { }
            inputFocus.requestFocus()
            keyboard?.show()
        } else if (biometricActive) { focus.clearFocus(); keyboard?.hide() }
    }
    val reduceMotion = LocalWalletVisualPreferences.current.reduceMotion
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    WalletAccessScaffold(modifier = Modifier.testTag(WalletUiTestTags.PinScreen), header = {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (changing) stringResource(Res.string.settings_change_pin) else LocalWalletDemoBranding.current.appTitle,
                Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            onCancel?.let { cancel ->
                IconButton(cancel, Modifier.testTag(WalletUiTestTags.PinCancelButton), enabled = !access.isBusy) {
                    Icon(Icons.Filled.Close, stringResource(Res.string.pin_cancel))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        AnimatedContent(step, modifier = Modifier.heightIn(min = 120.dp),
            transitionSpec = { walletNavigationMotion(targetState > initialState, reduceMotion, rtl) }, label = "PIN heading") { shown ->
            Column(Modifier.fillMaxWidth()) {
                if (shown >= 2) {
                    Text(stringResource(if (shown == 3) Res.string.pin_step_confirm else Res.string.pin_step_choose),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                }
                Text(stringResource(when (shown) {
                    0 -> Res.string.pin_enter
                    1 -> Res.string.pin_current
                    3 -> Res.string.pin_confirm
                    else -> if (changing) Res.string.pin_create_new else Res.string.pin_create
                }), style = MaterialTheme.typography.headlineMedium)
            }
        }
    }, input = {
        WalletPinInput(value, { onValueChange(it, confirming) },
            label = stringResource(if (confirming) Res.string.pin_confirmation_label else Res.string.pin_label),
            progressDescription = stringResource(Res.string.pin_digit_progress, value.length, WalletDemoController.PinLength),
            digitCount = WalletDemoController.PinLength, enabled = !access.isBusy, isError = error != null,
            onSubmit = { if (!access.isBusy && value.length == WalletDemoController.PinLength) onRetry() },
            modifier = Modifier.focusRequester(inputFocus).testTag(if (confirming) WalletUiTestTags.PinConfirmationInput else WalletUiTestTags.PinInput))
    }, feedback = {
        when {
            access.operation == WalletAccessOperation.CheckingPin || access.operation == WalletAccessOperation.SavingPin ->
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            error != null -> Text(error, Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
    }, actions = {
        WalletActions(
            primary = if (access.operation is WalletAccessOperation.RetryPin) WalletAction(stringResource(Res.string.settings_try_again), onRetry,
                testTag = WalletUiTestTags.PinSubmitButton) else null,
            secondary = if (onBiometrics != null) WalletAction(stringResource(access.biometricKind.retryResource), onBiometrics,
                !access.isBusy, WalletUiTestTags.PinBiometricButton) else WalletAction(stringResource(Res.string.pin_clear),
                { onClear(); focusRequest += 1 }, !access.isBusy && (value.isNotEmpty() || error != null), WalletUiTestTags.PinClearButton),
            tertiary = if (onBiometrics != null) WalletAction(stringResource(Res.string.pin_clear),
                { onClear(); focusRequest += 1 }, !access.isBusy && (value.isNotEmpty() || error != null), WalletUiTestTags.PinClearButton)
                else onBack?.let { WalletAction(stringResource(Res.string.pin_back), it, !access.isBusy, WalletUiTestTags.PinBackButton) },
        )
    })
}

internal val DemoBiometricKind.retryResource get() = when (this) {
    DemoBiometricKind.FaceId -> Res.string.pin_retry_face_id
    DemoBiometricKind.TouchId -> Res.string.pin_retry_touch_id
    DemoBiometricKind.Generic -> Res.string.pin_retry_biometrics
}
