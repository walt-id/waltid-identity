package id.walt.walletdemo.compose.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.WalletDemoCredential
import id.walt.walletdemo.compose.logic.WalletDemoUiState
import id.walt.walletdemo.compose.logic.toCredentialDetails
import id.walt.walletdemo.compose.ui.WalletUiTestTags
import id.walt.walletdemo.compose.ui.components.CredentialCard
import id.walt.walletdemo.compose.ui.components.StatusCard

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
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatusCard(state, onDismissStatus)
        Text("Credentials", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(WalletUiTestTags.CredentialsEmpty)
            .padding(vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("No credentials yet", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
        Text(
            "Scan a credential offer to add your first one.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onScan,
            enabled = scanEnabled,
            modifier = Modifier.testTag(WalletUiTestTags.ScanEmptyAction),
        ) {
            Text("Scan a code")
        }
    }
}
