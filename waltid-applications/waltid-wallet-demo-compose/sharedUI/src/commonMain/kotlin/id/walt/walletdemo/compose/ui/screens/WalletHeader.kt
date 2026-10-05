package id.walt.walletdemo.compose.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.WalletDemoUiState
import id.walt.walletdemo.compose.ui.LocalWalletDemoBranding
import id.walt.walletdemo.compose.ui.WalletUiTestTags
import id.walt.walletdemo.compose.ui.components.CredentialDetailsCloseButton
import id.walt.walletdemo.compose.ui.components.CredentialDetailsOverflowMenu
import id.walt.walletdemo.compose.ui.components.WalletScreenHeader
import id.walt.walletdemo.compose.ui.resources.Res
import id.walt.walletdemo.compose.ui.resources.proximity_qr
import id.walt.walletdemo.compose.ui.resources.settings_nearby
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun WalletHeader(
    state: WalletDemoUiState,
    onSettings: () -> Unit,
    onScan: (() -> Unit)? = null,
    onShareNearby: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    title: String? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        WalletScreenHeader(
            title = title ?: LocalWalletDemoBranding.current.appTitle,
            titleTag = WalletUiTestTags.AppTitle,
            leading = onBack?.let { back ->
                {
                    IconButton(onClick = back, modifier = Modifier.testTag(WalletUiTestTags.FlowBack)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to wallet")
                    }
                }
            },
        ) {
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
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.warning?.let { warning -> WarningCard(warning) }
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
    WalletScreenHeader(title = null, leading = { CredentialDetailsCloseButton(onClose = chrome.onClose) }) {
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
