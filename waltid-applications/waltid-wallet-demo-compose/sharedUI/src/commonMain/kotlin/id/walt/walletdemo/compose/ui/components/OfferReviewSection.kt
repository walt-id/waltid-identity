package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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

        Text("Issuer", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MetadataIdentityRow(
            display = preview.issuer.display,
            fallbackName = preview.issuer.credentialIssuer.ifBlank { "Unknown issuer" },
            supportingText = preview.issuer.credentialIssuer.takeIf {
                it.isNotBlank() && it != preview.issuer.display?.name
            },
        )

        if (preview.offeredCredentials.isNotEmpty()) {
            Text(
                "Offered credentials",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            preview.offeredCredentials.forEach { credential ->
                OfferedCredentialCard(credential)
            }
        }

        preview.transactionCode?.let { requirement ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                    label = { Text("Transaction code") },
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
private fun OfferedCredentialCard(credential: WalletDemoOfferedCredentialMetadata) {
    val title = credential.display?.name
        ?: credential.vct
        ?: credential.doctype
        ?: credential.configurationId

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MetadataIdentityRow(
                display = credential.display,
                fallbackName = title,
                supportingText = credential.display?.description,
            )
            MetadataDetailLine("Format", credential.format)
            MetadataDetailLine("Type", credential.vct ?: credential.doctype)
            if (credential.claims.isNotEmpty()) {
                Text("Claims", style = MaterialTheme.typography.labelMedium)
                credential.claims.forEach { claim -> CredentialClaimLine(claim) }
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
