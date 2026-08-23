package id.walt.walletdemo.compose.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.WalletDemoCredential
import id.walt.walletdemo.compose.logic.WalletDemoTab
import id.walt.walletdemo.compose.logic.WalletDemoUiState
import id.walt.walletdemo.compose.logic.toCredentialDetails
import id.walt.walletdemo.compose.ui.WalletUiTestTags
import id.walt.walletdemo.compose.ui.components.CredentialCard
import id.walt.walletdemo.compose.ui.components.StatusCard
import id.walt.walletdemo.compose.ui.components.WalletPrimaryButton

@Composable
internal fun CredentialsTab(
    state: WalletDemoUiState,
    credentials: List<WalletDemoCredential>,
    onCredentialClick: (String) -> Unit,
    onScan: () -> Unit,
    scanEnabled: Boolean,
    onDismissStatus: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.selectedTab == WalletDemoTab.Credentials) {
            StatusCard(state, onDismissStatus)
        }
        if (credentials.isEmpty()) {
            EmptyCredentialsState(onScan, scanEnabled)
        } else {
            credentials.forEach { credential ->
                CredentialCard(
                    details = credential.toCredentialDetails(),
                    onClick = { onCredentialClick(credential.id) },
                )
            }
        }
    }
}

@Composable
private fun EmptyCredentialsState(onScan: () -> Unit, scanEnabled: Boolean) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(WalletUiTestTags.CredentialsEmpty),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WalletEmptyIcon()
            Text("No credentials yet", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Text(
                "Scan a credential offer to add your first one.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                WalletPrimaryButton(
                    onClick = onScan,
                    enabled = scanEnabled,
                    compact = true,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp,
                        vertical = 6.dp,
                    ),
                    modifier = Modifier.testTag(WalletUiTestTags.ScanEmptyAction),
                ) {
                    WalletScanIcon()
                    Text("Scan a code", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun WalletEmptyIcon() {
    val color = LocalContentColor.current
    Canvas(modifier = Modifier.size(30.dp)) {
        val stroke = 1.8.dp.toPx()
        val left = 3.dp.toPx()
        val top = 7.dp.toPx()
        val right = size.width - 3.dp.toPx()
        val bottom = size.height - 5.dp.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(left, top),
            size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
            style = androidx.compose.ui.graphics.drawscope.Stroke(stroke),
        )
        drawLine(color, Offset(left + 3.dp.toPx(), top), Offset(right - 2.dp.toPx(), top), strokeWidth = stroke)
        drawCircle(color, radius = 1.5.dp.toPx(), center = Offset(right - 6.dp.toPx(), (top + bottom) / 2))
    }
}
