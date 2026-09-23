package id.walt.walletdemo.compose.ui.screens

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import id.walt.walletdemo.compose.ui.components.SettingsSection
import id.walt.walletdemo.compose.ui.components.SettingsToggleRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.WalletAuthState
import id.walt.walletdemo.compose.logic.WalletDemoController
import id.walt.walletdemo.compose.ui.LocalWalletDemoBranding
import id.walt.walletdemo.compose.ui.WalletUiTestTags

@Composable
internal fun PinScreen(
    controller: WalletDemoController,
    auth: WalletAuthState.PinEntry,
    isBusy: Boolean,
    biometricAvailable: Boolean,
) {
    val setup = auth as? WalletAuthState.Setup
    val login = auth as? WalletAuthState.Login
    val pin = when (auth) {
        is WalletAuthState.Setup -> auth.pin
        is WalletAuthState.Login -> auth.pin
    }
    val error = when (auth) {
        is WalletAuthState.Setup -> auth.error
        is WalletAuthState.Login -> auth.error
    }
    val biometricUnlockEnabled = controller.isBiometricUnlockEnabled()
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var hasInputFocus by remember { mutableStateOf(false) }

    fun dismissKeyboard() {
        hasInputFocus = false
        focusManager.clearFocus()
        keyboardController?.hide()
    }
    val dismissKeyboardOnScroll = remember(focusManager, keyboardController) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f && hasInputFocus) {
                    dismissKeyboard()
                }
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(login != null, biometricUnlockEnabled, biometricAvailable) {
        if (login != null && biometricUnlockEnabled && biometricAvailable) {
            controller.unlockWithBiometrics()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { dismissKeyboard() })
            }
            .nestedScroll(dismissKeyboardOnScroll)
            .verticalScroll(scrollState)
            .testTag(WalletUiTestTags.PinScreen)
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = 640.dp)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Text(
            LocalWalletDemoBranding.current.appTitle,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            if (setup != null) "Create a PIN" else "Enter your PIN",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            if (setup != null) {
                "Use 4 to 8 digits to unlock this wallet."
            } else {
                "Enter your PIN to unlock this wallet."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SettingsSection {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = controller::updatePin,
                    label = { Text("PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = if (setup != null) ImeAction.Next else ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { dismissKeyboard() }),
                    isError = error != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { hasInputFocus = it.isFocused }
                        .testTag(WalletUiTestTags.PinInput),
                    singleLine = true,
                )

                if (setup != null) {
                    OutlinedTextField(
                        value = setup.confirmation,
                        onValueChange = controller::updatePinConfirmation,
                        label = { Text("Confirm PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { dismissKeyboard() }),
                        isError = error != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { hasInputFocus = it.isFocused }
                            .testTag(WalletUiTestTags.PinConfirmationInput),
                        singleLine = true,
                    )
                }
            }
        }
        if (setup != null) {
            SettingsSection {
                SettingsToggleRow("Unlock with biometrics", setup.useBiometrics && biometricAvailable,
                    controller::updateUseBiometrics, Modifier.testTag(WalletUiTestTags.PinBiometricToggle),
                    detail = if (biometricAvailable) "Use biometrics to open the app instead of typing the PIN. Signing approval is set up next."
                        else "Biometrics are not available on this device.", enabled = biometricAvailable && !isBusy)
            }
        }

        error?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Button(
            onClick = controller::submitPin,
            enabled = !isBusy,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(WalletUiTestTags.PinSubmitButton),
        ) {
            Text(if (setup != null) "Set PIN" else "Unlock")
        }

        if (login != null && biometricUnlockEnabled && biometricAvailable) {
            OutlinedButton(
                onClick = { controller.unlockWithBiometrics(force = true) },
                enabled = !isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(WalletUiTestTags.PinBiometricButton),
            ) {
                Text("Unlock with biometrics")
            }
        }
    }
}

@Composable
internal fun PinStorageUnavailableScreen(
    controller: WalletDemoController,
    message: String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Text(
            LocalWalletDemoBranding.current.appTitle,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text("PIN storage unavailable", style = MaterialTheme.typography.titleMedium)
        Text(
            "$message. The wallet remains locked.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Button(
            onClick = controller::retryPinStorage,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("wallet.pinStorageRetryButton"),
        ) {
            Text("Retry")
        }
    }
}
