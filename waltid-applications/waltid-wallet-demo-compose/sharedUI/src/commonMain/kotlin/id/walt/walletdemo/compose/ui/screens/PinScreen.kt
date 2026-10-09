package id.walt.walletdemo.compose.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.WalletAuthState
import id.walt.walletdemo.compose.logic.WalletDemoController
import id.walt.walletdemo.compose.ui.LocalWalletDemoBranding
import id.walt.walletdemo.compose.ui.WalletUiTestTags
import id.walt.walletdemo.compose.ui.components.*
import id.walt.walletdemo.compose.ui.resources.*
import org.jetbrains.compose.resources.stringResource

/** App unlock uses the existing four-digit PIN policy. */
@Composable
internal fun PinScreen(
    controller: WalletDemoController,
    auth: WalletAuthState.PinEntry,
    isBusy: Boolean,
    biometricAvailable: Boolean,
) {
    val setup = auth as? WalletAuthState.Setup
    val login = auth as? WalletAuthState.Login
    val focus = LocalFocusManager.current
    val confirmationFocus = remember { FocusRequester() }
    val biometricUnlockEnabled = controller.isBiometricUnlockEnabled()
    val error = setup?.error ?: login?.error
    val inputError = error != null && (setup == null || setup.pin != setup.confirmation)
    LaunchedEffect(login != null, biometricUnlockEnabled, biometricAvailable) {
        if (login != null && biometricUnlockEnabled && biometricAvailable) controller.unlockWithBiometrics()
    }
    val primary = WalletAction(
        stringResource(if (setup != null) Res.string.pin_create_action else Res.string.pin_unlock),
        onClick = {
            focus.clearFocus()
            controller.submitPin()
        },
        enabled = !isBusy && (setup == null || (setup.pin.length == WalletDemoController.PinLength && setup.confirmation.length == WalletDemoController.PinLength)),
        testTag = WalletUiTestTags.PinSubmitButton,
        icon = WalletSymbol.Lock,
    )
    val secondary = if (login != null && biometricUnlockEnabled && biometricAvailable) {
        WalletAction(stringResource(Res.string.pin_unlock_biometrics),
            { focus.clearFocus(); controller.unlockWithBiometrics(force = true) }, !isBusy,
            WalletUiTestTags.PinBiometricButton, WalletSymbol.Lock)
    } else null
    Scaffold(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding().testTag(WalletUiTestTags.PinScreen),
        bottomBar = { WalletActionBar(primary, secondary) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
            .wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 640.dp).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(LocalWalletDemoBranding.current.appTitle, style = MaterialTheme.typography.headlineSmall)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(if (setup != null) Res.string.pin_create else Res.string.pin_enter),
                    style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(if (setup != null) Res.string.pin_create_help else Res.string.pin_enter_help),
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            WalletSection {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = setup?.pin ?: login?.pin.orEmpty(),
                        onValueChange = { input ->
                            controller.updatePin(input.filter { it in '0'..'9' }.take(WalletDemoController.PinLength))
                        },
                        label = { Text(stringResource(Res.string.pin_label)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword,
                            imeAction = if (setup != null) ImeAction.Next else ImeAction.Done),
                        keyboardActions = KeyboardActions(onNext = { confirmationFocus.requestFocus() },
                            onDone = { focus.clearFocus() }),
                        enabled = !isBusy, isError = inputError, singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag(WalletUiTestTags.PinInput),
                    )
                    if (setup != null) OutlinedTextField(
                        value = setup.confirmation,
                        onValueChange = { input ->
                            controller.updatePinConfirmation(input.filter { it in '0'..'9' }.take(WalletDemoController.PinLength))
                        },
                        label = { Text(stringResource(Res.string.pin_confirmation_label)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                        enabled = !isBusy, isError = inputError, singleLine = true,
                        modifier = Modifier.fillMaxWidth().focusRequester(confirmationFocus)
                            .testTag(WalletUiTestTags.PinConfirmationInput),
                    )
                }
            }
            if (setup != null) WalletSection {
                SettingsToggleRow(
                    title = stringResource(Res.string.pin_biometric_label),
                    detail = stringResource(when {
                        !biometricAvailable -> Res.string.pin_biometric_unavailable
                        setup.useBiometrics && !isBusy -> Res.string.pin_biometric_enabled
                        else -> Res.string.pin_biometric_help
                    }),
                    checked = setup.useBiometrics,
                    onCheckedChange = { focus.clearFocus(); controller.updateUseBiometrics(it) },
                    enabled = biometricAvailable && !isBusy,
                    modifier = Modifier.testTag(WalletUiTestTags.PinBiometricToggle),
                )
            }
            if (isBusy) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(24.dp))
                Text(stringResource(Res.string.pin_authenticating))
            }
            error?.let { SettingsNotice(it, error = true) }
        }
    }
}
