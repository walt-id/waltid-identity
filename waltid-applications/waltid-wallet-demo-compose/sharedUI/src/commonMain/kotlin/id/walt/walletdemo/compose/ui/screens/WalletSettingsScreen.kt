package id.walt.walletdemo.compose.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.WalletSessionState
import id.walt.walletdemo.compose.logic.WalletDemoUiState
import id.walt.walletdemo.compose.ui.WalletUiTestTags
import id.walt.walletdemo.compose.ui.components.StatusCard
import id.walt.walletdemo.compose.ui.components.WalletSecondaryButton

@Composable
internal fun WalletSettingsScreen(
    state: WalletDemoUiState,
    session: WalletSessionState.Ready,
    resetEnabled: Boolean,
    onLock: () -> Unit,
    onReset: () -> Unit,
    onDismissStatus: () -> Unit,
) {
    var confirmsReset by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag(WalletUiTestTags.SettingsScreen),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StatusCard(state, onDismissStatus)
        SettingsSection("Wallet identity") {
            SettingsValue(
                label = "Wallet DID",
                value = session.did,
                modifier = Modifier.testTag(WalletUiTestTags.Did),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsValue(
                label = "Wallet key identifier",
                value = session.keyId,
                modifier = Modifier.testTag(WalletUiTestTags.KeyId),
            )
        }

        SettingsSection("Wallet controls") {
            WalletSecondaryButton(
                onClick = onLock,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(WalletUiTestTags.LockAction),
            ) {
                Text("Lock wallet")
            }
            TextButton(
                onClick = { confirmsReset = true },
                enabled = resetEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(WalletUiTestTags.ResetAction),
            ) {
                Text("Reset wallet", color = MaterialTheme.colorScheme.error)
            }
            Text(
                "Reset removes credentials, the wallet identity, key material, and the local PIN from this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (confirmsReset) {
        AlertDialog(
            onDismissRequest = { confirmsReset = false },
            modifier = Modifier.testTag(WalletUiTestTags.ResetConfirmation),
            title = { Text("Reset wallet?") },
            text = { Text("This permanently deletes all wallet data stored by this app on this device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmsReset = false
                        onReset()
                    },
                    modifier = Modifier.testTag(WalletUiTestTags.ResetConfirm),
                ) {
                    Text("Reset", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmsReset = false },
                    modifier = Modifier.testTag(WalletUiTestTags.ResetCancel),
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun SettingsValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
    }
}
