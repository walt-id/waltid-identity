package id.walt.walletdemo.compose.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.WalletAuthState
import id.walt.walletdemo.compose.logic.WalletDemoController
import id.walt.walletdemo.compose.ui.LocalWalletDemoBranding
import id.walt.walletdemo.compose.ui.SystemBackHandler
import id.walt.walletdemo.compose.ui.WalletUiTestTags
import id.walt.walletdemo.compose.ui.components.*
import id.walt.walletdemo.compose.ui.resources.*
import org.jetbrains.compose.resources.stringResource

/** New app PINs use six digits. The existing store and legacy unlock formats stay compatible. */
@Composable
internal fun PinScreen(
    controller: WalletDemoController,
    auth: WalletAuthState.PinEntry,
    isBusy: Boolean,
    biometricAvailable: Boolean,
    initialPage: PinSetupPage = PinSetupPage.Create,
) {
    val setup = auth as? WalletAuthState.Setup
    val login = auth as? WalletAuthState.Login
    var page by rememberSaveable { mutableStateOf(initialPage) }
    var mismatch by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val biometricUnlockEnabled = controller.isBiometricUnlockEnabled()
    val confirmation = setup != null && page == PinSetupPage.Confirm
    val biometricChoice = setup != null && page == PinSetupPage.Biometrics
    val title = stringResource(when {
        login != null -> Res.string.pin_enter
        confirmation -> Res.string.pin_confirm
        biometricChoice -> Res.string.pin_biometric_title
        else -> Res.string.pin_create
    })
    val description = stringResource(when {
        login != null -> Res.string.pin_enter_help
        confirmation -> Res.string.pin_confirm_help
        biometricChoice && !biometricAvailable -> Res.string.pin_biometric_unavailable
        biometricChoice -> Res.string.pin_biometric_help
        else -> Res.string.pin_create_help
    })
    val error = if (mismatch) stringResource(Res.string.pin_mismatch) else setup?.error ?: login?.error
    val value = if (confirmation) setup.confirmation else setup?.pin ?: login?.pin.orEmpty()
    fun back() {
        focus.clearFocus()
        mismatch = false
        page = if (page == PinSetupPage.Biometrics) PinSetupPage.Confirm else PinSetupPage.Create
    }
    SystemBackHandler(enabled = setup != null && page != PinSetupPage.Create && !isBusy) { back() }
    // A restored presentation step must never outlive its in-memory PIN draft.
    LaunchedEffect(setup?.pin) { if (setup != null && setup.pin.isEmpty()) page = PinSetupPage.Create }
    LaunchedEffect(login != null, biometricUnlockEnabled, biometricAvailable) {
        if (login != null && biometricUnlockEnabled && biometricAvailable) controller.unlockWithBiometrics()
    }
    val primary = when {
        login != null -> WalletAction(stringResource(Res.string.pin_unlock), {
            focus.clearFocus(); controller.submitPin()
        }, !isBusy, WalletUiTestTags.PinSubmitButton, WalletSymbol.Lock)
        biometricChoice -> WalletAction(stringResource(when {
            !biometricAvailable -> Res.string.pin_only
            setup.useBiometrics && !isBusy -> Res.string.pin_finish
            else -> Res.string.pin_enable_biometrics
        }), {
            if (biometricAvailable && !setup.useBiometrics) controller.updateUseBiometrics(true)
            else {
                if (!biometricAvailable) controller.updateUseBiometrics(false)
                controller.submitPin()
            }
        }, !isBusy, WalletUiTestTags.PinSubmitButton, WalletSymbol.Lock)
        else -> WalletAction(stringResource(Res.string.pin_continue), {
            focus.clearFocus()
            if (confirmation && setup.pin != setup.confirmation) mismatch = true
            else {
                mismatch = false
                page = if (confirmation) PinSetupPage.Biometrics else PinSetupPage.Confirm
            }
        }, !isBusy && value.length == 6, WalletUiTestTags.PinSubmitButton, WalletSymbol.Next)
    }
    val secondary = when {
        biometricChoice && biometricAvailable -> WalletAction(stringResource(Res.string.pin_only), {
            controller.updateUseBiometrics(false); controller.submitPin()
        }, !isBusy, "wallet.pinSkipBiometrics", WalletSymbol.Next)
        setup != null && page != PinSetupPage.Create -> WalletAction(stringResource(Res.string.settings_back), { back() },
            !isBusy, "wallet.pinBack", WalletSymbol.Back)
        login != null && biometricUnlockEnabled && biometricAvailable -> WalletAction(stringResource(Res.string.pin_unlock_biometrics),
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
            verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Text(LocalWalletDemoBranding.current.appTitle, style = MaterialTheme.typography.headlineSmall)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.headlineMedium)
                Text(description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!biometricChoice) WalletSection {
                OutlinedTextField(
                    value = value,
                    onValueChange = { input ->
                        mismatch = false
                        val digits = input.filter { it in '0'..'9' }.take(if (setup != null) 6 else 8)
                        if (confirmation) controller.updatePinConfirmation(digits) else controller.updatePin(digits)
                    },
                    label = { Text(stringResource(if (confirmation) Res.string.pin_confirmation_label else Res.string.pin_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    enabled = !isBusy, isError = error != null, singleLine = true,
                    modifier = Modifier.padding(16.dp).fillMaxWidth().testTag(
                        if (confirmation) WalletUiTestTags.PinConfirmationInput else WalletUiTestTags.PinInput),
                )
            }
            if (biometricChoice && biometricAvailable && setup.useBiometrics && !isBusy) {
                SettingsNotice(stringResource(Res.string.pin_biometric_enabled))
            }
            if (isBusy) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(24.dp))
                Text(stringResource(Res.string.pin_authenticating))
            }
            error?.let { SettingsNotice(it, error = true) }
        }
    }
}

internal enum class PinSetupPage { Create, Confirm, Biometrics }
