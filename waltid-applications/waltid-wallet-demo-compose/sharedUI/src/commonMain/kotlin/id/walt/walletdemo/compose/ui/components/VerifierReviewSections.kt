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
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.WalletDemoPresentationPreview
import id.walt.walletdemo.compose.logic.WalletDemoResponseEncryption
import id.walt.walletdemo.compose.logic.WalletDemoVerifierMetadata
import id.walt.walletdemo.compose.ui.WalletUiTestTags

@Composable
internal fun VerifierReviewSections(preview: WalletDemoPresentationPreview, modifier: Modifier = Modifier) {
    var technicalDetailsExpanded by rememberSaveable { mutableStateOf(false) }
    val verifierMetadata = preview.verifierMetadata

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        verifierMetadata?.display?.let { display ->
            display.name?.trim()?.takeIf { it.isNotEmpty() }?.let { name ->
                ReviewMetadataSection(
                    title = "Verifier",
                    modifier = Modifier.testTag(WalletUiTestTags.PresentationVerifierSection),
                ) {
                    MetadataIdentityRow(
                        display = display,
                        fallbackName = name,
                        supportingText = null,
                    )
                }
            }
        }

        if (verifierMetadata?.hasUserFacingInformation == true) {
            ReviewMetadataSection(
                title = "Verifier information",
                modifier = Modifier.testTag(WalletUiTestTags.PresentationVerifierInformationSection),
            ) {
                MetadataDetailLine("Client URI", verifierMetadata.clientUri)
                MetadataDetailLine("Privacy policy", verifierMetadata.policyUri)
                MetadataDetailLine("Terms of service", verifierMetadata.termsOfServiceUri)
            }
        }

        preview.transactionData.forEach { group ->
            ClaimGroupSection(group)
        }

        ReviewMetadataSection(
            title = "Response protection",
            modifier = Modifier.testTag(WalletUiTestTags.PresentationResponseProtectionSection),
        ) {
            val encryption = preview.responseEncryption
            MetadataDetailLine(
                "Message-level encryption",
                when (encryption) {
                    WalletDemoResponseEncryption.NotRequired -> "Not requested"
                    is WalletDemoResponseEncryption.Required -> "Required"
                },
            )
            if (encryption is WalletDemoResponseEncryption.Required) {
                MetadataDetailLine("Key management algorithm", encryption.keyManagementAlgorithm)
                MetadataDetailLine("Content encryption algorithm", encryption.contentEncryptionAlgorithm)
                MetadataDetailLine("Verifier key ID", encryption.verifierKeyId)
                MetadataDetailLine("Verifier key thumbprint", encryption.verifierKeyThumbprint)
            }
        }

        ReviewMetadataSection(
            title = "Technical request details",
            modifier = Modifier.testTag(WalletUiTestTags.PresentationTechnicalDetailsSection),
        ) {
            TextButton(
                onClick = { technicalDetailsExpanded = !technicalDetailsExpanded },
                modifier = Modifier.testTag(WalletUiTestTags.VerifierTechnicalDetailsToggle),
            ) {
                Text(if (technicalDetailsExpanded) "Hide details" else "Show details")
            }

            if (technicalDetailsExpanded) {
                Column(
                    modifier = Modifier.testTag(WalletUiTestTags.VerifierTechnicalDetails),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MetadataDetailLine("Client ID", preview.clientId)
                    MetadataDetailLine("Response URI", preview.responseUri)
                    MetadataDetailLine("State", preview.state)
                    MetadataDetailLine("Nonce", preview.nonce)
                }
            }
        }
    }
}

private val WalletDemoVerifierMetadata.hasUserFacingInformation: Boolean
    get() = !clientUri.isNullOrBlank() || !policyUri.isNullOrBlank() || !termsOfServiceUri.isNullOrBlank()
