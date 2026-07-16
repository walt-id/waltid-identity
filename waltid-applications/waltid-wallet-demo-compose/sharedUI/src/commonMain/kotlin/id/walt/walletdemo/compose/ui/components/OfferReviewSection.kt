package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.CredentialDisplayNormalizer
import id.walt.walletdemo.compose.logic.WalletDemoOfferPreview
import id.walt.walletdemo.compose.logic.WalletDemoTxCode
import id.walt.walletdemo.compose.logic.WalletDemoTxCodeInputMode
import id.walt.walletdemo.compose.logic.offeredCredentialDetails
import id.walt.walletdemo.compose.ui.WalletUiTestTags

@Composable
internal fun OfferReviewSection(
    preview: WalletDemoOfferPreview,
    txCodeRequirement: WalletDemoTxCode?,
    enabled: Boolean,
    txCodeEnabled: Boolean,
    txCode: String,
    onTxCodeChange: (String) -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(WalletUiTestTags.OfferReview),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "Credential offer",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        MetadataIdentityCard(
            CredentialDisplayNormalizer.metadataIdentity(
                title = "Issuer",
                rawJson = preview.issuerMetadataJson,
                fallbackName = preview.credentialIssuer,
                fallbackSubtitle = "Credential issuer",
            )
        )

        val offeredCredentialDetails = preview.offeredCredentialDetails()
        if (offeredCredentialDetails.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Credential preview",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                offeredCredentialDetails.forEach { details ->
                    CredentialCard(details = details)
                }
            }
        }

        if (preview.transactionCodeRequired) {
            Text(
                text = "This offer requires a transaction code.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = txCode,
                onValueChange = { value ->
                    val accepted = when (txCodeRequirement?.inputMode) {
                        WalletDemoTxCodeInputMode.Numeric -> value.filter(Char::isDigit)
                        WalletDemoTxCodeInputMode.Text,
                        null,
                        -> value
                    }
                    onTxCodeChange(txCodeRequirement?.length?.let(accepted::take) ?: accepted)
                },
                label = { Text("Transaction code") },
                singleLine = true,
                enabled = txCodeEnabled,
                keyboardOptions = KeyboardOptions(
                    autoCorrectEnabled = false,
                    keyboardType = when (txCodeRequirement?.inputMode) {
                        WalletDemoTxCodeInputMode.Numeric -> KeyboardType.NumberPassword
                        WalletDemoTxCodeInputMode.Text,
                        null,
                        -> KeyboardType.Password
                    },
                ),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(WalletUiTestTags.TxCodeInput),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onAccept,
                enabled = enabled,
                modifier = Modifier.testTag(WalletUiTestTags.OfferAcceptButton),
            ) {
                Text("Accept")
            }
            TextButton(
                onClick = onDecline,
                enabled = enabled,
                modifier = Modifier.testTag(WalletUiTestTags.OfferDeclineButton),
            ) {
                Text("Decline")
            }
        }
    }
}
