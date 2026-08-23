package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
internal fun UrlActionSection(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    buttonText: String,
    enabled: Boolean,
    inputEnabled: Boolean = true,
    inputTestTag: String,
    buttonTestTag: String,
    scanButtonTestTag: String,
    contentBeforeActions: @Composable ColumnScope.() -> Unit = {},
    onClick: () -> Unit,
) {
    var scannerVisible by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(inputTestTag),
            enabled = inputEnabled,
            keyboardOptions = KeyboardOptions(
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Uri,
            ),
            singleLine = true,
        )
        contentBeforeActions()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            WalletSecondaryButton(
                enabled = inputEnabled,
                onClick = { scannerVisible = true },
                modifier = Modifier.testTag(scanButtonTestTag),
            ) {
                Text("Scan QR")
            }
            WalletPrimaryButton(
                enabled = enabled,
                onClick = onClick,
                modifier = Modifier.testTag(buttonTestTag),
            ) {
                Text(buttonText)
            }
        }
    }

    if (scannerVisible) {
        QrScannerDialog(
            onDismiss = { scannerVisible = false },
            onCodeScanned = { rawValue ->
                val value = rawValue.trim()
                if (value.isNotEmpty()) {
                    onValueChange(value)
                    scannerVisible = false
                }
            },
        )
    }
}
