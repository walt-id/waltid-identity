package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.WalletDemoCredentialClaimMetadata
import id.walt.walletdemo.compose.logic.WalletDemoOfferPreview
import id.walt.walletdemo.compose.logic.WalletDemoOfferedCredentialMetadata
import id.walt.walletdemo.compose.logic.WalletDemoTransactionCodeInputMode
import id.walt.walletdemo.compose.ui.WalletUiTestTags

@Composable
internal fun OfferReviewSection(
    preview: WalletDemoOfferPreview,
    acceptEnabled: Boolean,
    reviewEnabled: Boolean,
    txCode: String,
    onTxCodeChange: (String) -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(WalletUiTestTags.OfferReview),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Credential offer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

        ReviewMetadataSection(
            title = "Issuer",
            modifier = Modifier.testTag(WalletUiTestTags.OfferIssuerSection),
        ) {
            MetadataIdentityRow(
                display = preview.issuer.display,
                fallbackName = preview.issuer.credentialIssuer,
                supportingText = preview.issuer.credentialIssuer.takeIf {
                    it.isNotBlank() && it != preview.issuer.display?.name
                },
            )
        }

        if (preview.offeredCredentials.isNotEmpty()) {
            ReviewMetadataSection(
                title = "Offered credentials",
                modifier = Modifier.testTag(WalletUiTestTags.OfferCredentialsSection),
            ) {
                preview.offeredCredentials.forEachIndexed { index, credential ->
                    if (index > 0) HorizontalDivider()
                    OfferedCredentialContent(credential)
                }
            }
        }

        preview.transactionCode?.let { requirement ->
            ReviewMetadataSection(
                title = "Transaction code",
                modifier = Modifier.testTag(WalletUiTestTags.OfferTransactionCodeSection),
            ) {
                Text(
                    text = requirement.description ?: "Enter the transaction code provided by the issuer.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = txCode,
                    onValueChange = { value ->
                        onTxCodeChange(value)
                        val requiredLength = requirement.length
                        if (requiredLength != null && requirement.normalizeInput(value).length == requiredLength) {
                            focusManager.clearFocus()
                        }
                    },
                    label = { Text("Code") },
                    supportingText = requirement.length?.let { length ->
                        { Text("$length characters") }
                    },
                    singleLine = true,
                    enabled = reviewEnabled,
                    keyboardOptions = KeyboardOptions(
                        autoCorrectEnabled = false,
                        keyboardType = when (requirement.inputMode) {
                            WalletDemoTransactionCodeInputMode.Numeric -> KeyboardType.NumberPassword
                            WalletDemoTransactionCodeInputMode.Text -> KeyboardType.Password
                        },
                    ),
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(WalletUiTestTags.TxCodeInput),
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onAccept,
                enabled = acceptEnabled,
                modifier = Modifier.testTag(WalletUiTestTags.OfferAcceptButton),
            ) {
                Text("Accept")
            }
            TextButton(
                onClick = onDecline,
                enabled = reviewEnabled,
                modifier = Modifier.testTag(WalletUiTestTags.OfferDeclineButton),
            ) {
                Text("Decline")
            }
        }
    }
}

@Composable
private fun OfferedCredentialContent(credential: WalletDemoOfferedCredentialMetadata) {
    val title = credential.display?.name
        ?: credential.vct
        ?: credential.doctype
        ?: credential.configurationId

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MetadataIdentityRow(
            display = credential.display,
            fallbackName = title,
            supportingText = credential.display?.description,
        )
        val details = listOf(
            MetadataDetailItem("Format", credential.format),
            MetadataDetailItem("Type", credential.vct ?: credential.doctype),
        ).filter { !it.value.isNullOrBlank() }
        if (details.isNotEmpty()) {
            MetadataRowDivider()
            MetadataDetailList(details)
        }
        if (credential.claims.isNotEmpty()) {
            MetadataRowDivider()
            Text("Claims", style = MaterialTheme.typography.labelMedium)
            credential.claims.forEachIndexed { index, claim ->
                if (index > 0) MetadataRowDivider()
                CredentialClaimLine(claim)
            }
        }
    }
}

@Composable
private fun CredentialClaimLine(claim: WalletDemoCredentialClaimMetadata) {
    val fallback = claim.path.joinToString(".")
    val value = claim.displayName?.takeIf { it.isNotBlank() } ?: fallback
    Text(
        text = if (claim.mandatory == true) "$value (required)" else value,
        style = MaterialTheme.typography.bodySmall,
    )
}
