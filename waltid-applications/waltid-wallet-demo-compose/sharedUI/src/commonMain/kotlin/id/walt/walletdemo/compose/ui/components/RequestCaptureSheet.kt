package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.WalletCaptureMode
import id.walt.walletdemo.compose.logic.WalletInteractionKind
import id.walt.walletdemo.compose.logic.WalletInteractionState
import id.walt.walletdemo.compose.ui.WalletUiTestTags

@Composable
internal fun RequestCaptureSheet(
    interaction: WalletInteractionState,
    manualValue: String,
    onManualValueChange: (String) -> Unit,
    onCodeScanned: (String) -> Unit,
    onSubmitManual: () -> Unit,
    onShowManual: () -> Unit,
    onShowScanner: () -> Unit,
    onSwitchFlow: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val kind = when (interaction) {
        is WalletInteractionState.Capturing -> interaction.kind
        is WalletInteractionState.Resolving -> interaction.request.kind
        is WalletInteractionState.Validating -> interaction.request.kind
        is WalletInteractionState.WrongRequestType -> interaction.expected
        is WalletInteractionState.Failure -> interaction.kind
        else -> WalletInteractionKind.Receive
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(WalletUiTestTags.RequestCaptureSheet),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (kind == WalletInteractionKind.Receive) "Receive a credential" else "Share information",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (kind == WalletInteractionKind.Receive) {
                    "Scan the credential offer QR code."
                } else {
                    "Scan the request QR code from the service asking for information."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        when (interaction) {
            is WalletInteractionState.Capturing -> {
                if (interaction.mode == WalletCaptureMode.Scanner) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(4f / 3f),
                        contentAlignment = Alignment.Center,
                    ) {
                        PlatformQrScanner(
                            modifier = Modifier.fillMaxSize(),
                            onCodeScanned = onCodeScanned,
                        )
                    }
                    TextButton(onClick = onShowManual) { Text("Enter link manually") }
                } else {
                    OutlinedTextField(
                        value = manualValue,
                        onValueChange = onManualValueChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(WalletUiTestTags.RequestManualInput),
                        label = { Text(if (kind == WalletInteractionKind.Receive) "Credential offer link" else "Presentation request link") },
                        minLines = 3,
                        maxLines = 6,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        supportingText = { Text("Long links can be pasted here.") },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                onSubmitManual()
                            },
                            enabled = manualValue.isNotBlank(),
                        ) { Text("Continue") }
                        TextButton(onClick = onShowScanner) { Text("Scan QR instead") }
                    }
                }
                interaction.error?.let { CaptureError(it) }
            }
            is WalletInteractionState.Validating,
            is WalletInteractionState.Resolving,
            -> Row(
                modifier = Modifier.padding(vertical = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator()
                Column {
                    Text("Checking request", style = MaterialTheme.typography.titleMedium)
                    Text("The scanner will stay open if this request cannot be resolved.")
                }
            }
            is WalletInteractionState.WrongRequestType -> {
                CaptureError(
                    if (interaction.detected.kind == WalletInteractionKind.Receive) {
                        "This is a credential offer, not a presentation request."
                    } else {
                        "This is a presentation request, not a credential offer."
                    }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onSwitchFlow) {
                        Text(if (interaction.detected.kind == WalletInteractionKind.Receive) "Switch to Receive" else "Switch to Present")
                    }
                    TextButton(onClick = onShowScanner) { Text("Scan another") }
                }
            }
            is WalletInteractionState.Failure -> {
                CaptureError(interaction.message)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (interaction.recoverable) Button(onClick = onRetry) { Text("Try again") }
                    OutlinedButton(onClick = onShowManual) { Text("Edit link") }
                }
            }
            else -> Unit
        }

        TextButton(onClick = onCancel) { Text("Cancel") }
    }
}

@Composable
private fun CaptureError(message: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Request not ready", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}
