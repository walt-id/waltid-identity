package id.walt.walletdemo.compose.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.WalletDemoUiState
import id.walt.walletdemo.compose.ui.WalletUiTestTags
import id.walt.walletdemo.compose.ui.WalletBrandBlue

@Composable
internal fun WalletHeader(
    state: WalletDemoUiState,
    scanEnabled: Boolean,
    destinationTitle: String?,
    backTestTag: String = WalletUiTestTags.DetailsBack,
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
            if (destinationTitle != null) {
                Row(modifier = Modifier.weight(1f)) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag(backTestTag),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        destinationTitle,
                        modifier = Modifier.padding(top = 10.dp),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            } else {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = WaltIdLogo,
                        contentDescription = "walt.id",
                        tint = WalletBrandBlue,
                        modifier = Modifier.size(30.dp),
                    )
                    Text(
                        "Demo Wallet",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HeaderIconButton(
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
                    HeaderIconButton(
                        onClick = onSettings,
                        modifier = Modifier.testTag(WalletUiTestTags.SettingsAction),
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
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
private fun HeaderIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(44.dp)
            .clip(shape)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape),
    ) {
        content()
    }
}

@Composable
internal fun WalletSheetHeader(
    title: String,
    onBack: (() -> Unit)?,
    onClose: () -> Unit,
    enabled: Boolean = true,
    backTestTag: String = WalletUiTestTags.ReviewTechnicalDetailsBack,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                enabled = enabled,
                modifier = Modifier.testTag(backTestTag),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        if (onBack == null) {
            IconButton(
                onClick = onClose,
                enabled = enabled,
                modifier = Modifier.testTag(WalletUiTestTags.InteractionDismiss),
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }
    }
}

@Composable
internal fun WalletScanIcon() {
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
