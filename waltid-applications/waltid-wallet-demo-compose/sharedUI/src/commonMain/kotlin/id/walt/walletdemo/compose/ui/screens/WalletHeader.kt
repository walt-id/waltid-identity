package id.walt.walletdemo.compose.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.WalletDemoUiState
import id.walt.walletdemo.compose.ui.WalletUiTestTags

@Composable
internal fun WalletHeader(
    state: WalletDemoUiState,
    scanEnabled: Boolean,
    showingSettings: Boolean,
    onBack: () -> Unit,
    onScan: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showingSettings) {
                Row(modifier = Modifier.weight(1f)) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag(WalletUiTestTags.SettingsBack),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        "Settings",
                        modifier = Modifier.padding(top = 10.dp),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            } else {
                Text(
                    "Credentials",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row {
                        IconButton(
                            onClick = onScan,
                            enabled = scanEnabled,
                            modifier = Modifier
                                .testTag(WalletUiTestTags.ScanAction)
                                .semantics {
                                    contentDescription = "Scan credential offer or presentation request"
                                },
                        ) {
                            WalletScanIcon()
                        }
                        IconButton(
                            onClick = onSettings,
                            modifier = Modifier.testTag(WalletUiTestTags.SettingsAction),
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                }
            }
        }
        state.warning?.let { warning ->
            WarningCard(warning)
        }
    }
}

@Composable
private fun WalletScanIcon() {
    val color = LocalContentColor.current
    Canvas(modifier = Modifier.size(24.dp)) {
        val stroke = 2.dp.toPx()
        val arm = 6.dp.toPx()
        val inset = 3.dp.toPx()
        val far = size.width - inset

        drawLine(color, start = Offset(inset, inset + arm), end = Offset(inset, inset), strokeWidth = stroke)
        drawLine(color, start = Offset(inset, inset), end = Offset(inset + arm, inset), strokeWidth = stroke)
        drawLine(color, start = Offset(far - arm, inset), end = Offset(far, inset), strokeWidth = stroke)
        drawLine(color, start = Offset(far, inset), end = Offset(far, inset + arm), strokeWidth = stroke)
        drawLine(color, start = Offset(inset, far - arm), end = Offset(inset, far), strokeWidth = stroke)
        drawLine(color, start = Offset(inset, far), end = Offset(inset + arm, far), strokeWidth = stroke)
        drawLine(color, start = Offset(far - arm, far), end = Offset(far, far), strokeWidth = stroke)
        drawLine(color, start = Offset(far, far - arm), end = Offset(far, far), strokeWidth = stroke)

        val module = 2.5.dp.toPx()
        drawRect(color, topLeft = Offset(8.dp.toPx(), 8.dp.toPx()), size = Size(module, module))
        drawRect(color, topLeft = Offset(13.5.dp.toPx(), 8.dp.toPx()), size = Size(module, module))
        drawRect(color, topLeft = Offset(8.dp.toPx(), 13.5.dp.toPx()), size = Size(module, module))
        drawRect(color, topLeft = Offset(13.5.dp.toPx(), 13.5.dp.toPx()), size = Size(module, module))
    }
}

@Composable
private fun WarningCard(message: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Text(
            text = message,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
