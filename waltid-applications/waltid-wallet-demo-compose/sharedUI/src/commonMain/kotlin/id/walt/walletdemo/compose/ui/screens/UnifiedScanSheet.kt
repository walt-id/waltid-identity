package id.walt.walletdemo.compose.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.WalletInteractionClassification
import id.walt.walletdemo.compose.ui.WalletUiTestTags
import id.walt.walletdemo.compose.ui.components.QrScannerDialog

@Composable
internal fun UnifiedScanSheet(
    onSubmit: (String) -> WalletInteractionClassification,
    onAccepted: () -> Unit,
    onDismiss: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var scannerVisible by remember { mutableStateOf(true) }
    var accepted by remember { mutableStateOf(false) }

    fun submit(value: String) {
        if (accepted) return
        when (val classification = onSubmit(value)) {
            is WalletInteractionClassification.Supported -> {
                accepted = true
                input = ""
                error = null
                onAccepted()
            }
            is WalletInteractionClassification.Invalid -> error = classification.message
            is WalletInteractionClassification.Unsupported -> error = classification.message
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(WalletUiTestTags.ScanSheet)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Scan", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Scan a credential offer or information request.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(WalletUiTestTags.ScanDismiss),
            ) {
                Text("Close")
            }
        }

        OutlinedTextField(
            value = input,
            onValueChange = {
                input = it
                error = null
            },
            label = { Text("Wallet link") },
            supportingText = error?.let { message -> { Text(message) } },
            isError = error != null,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(WalletUiTestTags.ScanInput),
            enabled = !accepted,
            keyboardOptions = KeyboardOptions(
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Uri,
            ),
            minLines = 2,
            maxLines = 4,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = { scannerVisible = true },
                enabled = !accepted,
                modifier = Modifier
                    .weight(1f)
                    .testTag(WalletUiTestTags.ScanCamera),
            ) {
                Text("Open camera")
            }
            Button(
                onClick = { submit(input) },
                enabled = !accepted && input.isNotBlank(),
                modifier = Modifier
                    .weight(1f)
                    .testTag(WalletUiTestTags.ScanSubmit),
            ) {
                Text("Continue")
            }
        }
    }

    if (scannerVisible) {
        QrScannerDialog(
            onDismiss = { scannerVisible = false },
            onCodeScanned = { rawValue ->
                scannerVisible = false
                submit(rawValue)
            },
        )
    }
}
