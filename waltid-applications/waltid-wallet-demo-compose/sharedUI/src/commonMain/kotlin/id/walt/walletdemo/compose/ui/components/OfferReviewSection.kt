package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.WalletDemoOfferPreview
import id.walt.walletdemo.compose.logic.WalletDemoOfferedCredentialMetadata
import id.walt.walletdemo.compose.logic.WalletDemoTransactionCodeInputMode
import id.walt.walletdemo.compose.logic.claimDisplayGroups
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
    showActions: Boolean = true,
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
                var page by remember(preview.previewHandle.value) { mutableIntStateOf(0) }
                OfferedCredentialContent(preview.offeredCredentials[page])
                CarouselControls(
                    page = page,
                    pageCount = preview.offeredCredentials.size,
                    itemName = "credential",
                    onPrevious = { page -= 1 },
                    onNext = { page += 1 },
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
                acceptEnabled = acceptEnabled,
                reviewEnabled = reviewEnabled,
                onAccept = onAccept,
                onDecline = onDecline,
            )
        }
    }
}

@Composable
internal fun CarouselControls(
    page: Int,
    pageCount: Int,
    itemName: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    if (pageCount <= 1) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TextButton(onClick = onPrevious, enabled = page > 0) { Text("Previous") }
        Text(
            "${page + 1} of $pageCount $itemName options",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onNext, enabled = page < pageCount - 1) { Text("Next") }
    }
}

@Composable
internal fun OfferReviewActions(
    acceptEnabled: Boolean,
    reviewEnabled: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = onAccept,
            enabled = acceptEnabled,
            modifier = Modifier
                .weight(1f)
                .testTag(WalletUiTestTags.OfferAcceptButton),
        ) {
            Text("Add credential")
        }
        TextButton(
            onClick = onDecline,
            enabled = reviewEnabled,
            modifier = Modifier.testTag(WalletUiTestTags.OfferDeclineButton),
        ) {
            Text("Decline offer")
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
            MetadataDetailItem("Configuration ID", credential.configurationId),
            MetadataDetailItem("Format", credential.format),
            MetadataDetailItem("Authorization scope", credential.scope),
            MetadataDetailItem("SD-JWT VC type", credential.vct),
            MetadataDetailItem("mdoc doctype", credential.doctype),
        ).filter { !it.value.isNullOrBlank() }
        if (details.isNotEmpty()) {
            MetadataRowDivider()
            MetadataDetailList(details)
        }
        if (credential.claims.isNotEmpty()) {
            MetadataRowDivider()
            MetadataDisclosure(
                title = "Supported claims (${credential.claims.size})",
                initiallyExpanded = false,
                modifier = Modifier.testTag(WalletUiTestTags.OfferSupportedClaims),
            ) {
                credential.claimDisplayGroups().forEachIndexed { groupIndex, group ->
                    if (groupIndex > 0) MetadataRowDivider()
                    Text(
                        text = group.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    group.claims.forEachIndexed { index, claim ->
                        if (index > 0) MetadataRowDivider()
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(claim.label, style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = claim.inclusion,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
