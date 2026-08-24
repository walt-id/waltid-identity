package id.walt.walletdemo.compose.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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
    var biometricAvailable by remember { mutableStateOf(false) }
    val biometricUnlockEnabled = controller.isBiometricUnlockEnabled()

    LaunchedEffect(Unit) {
        biometricAvailable = controller.isBiometricUnlockAvailable()
    }
    LaunchedEffect(login != null, biometricUnlockEnabled, biometricAvailable) {
        if (login != null && biometricUnlockEnabled && biometricAvailable) {
            controller.unlockWithBiometrics()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
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
                "Use 4 to 8 digits for this local demo unlock flow."
            } else {
                "Unlock the local demo wallet."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = pin,
            onValueChange = controller::updatePin,
            label = { Text("PIN") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            isError = error != null,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(WalletUiTestTags.PinInput),
            singleLine = true,
        )

        if (setup != null) {
            OutlinedTextField(
                value = setup.confirmation,
                onValueChange = controller::updatePinConfirmation,
                label = { Text("Confirm PIN") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                isError = error != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(WalletUiTestTags.PinConfirmationInput),
                singleLine = true,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(WalletUiTestTags.PinBiometricToggle),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Unlock with biometrics", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (biometricAvailable) {
                            "Use Face ID or fingerprint instead of typing the PIN. The PIN remains a fallback."
                        } else {
                            "Biometrics are not available on this device."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = setup.useBiometrics && biometricAvailable,
                    onCheckedChange = controller::updateUseBiometrics,
                    enabled = biometricAvailable && !isBusy,
                )
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
