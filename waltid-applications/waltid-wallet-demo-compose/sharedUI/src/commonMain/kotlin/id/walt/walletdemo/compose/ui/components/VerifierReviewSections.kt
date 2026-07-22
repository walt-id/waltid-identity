package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.WalletDemoPresentationRequestInfo
import id.walt.walletdemo.compose.logic.WalletDemoResponseEncryption
import id.walt.walletdemo.compose.ui.WalletUiTestTags

@Composable
internal fun VerifierReviewSections(request: WalletDemoPresentationRequestInfo, modifier: Modifier = Modifier) {
    var technicalDetailsExpanded by rememberSaveable { mutableStateOf(false) }
    val verifierMetadata = request.verifierMetadata

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        verifierMetadata?.let { metadata ->
            val displayName = metadata.display?.name?.trim()?.takeIf { it.isNotEmpty() }
            val details = listOf(
                MetadataDetailItem("Client URI", metadata.clientUri, metadata.clientUri),
                MetadataDetailItem("Privacy policy", metadata.policyUri, metadata.policyUri),
                MetadataDetailItem("Terms of service", metadata.termsOfServiceUri, metadata.termsOfServiceUri),
            ).filter { !it.value.isNullOrBlank() }

            if (displayName != null || details.isNotEmpty()) {
                ReviewMetadataSection(
                    title = "Verifier",
                    modifier = Modifier.testTag(WalletUiTestTags.PresentationVerifierSection),
                ) {
                    if (displayName != null) {
                        MetadataIdentityRow(
                            display = metadata.display,
                            fallbackName = displayName,
                            supportingText = null,
                        )
                        if (details.isNotEmpty()) MetadataRowDivider()
                    }
                    MetadataDetailList(details)
                }
            }
        }

        request.transactionData.forEach { group ->
            ClaimGroupSection(group)
        }

        ReviewMetadataSection(
            title = "Response protection",
            modifier = Modifier.testTag(WalletUiTestTags.PresentationResponseProtectionSection),
        ) {
            val encryption = request.responseEncryption
            MetadataDetailList(
                buildList {
                    add(
                        MetadataDetailItem(
                            "Message-level encryption",
                            when (encryption) {
                                WalletDemoResponseEncryption.NotRequired -> "Not requested"
                                is WalletDemoResponseEncryption.Required -> "Required"
                            },
                        )
                    )
                    if (encryption is WalletDemoResponseEncryption.Required) {
                        add(MetadataDetailItem("Key management algorithm", encryption.keyManagementAlgorithm))
                        add(MetadataDetailItem("Content encryption algorithm", encryption.contentEncryptionAlgorithm))
                        add(MetadataDetailItem("Verifier key ID", encryption.verifierKeyId))
                        add(MetadataDetailItem("Verifier key thumbprint", encryption.verifierKeyThumbprint))
                    }
                }
            )
        }

        ReviewMetadataSection(
            title = "Technical request details",
            modifier = Modifier.testTag(WalletUiTestTags.PresentationTechnicalDetailsSection),
            contentPadding = if (technicalDetailsExpanded) {
                PaddingValues(16.dp)
            } else {
                PaddingValues(horizontal = 16.dp, vertical = 2.dp)
            },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .clickable { technicalDetailsExpanded = !technicalDetailsExpanded }
                    .testTag(WalletUiTestTags.VerifierTechnicalDetailsToggle),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (technicalDetailsExpanded) "Hide details" else "Show details")
                Icon(
                    imageVector = if (technicalDetailsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                )
            }

            if (technicalDetailsExpanded) {
                MetadataRowDivider()
                MetadataDetailList(
                    listOf(
                        MetadataDetailItem("Client ID", request.clientId),
                        MetadataDetailItem("Response URI", request.responseUri),
                        MetadataDetailItem("State", request.state),
                        MetadataDetailItem("Nonce", request.nonce),
                    ),
                    modifier = Modifier.testTag(WalletUiTestTags.VerifierTechnicalDetails),
                )
            }
        }
    }
}
