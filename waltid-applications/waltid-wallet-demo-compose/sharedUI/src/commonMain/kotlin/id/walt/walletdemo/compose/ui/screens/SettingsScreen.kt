package id.walt.walletdemo.compose.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.WalletSessionState
import id.walt.walletdemo.compose.ui.SystemBackHandler
import id.walt.walletdemo.compose.ui.WalletUiTestTags

@Composable
internal fun SettingsScreen(
    ready: WalletSessionState.Ready?,
    onBack: () -> Unit,
    onLock: () -> Unit,
    onResetWallet: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var confirmReset by remember { mutableStateOf(false) }

    SystemBackHandler(enabled = true, onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .testTag(WalletUiTestTags.SettingsScreen),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag(WalletUiTestTags.SettingsBack),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                )
            }
            Text(
                "Settings",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SettingsCopyRow(
                title = "Wallet DID",
                value = ready?.did.orEmpty().ifBlank { "Not available" },
                valueTag = WalletUiTestTags.SettingsDid,
                copyTag = WalletUiTestTags.SettingsDidCopy,
                onCopy = { text -> clipboard.setText(AnnotatedString(text)) },
            )
            SettingsCopyRow(
                title = "Wallet key",
                value = ready?.keyId.orEmpty().ifBlank { "Not available" },
                valueTag = WalletUiTestTags.SettingsKeyId,
                copyTag = WalletUiTestTags.SettingsKeyIdCopy,
                onCopy = { text -> clipboard.setText(AnnotatedString(text)) },
            )
            OutlinedButton(
                onClick = onLock,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(WalletUiTestTags.SettingsLock),
            ) {
                Text("Lock")
            }
            Button(
                onClick = { confirmReset = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(WalletUiTestTags.SettingsReset),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text("Reset wallet")
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset wallet?") },
            text = { Text("This deletes the wallet DID, keys, credentials, and PIN. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmReset = false
                        onResetWallet()
                    },
                    modifier = Modifier.testTag(WalletUiTestTags.SettingsResetConfirm),
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun SettingsCopyRow(
    title: String,
    value: String,
    valueTag: String,
    copyTag: String,
    onCopy: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                value,
                modifier = Modifier
                    .weight(1f)
                    .testTag(valueTag),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(
                onClick = { onCopy(value) },
                modifier = Modifier.testTag(copyTag),
            ) {
                Text("Copy")
            }
        }
    }
}
