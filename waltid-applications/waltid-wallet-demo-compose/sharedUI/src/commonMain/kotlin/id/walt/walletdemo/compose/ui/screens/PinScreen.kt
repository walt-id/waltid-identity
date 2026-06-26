package id.walt.walletdemo.compose.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.WalletAuthState
import id.walt.walletdemo.compose.logic.WalletDemoController

@Composable
internal fun PinScreen(
    controller: WalletDemoController,
    auth: WalletAuthState,
    isBusy: Boolean,
) {
    val setup = auth as? WalletAuthState.Setup
    val pin = when (auth) {
        is WalletAuthState.Setup -> auth.pin
        is WalletAuthState.Login -> auth.pin
        WalletAuthState.Unlocked -> ""
    }
    val error = when (auth) {
        is WalletAuthState.Setup -> auth.error
        is WalletAuthState.Login -> auth.error
        WalletAuthState.Unlocked -> null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Text("walt.id Wallet", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
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
                .testTag("wallet.pinInput"),
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
                    .testTag("wallet.pinConfirmationInput"),
                singleLine = true,
            )
        }

        error?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Button(
            onClick = controller::submitPin,
            enabled = !isBusy,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("wallet.pinSubmitButton"),
        ) {
            Text(if (setup != null) "Set PIN" else "Unlock")
        }
    }
}
