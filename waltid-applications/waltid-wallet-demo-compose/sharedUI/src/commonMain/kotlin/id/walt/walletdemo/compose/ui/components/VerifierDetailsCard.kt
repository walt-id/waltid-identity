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
import id.walt.walletdemo.compose.logic.WalletDemoResponseEncryption
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
        MetadataIdentityRow(
            display = verifier.metadata?.display,
            fallbackName = verifier.displayName,
            supportingText = "Verifier-provided identity",
        )
        MetadataDetailLine("Trust", verifier.trustStatus)
        MetadataDetailLine(
            "Response encryption",
            when (verifier.responseEncryption) {
                WalletDemoResponseEncryption.NotRequired -> "Not encrypted"
                is WalletDemoResponseEncryption.Required -> "Encrypted"
            },
        )
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
                MetadataDetailLine("Client ID", verifier.clientId)
                MetadataDetailLine("Response URI", verifier.responseUri)
                MetadataDetailLine("Client URI", verifier.metadata?.clientUri)
                MetadataDetailLine("Privacy policy", verifier.metadata?.policyUri)
                MetadataDetailLine("Terms of service", verifier.metadata?.termsOfServiceUri)
                MetadataDetailLine("State", verifier.state)
                MetadataDetailLine("Nonce", verifier.nonce)
                val encryption = verifier.responseEncryption
                if (encryption is WalletDemoResponseEncryption.Required) {
                    MetadataDetailLine("JWE algorithm", encryption.keyManagementAlgorithm)
                    MetadataDetailLine("Content encryption", encryption.contentEncryptionAlgorithm)
                    MetadataDetailLine("Verifier key ID", encryption.verifierKeyId)
                    MetadataDetailLine("Verifier key thumbprint", encryption.verifierKeyThumbprint)
                }
            }
        }
    }
}
