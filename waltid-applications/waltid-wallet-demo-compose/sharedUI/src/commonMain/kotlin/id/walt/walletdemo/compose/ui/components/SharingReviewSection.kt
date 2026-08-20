package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.ClaimItem
import id.walt.walletdemo.compose.logic.WalletDemoPresentationCredentialOption
import id.walt.walletdemo.compose.logic.WalletDemoPresentationCredentialSelection
import id.walt.walletdemo.compose.logic.WalletDemoPresentationDisclosureSelection
import id.walt.walletdemo.compose.logic.WalletDemoReaderTrust
import id.walt.walletdemo.compose.logic.WalletDemoReviewIsland
import id.walt.walletdemo.compose.logic.WalletDemoReviewIslandKind
import id.walt.walletdemo.compose.logic.WalletDemoReviewSurfaceContext
import id.walt.walletdemo.compose.logic.WalletDemoSharingEncryptionMechanism
import id.walt.walletdemo.compose.logic.WalletDemoSharingRequest
import id.walt.walletdemo.compose.logic.WalletDemoSharingResponseProtection
import id.walt.walletdemo.compose.logic.WalletDemoSharingReview
import id.walt.walletdemo.compose.logic.toCardDisplayData
import id.walt.walletdemo.compose.logic.toCredentialDetails
import id.walt.walletdemo.compose.logic.toRequestedDisclosureGroup
import id.walt.walletdemo.compose.logic.toReviewIslands
import id.walt.walletdemo.compose.ui.WalletUiTestTags

/** The presentation review shared by wallet-initiated and platform-invoked hosts. */
@Composable
internal fun SharingReviewSection(
    review: WalletDemoSharingReview,
    selectedCredentialOptions: Set<WalletDemoPresentationCredentialSelection>,
    selectedDisclosureOptions: Set<WalletDemoPresentationDisclosureSelection>,
    selectionComplete: Boolean,
    enabled: Boolean,
    onToggleCredential: (WalletDemoPresentationCredentialSelection) -> Unit,
    onToggleDisclosure: (WalletDemoPresentationDisclosureSelection) -> Unit,
    onCredentialClick: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    onReject: (() -> Unit)? = null,
    readOnly: Boolean = false,
    modifier: Modifier = Modifier,
    showActions: Boolean = true,
    scrollContent: Boolean = false,
    context: WalletDemoReviewSurfaceContext = WalletDemoReviewSurfaceContext.SelectedForSharing,
) {
    val islands = review.toReviewIslands(context)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(WalletUiTestTags.PresentationReview),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ReviewIslandNavigationHost(
            reviewKey = review,
            islands = islands,
            modifier = if (scrollContent) Modifier.weight(1f) else Modifier,
            scrollContent = scrollContent,
            islandModifier = { island -> sharingIslandTestModifier(island) },
            showModelExpandedValues = { island ->
                island.kind !in setOf(
                    WalletDemoReviewIslandKind.Credential,
                    WalletDemoReviewIslandKind.Information,
                )
            },
        ) { island ->
            when (island.kind) {
                WalletDemoReviewIslandKind.Verifier -> VerifierReviewFacts(review.request)
                WalletDemoReviewIslandKind.Credential -> SharingCredentialChoices(
                    options = review.credentialOptions,
                    selectedCredentialOptions = selectedCredentialOptions,
                    enabled = enabled,
                    readOnly = readOnly,
                    onToggleCredential = onToggleCredential,
                    onCredentialClick = onCredentialClick,
                )
                WalletDemoReviewIslandKind.Information -> SharingInformationChoices(
                    options = review.credentialOptions,
                    selectedCredentialOptions = selectedCredentialOptions,
                    selectedDisclosureOptions = selectedDisclosureOptions,
                    enabled = enabled,
                    readOnly = readOnly,
                    onToggleDisclosure = onToggleDisclosure,
                )
                WalletDemoReviewIslandKind.Issuer,
                WalletDemoReviewIslandKind.ValidityAndStatus,
                WalletDemoReviewIslandKind.PurposeAndTransaction,
                WalletDemoReviewIslandKind.RequiredAction,
                -> Unit
            }
        }

        if (showActions && !readOnly) {
            SharingReviewActionBar(
                enabled = enabled,
                selectionComplete = selectionComplete,
                onSubmit = onSubmit,
                onCancel = onCancel,
                onReject = onReject,
            )
        }
    }
}

