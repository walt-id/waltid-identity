package id.walt.walletdemo.compose.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.WalletDemoUiState
import id.walt.walletdemo.compose.ui.LocalWalletDemoBranding
import id.walt.walletdemo.compose.ui.WalletUiTestTags
import id.walt.walletdemo.compose.ui.components.CredentialDetailsCloseButton
import id.walt.walletdemo.compose.ui.components.CredentialDetailsOverflowMenu
import id.walt.walletdemo.compose.ui.components.StatusCard
import id.walt.walletdemo.compose.ui.resources.Res
import id.walt.walletdemo.compose.ui.resources.proximity_qr
import id.walt.walletdemo.compose.ui.resources.settings_nearby
import org.jetbrains.compose.resources.painterResource

internal val WalletHeaderHorizontalPadding = 20.dp
internal val WalletHeaderVerticalPadding = 14.dp
internal val WalletHeaderTitleRowHeight = 48.dp

@Composable
internal fun WalletHeader(
    state: WalletDemoUiState,
    onSettings: () -> Unit,
    onDismissStatus: () -> Unit,
    onToggleStatusExpanded: () -> Unit,
    onScan: (() -> Unit)? = null,
    onShareNearby: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    title: String? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = WalletHeaderHorizontalPadding, vertical = WalletHeaderVerticalPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = WalletHeaderTitleRowHeight),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            onBack?.let { back ->
                IconButton(onClick = back, modifier = Modifier.testTag(WalletUiTestTags.FlowBack)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to wallet")
                }
            }
            Text(
                title ?: LocalWalletDemoBranding.current.appTitle,
                modifier = Modifier
                    .weight(1f)
                    .testTag(WalletUiTestTags.AppTitle),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            onShareNearby?.let { nearby ->
                IconButton(onClick = nearby, modifier = Modifier.testTag(WalletUiTestTags.ProximityStartButton)) {
                    Icon(painterResource(Res.drawable.settings_nearby), "Share nearby")
                }
            }
            onScan?.let { scan ->
                IconButton(onClick = scan, modifier = Modifier.testTag(WalletUiTestTags.ScanButton)) {
                    Icon(painterResource(Res.drawable.proximity_qr), "Scan or paste a link")
                }
            }
            IconButton(
                onClick = onSettings,
                modifier = Modifier.testTag(WalletUiTestTags.SettingsButton),
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Settings",
                )
            }
        }
        StatusCard(
            state = state,
            onDismiss = onDismissStatus,
            onToggleExpanded = onToggleStatusExpanded,
        )
        state.warning?.let { warning ->
            WarningCard(warning)
        }
    }
}

internal data class CredentialDetailsChrome(
    val onClose: () -> Unit,
    val onCopy: () -> Unit,
    val onDelete: (() -> Unit)?,
)

@Composable
internal fun CredentialDetailsTopBar(chrome: CredentialDetailsChrome) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = WalletHeaderHorizontalPadding, vertical = WalletHeaderVerticalPadding)
            .height(WalletHeaderTitleRowHeight),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CredentialDetailsCloseButton(onClose = chrome.onClose)
        CredentialDetailsOverflowMenu(
            onCopy = chrome.onCopy,
            onDelete = chrome.onDelete,
        )
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
