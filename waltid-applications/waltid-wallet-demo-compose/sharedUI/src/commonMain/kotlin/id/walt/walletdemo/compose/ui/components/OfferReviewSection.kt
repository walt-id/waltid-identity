package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.WalletDemoOfferPreview
import id.walt.walletdemo.compose.logic.WalletDemoTransactionCodeInputMode
import id.walt.walletdemo.compose.ui.WalletUiTestTags
import id.walt.walletdemo.compose.ui.resources.*
import org.jetbrains.compose.resources.stringResource

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
    showActions: Boolean = true,
    copies: Map<String, Int> = emptyMap(),
    onCopiesChange: ((String, Int) -> Unit)? = null,
) {
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(WalletUiTestTags.OfferReview),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val issuerName = preview.issuer.display?.name?.trim()?.takeIf { it.isNotEmpty() }
        val issuerIdentifier = preview.issuer.credentialIssuer.trim()
        IssuanceIssuerSection(preview.issuer)

        if (preview.offeredCredentials.isNotEmpty()) {
            val selected = preview.offeredCredentials.map { copies[it.configurationId] ?: 1 }.filter { it > 0 }
            WalletSection(
                title = stringResource(Res.string.issuance_offered_credentials),
                footer = if (onCopiesChange == null) null else if (selected.isEmpty())
                    stringResource(Res.string.issuance_select_one)
                else stringResource(Res.string.issuance_selection_summary, selected.size, selected.sum()),
                modifier = Modifier.testTag(WalletUiTestTags.OfferCredentialsSection),
            ) {
                preview.offeredCredentials.forEachIndexed { index, credential ->
                    if (index > 0) HorizontalDivider()
                    OfferedCredentialRow(
                        credential = credential,
                        issuer = issuerName ?: issuerIdentifier,
                        issuerIdentifier = issuerIdentifier,
                        count = copies[credential.configurationId] ?: 1,
                        limit = preview.batchSize ?: 1,
                        enabled = reviewEnabled,
                        onCountChange = onCopiesChange?.let { change -> { count -> change(credential.configurationId, count) } },
                    )
                }
            }
        }

        if (preview.requiresIssuerAuthentication) {
            ReviewMetadataSection(
                title = "Issuer sign-in",
                modifier = Modifier.testTag(WalletUiTestTags.OfferAuthorizationSection),
            ) {
                Text(
                    text = "Continuing opens your browser to sign in with the issuer before the credential is issued.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
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

        if (showActions) {
            OfferReviewActions(
                requiresIssuerAuthentication = preview.requiresIssuerAuthentication,
                acceptEnabled = acceptEnabled,
                reviewEnabled = reviewEnabled,
                onAccept = onAccept,
                onDecline = onDecline,
            )
        }
    }
}