@Composable
internal fun SharingReviewActionBar(
    enabled: Boolean,
    selectionComplete: Boolean,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    onReject: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    ReviewActionBar(
        primaryLabel = "Share information",
        primaryCompactLabel = "Share",
        primaryEnabled = enabled && selectionComplete,
        onPrimary = onSubmit,
        primaryTestTag = WalletUiTestTags.PresentationSubmitButton,
        secondaryLabel = "Cancel",
        secondaryEnabled = enabled,
        onSecondary = onCancel,
        secondaryTestTag = WalletUiTestTags.PresentationCancelButton,
        secondaryCompactIcon = Icons.AutoMirrored.Filled.ArrowBack,
        modifier = modifier.testTag(WalletUiTestTags.PresentationActions),
        tertiaryLabel = "Reject".takeIf { onReject != null },
        tertiaryEnabled = enabled,
        onTertiary = onReject,
        tertiaryTestTag = WalletUiTestTags.PresentationRejectButton.takeIf { onReject != null },
        tertiaryCompactIcon = Icons.Default.Close,
    )
}

@Composable
private fun VerifierReviewFacts(request: WalletDemoSharingRequest) {
    request.readerTrust?.let { trust ->
        val (headline, explanation) = trust.userFacingText()
        Column(
            modifier = Modifier.testTag(WalletUiTestTags.PresentationReaderTrustSection),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(headline, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                text = explanation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Column(
        modifier = Modifier.testTag(WalletUiTestTags.PresentationResponseProtectionSection),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = request.responseProtection.userFacingExplanation(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SharingCredentialChoices(
    options: List<WalletDemoPresentationCredentialOption>,
    selectedCredentialOptions: Set<WalletDemoPresentationCredentialSelection>,
    enabled: Boolean,
    readOnly: Boolean,
    onToggleCredential: (WalletDemoPresentationCredentialSelection) -> Unit,
    onCredentialClick: (String) -> Unit,
) {
    val hasMultipleOptions = options.size > 1
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { index, option ->
            val details = option.toCredentialDetails()
            val credentialDisplay = details.toCardDisplayData()
            if (index > 0) HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(WalletUiTestTags.presentationCredential(option.selection.id)),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                if (!readOnly) {
                    Checkbox(
                        checked = option.selection in selectedCredentialOptions,
                        onCheckedChange = { onToggleCredential(option.selection) },
                        enabled = enabled,
                        modifier = Modifier.testTag(
                            WalletUiTestTags.presentationCredentialToggle(option.selection.id)
                        ),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = if (hasMultipleOptions) option.label else "Use this credential",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    if (hasMultipleOptions) {
                        Text(
                            credentialDisplay.issuer,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    option.subject?.let { subject ->
                        Text(
                            "Subject: $subject",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(
                    onClick = { onCredentialClick(details.summary.id) },
                    modifier = Modifier.testTag(WalletUiTestTags.credentialCard(details.summary.id)),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "View credential details",
                    )
                }
            }
        }
    }
}

@Composable
private fun SharingInformationChoices(
    options: List<WalletDemoPresentationCredentialOption>,
    selectedCredentialOptions: Set<WalletDemoPresentationCredentialSelection>,
    selectedDisclosureOptions: Set<WalletDemoPresentationDisclosureSelection>,
    enabled: Boolean,
    readOnly: Boolean,
    onToggleDisclosure: (WalletDemoPresentationDisclosureSelection) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        options.filter { it.disclosures.isNotEmpty() }.forEachIndexed { optionIndex, option ->
            if (optionIndex > 0) HorizontalDivider()
            if (options.size > 1) {
                Text(option.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
            SharingDisclosureList(
                option = option,
                credentialSelected = option.selection in selectedCredentialOptions,
                selectedDisclosureOptions = selectedDisclosureOptions,
                requestedDisclosureItems = option.toRequestedDisclosureGroup()?.items.orEmpty(),
                enabled = enabled,
                readOnly = readOnly,
                onToggleDisclosure = onToggleDisclosure,
            )
        }
    }
}

@Composable
private fun SharingDisclosureList(
    option: WalletDemoPresentationCredentialOption,
    credentialSelected: Boolean,
    selectedDisclosureOptions: Set<WalletDemoPresentationDisclosureSelection>,
    requestedDisclosureItems: List<ClaimItem>,
    enabled: Boolean,
    readOnly: Boolean,
    onToggleDisclosure: (WalletDemoPresentationDisclosureSelection) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        option.disclosures.forEachIndexed { index, disclosure ->
            val selection = WalletDemoPresentationDisclosureSelection(
                queryId = option.queryId,
                credentialId = option.credentialId,
                path = disclosure.path,
            )
            val item = requestedDisclosureItems.getOrNull(index)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(WalletUiTestTags.presentationDisclosure(selection.id)),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (disclosure.selectable && !readOnly) {
                    Checkbox(
                        checked = selection in selectedDisclosureOptions,
                        onCheckedChange = { onToggleDisclosure(selection) },
                        enabled = enabled && credentialSelected,
                        modifier = Modifier.testTag(WalletUiTestTags.presentationDisclosureToggle(selection.id)),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (item != null) {
                        ClaimValueRow(item = item)
                    } else {
                        Text(disclosure.label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        Text(
                            disclosure.displayValue ?: disclosure.valueJson,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        when {
                            disclosure.selectable -> "Optional disclosure"
                            disclosure.required -> "Required by request"
                            disclosure.selectivelyDisclosable -> "Selective disclosure"
                            else -> "Required by credential format"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun sharingIslandTestModifier(island: WalletDemoReviewIsland): Modifier = when (island.kind) {
    WalletDemoReviewIslandKind.Verifier -> Modifier.testTag(WalletUiTestTags.PresentationVerifierSection)
    WalletDemoReviewIslandKind.PurposeAndTransaction -> Modifier.testTag(
        WalletUiTestTags.claimGroup(island.title)
    )
    WalletDemoReviewIslandKind.Issuer,
    WalletDemoReviewIslandKind.Credential,
    WalletDemoReviewIslandKind.Information,
    WalletDemoReviewIslandKind.ValidityAndStatus,
    WalletDemoReviewIslandKind.RequiredAction,
    -> Modifier
}

private fun WalletDemoReaderTrust.userFacingText(): Pair<String, String> = when (this) {
    WalletDemoReaderTrust.NotAuthenticated ->
        "Reader not authenticated" to "The request carried no reader signature."
    WalletDemoReaderTrust.PendingVerification ->
        "Reader authentication checked before sharing" to "Nothing is sent if verification fails."
    is WalletDemoReaderTrust.Untrusted ->
        "Reader identity not trusted by this wallet" to reason
    is WalletDemoReaderTrust.Trusted ->
        "Trusted reader" to readerIdentity
}

private fun WalletDemoSharingResponseProtection.userFacingExplanation(): String = when (this) {
    WalletDemoSharingResponseProtection.None -> "The request does not require an encrypted response."
    is WalletDemoSharingResponseProtection.Encrypted -> when (mechanism) {
        WalletDemoSharingEncryptionMechanism.Jwe -> "The response is encrypted for the Verifier."
        WalletDemoSharingEncryptionMechanism.DcApiJwt -> "The response is encrypted for platform delivery."
        WalletDemoSharingEncryptionMechanism.AnnexCHpke -> "The response is protected for this reader session."
    }
}
