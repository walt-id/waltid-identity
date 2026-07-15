package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.VerifierDetails
import id.walt.walletdemo.compose.logic.displayName
import id.walt.walletdemo.compose.ui.WalletUiTestTags

@Composable
internal fun VerifierDetailsCard(verifier: VerifierDetails, modifier: Modifier = Modifier) {
    var technicalDetailsExpanded by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(WalletUiTestTags.PresentationVerifier),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Verifier", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            verifier.displayName,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        DetailLine("Trust", verifier.trustStatus)
        verifier.transactionData.forEach { group ->
            ClaimGroupSection(group)
        }

        TextButton(
            onClick = { technicalDetailsExpanded = !technicalDetailsExpanded },
            modifier = Modifier.testTag(WalletUiTestTags.VerifierTechnicalDetailsToggle),
        ) {
            Text(if (technicalDetailsExpanded) "Hide technical details" else "Show technical details")
        }

        if (technicalDetailsExpanded) {
            Column(
                modifier = Modifier.testTag(WalletUiTestTags.VerifierTechnicalDetails),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DetailLine("Client ID", verifier.clientId)
                DetailLine("Response URI", verifier.responseUri)
                DetailLine("State", verifier.state)
                DetailLine("Nonce", verifier.nonce)
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String?) {
    if (value.isNullOrBlank()) return

    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
