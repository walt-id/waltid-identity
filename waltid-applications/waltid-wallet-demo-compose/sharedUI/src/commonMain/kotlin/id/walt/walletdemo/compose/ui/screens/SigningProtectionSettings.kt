package id.walt.walletdemo.compose.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.WalletDemoSigningProtection
import id.walt.walletdemo.compose.logic.WalletDemoSigningProtectionAvailability
import id.walt.walletdemo.compose.logic.WalletDemoSigningProtectionMode
import id.walt.walletdemo.compose.logic.WalletDemoUiState
import id.walt.walletdemo.compose.logic.WalletSessionState
import id.walt.walletdemo.compose.logic.displayMessage
import id.walt.walletdemo.compose.logic.isBusy
import id.walt.walletdemo.compose.ui.WalletUiTestTags
import id.walt.walletdemo.compose.ui.components.SigningProtectionChoice
import id.walt.walletdemo.compose.ui.components.title

@Composable
internal fun SigningProtectionSettings(
    state: WalletDemoUiState,
    ready: WalletSessionState.Ready?,
    onRequestChange: (WalletDemoSigningProtection) -> Unit,
) {
    val current = ready?.signingProtection
    val biometricSigningAvailable =
        state.biometricSigningAvailability == WalletDemoSigningProtectionAvailability.Available
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Signing protection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("Current: ${current?.title() ?: "Unavailable"}", style = MaterialTheme.typography.bodyLarge)
        Text(
            "Changing signing protection creates a new wallet key and DID.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        WalletDemoSigningProtection.entries.filter(state.signingProtectionMode::allows).forEach { protection ->
            SigningProtectionChoice(
                protection = protection,
                selected = state.selectedSigningProtection == protection,
                enabled = !state.isBusy && (!protection.requiresBiometrics || biometricSigningAvailable),
                testTag = when (protection) {
                    WalletDemoSigningProtection.None -> WalletUiTestTags.SigningProtectionNone
                    WalletDemoSigningProtection.Biometric -> WalletUiTestTags.SigningProtectionBiometric
                    WalletDemoSigningProtection.BiometricPerUse -> WalletUiTestTags.SigningProtectionBiometricPerUse
                },
                onSelect = { onRequestChange(protection) },
            )
        }

        if (!biometricSigningAvailable && state.signingProtectionMode != WalletDemoSigningProtectionMode.Disabled) {
            Text(
                state.biometricSigningAvailability?.displayMessage()
                    ?: "Checking strong biometric availability...",
                style = MaterialTheme.typography.bodySmall,
                color = if (state.biometricSigningAvailability == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
                modifier = Modifier.testTag(WalletUiTestTags.SigningProtectionAvailability),
            )
        }

        if (ready == null) {
            OutlinedButton(
                onClick = { onRequestChange(state.selectedSigningProtection) },
                enabled = !state.isBusy && (
                    !state.selectedSigningProtection.requiresBiometrics ||
                        biometricSigningAvailable
                    ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(WalletUiTestTags.SigningProtectionRetry),
            ) {
                Text("Retry wallet setup")
            }
        }

        if (state.isChangingSigningProtection) {
            CircularProgressIndicator()
        }
        state.signingProtectionError?.let { error ->
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag(WalletUiTestTags.SigningProtectionError),
            )
        }
    }
}
