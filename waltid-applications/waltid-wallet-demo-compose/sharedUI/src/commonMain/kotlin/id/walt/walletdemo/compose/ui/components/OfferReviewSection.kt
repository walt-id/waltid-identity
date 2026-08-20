package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.WalletDemoOfferPreview
import id.walt.walletdemo.compose.logic.WalletDemoOfferedCredentialMetadata
import id.walt.walletdemo.compose.logic.WalletDemoReviewIsland
import id.walt.walletdemo.compose.logic.WalletDemoReviewIslandKind
import id.walt.walletdemo.compose.logic.WalletDemoReviewSurfaceContext
import id.walt.walletdemo.compose.logic.WalletDemoTransactionCodeInputMode
import id.walt.walletdemo.compose.logic.claimDisplayGroups
import id.walt.walletdemo.compose.logic.toReviewIslands
import id.walt.walletdemo.compose.ui.WalletUiTestTags

/** Issuance review rendered through the shared island and technical-navigation grammar. */
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
    scrollContent: Boolean = false,
    context: WalletDemoReviewSurfaceContext = WalletDemoReviewSurfaceContext.Offered,
) {
    val islands = preview.toReviewIslands(context)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(WalletUiTestTags.OfferReview),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ReviewIslandNavigationHost(
            reviewKey = preview,
            islands = islands,
            modifier = if (scrollContent) Modifier.weight(1f) else Modifier,
            scrollContent = scrollContent,
            islandModifier = { island -> offerIslandTestModifier(island) },
            showModelExpandedValues = { island -> island.kind != WalletDemoReviewIslandKind.Information },
        ) { island ->
            when (island.kind) {
                WalletDemoReviewIslandKind.Information -> OfferedInformationContent(preview.offeredCredentials)
                WalletDemoReviewIslandKind.RequiredAction -> TransactionCodeField(
                        preview = preview,
                        txCode = txCode,
                        reviewEnabled = reviewEnabled,
                        onTxCodeChange = onTxCodeChange,
                    )
                WalletDemoReviewIslandKind.Issuer,
                WalletDemoReviewIslandKind.Verifier,
                WalletDemoReviewIslandKind.Credential,
                WalletDemoReviewIslandKind.ValidityAndStatus,
                WalletDemoReviewIslandKind.PurposeAndTransaction,
                -> Unit
            }
        }

        if (showActions) {
            OfferReviewActionBar(
                preview = preview,
                acceptEnabled = acceptEnabled,
                reviewEnabled = reviewEnabled,
                onAccept = onAccept,
                onDecline = onDecline,
            )
        }
    }
}

@Composable
private fun OfferedInformationContent(credentials: List<WalletDemoOfferedCredentialMetadata>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        credentials.filter { it.claims.isNotEmpty() }.forEachIndexed { credentialIndex, credential ->
            if (credentialIndex > 0) HorizontalDivider()
            if (credentials.size > 1) {
                Text(
                    text = credential.display?.name ?: credential.configurationId,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            credential.claimDisplayGroups().forEachIndexed { groupIndex, group ->
                if (groupIndex > 0) HorizontalDivider()
                Text(
                    text = group.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                group.claims.forEach { claim ->
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

@Composable
internal fun OfferReviewActionBar(
    preview: WalletDemoOfferPreview,
    acceptEnabled: Boolean,
    reviewEnabled: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ReviewActionBar(
        primaryLabel = if (preview.requiresIssuerAuthentication) "Continue" else "Add credential",
        primaryEnabled = acceptEnabled,
        onPrimary = onAccept,
        primaryTestTag = WalletUiTestTags.OfferAcceptButton,
        secondaryLabel = "Decline",
        secondaryEnabled = reviewEnabled,
        onSecondary = onDecline,
        secondaryTestTag = WalletUiTestTags.OfferDeclineButton,
        modifier = modifier,
    )
}

@Composable
private fun TransactionCodeField(
    preview: WalletDemoOfferPreview,
    txCode: String,
    reviewEnabled: Boolean,
    onTxCodeChange: (String) -> Unit,
) {
    val requirement = preview.transactionCode ?: return
    val focusManager = LocalFocusManager.current
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
        colors = OutlinedTextFieldDefaults.colors(),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(WalletUiTestTags.TxCodeInput),
    )
}

private fun offerIslandTestModifier(island: WalletDemoReviewIsland): Modifier = when (island.kind) {
    WalletDemoReviewIslandKind.Issuer -> Modifier.testTag(WalletUiTestTags.OfferIssuerSection)
    WalletDemoReviewIslandKind.Credential -> Modifier.testTag(WalletUiTestTags.OfferCredentialsSection)
    WalletDemoReviewIslandKind.Information -> Modifier.testTag(WalletUiTestTags.OfferSupportedClaims)
    WalletDemoReviewIslandKind.RequiredAction -> Modifier.testTag(
        if (island.title.contains("code", ignoreCase = true)) {
            WalletUiTestTags.OfferTransactionCodeSection
        } else {
            WalletUiTestTags.OfferAuthorizationSection
        }
    )
    WalletDemoReviewIslandKind.Verifier,
    WalletDemoReviewIslandKind.ValidityAndStatus,
    WalletDemoReviewIslandKind.PurposeAndTransaction,
    -> Modifier
}
