package id.walt.walletdemo.compose.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.WalletInteractionClassification
import id.walt.walletdemo.compose.ui.WalletUiTestTags
import id.walt.walletdemo.compose.ui.exportTestTagsForPlatformAutomation
import id.walt.walletdemo.compose.ui.components.QrScannerDialog
import id.walt.walletdemo.compose.ui.components.WalletPrimaryButton
import id.walt.walletdemo.compose.ui.components.WalletSecondaryButton

@Composable
internal fun UnifiedScanSheet(
    onSubmit: (String) -> WalletInteractionClassification,
    onAccepted: () -> Unit,
    onDismiss: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var scannerVisible by remember { mutableStateOf(false) }
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
            .exportTestTagsForPlatformAutomation()
            .testTag(WalletUiTestTags.ScanSheet)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
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
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(WalletUiTestTags.ScanDismiss),
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close")
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
            singleLine = true,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            WalletSecondaryButton(
                onClick = { scannerVisible = true },
                enabled = !accepted,
                modifier = Modifier
                    .weight(1f)
                    .testTag(WalletUiTestTags.ScanCamera),
            ) {
                CameraIcon()
                Text("Camera", modifier = Modifier.padding(start = 8.dp))
            }
            WalletPrimaryButton(
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

@Composable
private fun CameraIcon() {
    val color = LocalContentColor.current
    Canvas(modifier = Modifier.size(20.dp)) {
        val stroke = 1.8.dp.toPx()
        val top = 5.dp.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(1.dp.toPx(), top),
            size = Size(size.width - 2.dp.toPx(), size.height - top - 2.dp.toPx()),
            cornerRadius = CornerRadius(3.dp.toPx()),
            style = Stroke(stroke),
        )
        drawCircle(
            color = color,
            radius = 3.5.dp.toPx(),
            center = Offset(size.width / 2, (top + size.height - 2.dp.toPx()) / 2),
            style = Stroke(stroke),
        )
        drawLine(
            color = color,
            start = Offset(6.dp.toPx(), top),
            end = Offset(8.dp.toPx(), 2.dp.toPx()),
            strokeWidth = stroke,
        )
        drawLine(
            color = color,
            start = Offset(8.dp.toPx(), 2.dp.toPx()),
            end = Offset(12.dp.toPx(), 2.dp.toPx()),
            strokeWidth = stroke,
        )
        drawLine(
            color = color,
            start = Offset(12.dp.toPx(), 2.dp.toPx()),
            end = Offset(14.dp.toPx(), top),
            strokeWidth = stroke,
        )
    }
}
