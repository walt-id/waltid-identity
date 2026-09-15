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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import id.walt.walletdemo.compose.logic.WalletDemoSigningProtection
import id.walt.walletdemo.compose.logic.WalletDemoSigningProtectionAvailability
import id.walt.walletdemo.compose.logic.WalletDemoSigningProtectionMode
import id.walt.walletdemo.compose.logic.WalletDemoUiState
import id.walt.walletdemo.compose.logic.WalletSessionState
import id.walt.walletdemo.compose.logic.displayMessage
import id.walt.walletdemo.compose.logic.isBusy
import id.walt.walletdemo.compose.ui.SystemBackHandler
import id.walt.walletdemo.compose.ui.WalletUiTestTags
import id.walt.walletdemo.compose.ui.components.SigningProtectionChoice
import id.walt.walletdemo.compose.ui.components.title

@Composable
internal fun SettingsScreen(
    state: WalletDemoUiState,
    onShowDcApiPresentationPreviewChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onLock: () -> Unit,
    onResetWallet: () -> Unit,
    onSignOut: (() -> Unit)? = null,
    onRequestSigningProtectionChange: (WalletDemoSigningProtection) -> Unit,
    onConfirmSigningProtectionChange: () -> Unit,
    onCancelSigningProtectionChange: () -> Unit,
) {
    val ready = state.session as? WalletSessionState.Ready
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
            SettingsCopyRow(
                title = "Public JWK",
                value = ready?.publicJwk.orEmpty().ifBlank { "Not available" },
                valueTag = WalletUiTestTags.SettingsPublicJwk,
                copyTag = WalletUiTestTags.SettingsPublicJwkCopy,
                onCopy = { text -> clipboard.setText(AnnotatedString(text)) },
            )
            if (state.pinLockEnabled) {
                SigningProtectionSettings(
                    state = state,
                    ready = ready,
                    onRequestChange = onRequestSigningProtectionChange,
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(WalletUiTestTags.SettingsCredentialSharing),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Credential Sharing",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                "Show Walt Wallet preview for DC API Presentation",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                "When off, Digital Credentials presentations skip the wallet review and continue from the system picker to biometrics.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = state.showDcApiPresentationPreview,
                            onCheckedChange = onShowDcApiPresentationPreviewChange,
                            modifier = Modifier.testTag(WalletUiTestTags.SettingsShowDcApiPreview),
                        )
                    }
                }
                OutlinedButton(
                    onClick = onLock,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(WalletUiTestTags.SettingsLock),
                ) {
                    Text("Lock")
                }
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
            if (onSignOut != null) {
                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(WalletUiTestTags.SettingsSignOut),
                ) {
                    Text("Sign out")
                }
            }
        }
    }

    state.pendingSigningProtectionChange?.let { target ->
        AlertDialog(
            onDismissRequest = onCancelSigningProtectionChange,
            title = { Text("Change signing protection?") },
            text = {
                Text(
                    "Changing to ${target.title().lowercase()} creates a new wallet key and DID. " +
                        "Your current credentials will be removed and must be issued again.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = onConfirmSigningProtectionChange,
                    modifier = Modifier.testTag(WalletUiTestTags.SigningProtectionConfirm),
                ) {
                    Text("Recreate wallet")
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelSigningProtectionChange) {
                    Text("Cancel")
                }
            },
        )
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
private fun SigningProtectionSettings(
    state: WalletDemoUiState,
    ready: WalletSessionState.Ready?,
    onRequestChange: (WalletDemoSigningProtection) -> Unit,
) {
    val current = ready?.signingProtection
    val biometricSigningAvailable =
        state.biometricSigningAvailability == WalletDemoSigningProtectionAvailability.Available
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Signing protection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("Current: ${current?.title() ?: "Not available"}", style = MaterialTheme.typography.bodyLarge)
        Text(
            "Changing signing protection creates a new wallet key and DID.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        when (state.signingProtectionMode) {
            WalletDemoSigningProtectionMode.Optional -> {
                SigningProtectionChoice(
                    protection = WalletDemoSigningProtection.Biometric,
                    selected = state.selectedSigningProtection == WalletDemoSigningProtection.Biometric,
                    enabled = biometricSigningAvailable && !state.isBusy,
                    testTag = WalletUiTestTags.SigningProtectionBiometric,
                    onSelect = { onRequestChange(WalletDemoSigningProtection.Biometric) },
                )
                SigningProtectionChoice(
                    protection = WalletDemoSigningProtection.None,
                    selected = state.selectedSigningProtection == WalletDemoSigningProtection.None,
                    enabled = !state.isBusy,
                    testTag = WalletUiTestTags.SigningProtectionNone,
                    onSelect = { onRequestChange(WalletDemoSigningProtection.None) },
                )
            }
            WalletDemoSigningProtectionMode.Required,
            WalletDemoSigningProtectionMode.Disabled,
            -> {
                val required = state.signingProtectionMode.defaultSelection
                SigningProtectionChoice(
                    protection = required,
                    selected = state.selectedSigningProtection == required,
                    enabled = ready != null && ready.signingProtection != required &&
                        !state.isBusy &&
                        (required == WalletDemoSigningProtection.None || biometricSigningAvailable),
                    testTag = if (required == WalletDemoSigningProtection.Biometric) {
                        WalletUiTestTags.SigningProtectionBiometric
                    } else {
                        WalletUiTestTags.SigningProtectionNone
                    },
                    onSelect = { onRequestChange(required) },
                )
                Text(
                    "Managed by app configuration.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
                    state.selectedSigningProtection != WalletDemoSigningProtection.Biometric ||
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
