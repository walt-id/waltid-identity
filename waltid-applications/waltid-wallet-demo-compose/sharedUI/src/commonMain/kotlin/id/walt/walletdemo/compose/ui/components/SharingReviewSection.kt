package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.ClaimItem
import id.walt.walletdemo.compose.logic.WalletDemoPresentationCredentialOption
import id.walt.walletdemo.compose.logic.WalletDemoPresentationCredentialSelection
import id.walt.walletdemo.compose.logic.WalletDemoPresentationDisclosureSelection
import id.walt.walletdemo.compose.logic.WalletDemoReaderTrust
import id.walt.walletdemo.compose.logic.WalletDemoReviewIsland
import id.walt.walletdemo.compose.logic.WalletDemoReviewIslandKind
import id.walt.walletdemo.compose.logic.WalletDemoReviewRoute
import id.walt.walletdemo.compose.logic.WalletDemoReviewSurfaceContext
import id.walt.walletdemo.compose.logic.WalletDemoSharingEncryptionMechanism
import id.walt.walletdemo.compose.logic.WalletDemoSharingRequest
import id.walt.walletdemo.compose.logic.WalletDemoSharingResponseProtection
import id.walt.walletdemo.compose.logic.WalletDemoSharingReview
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
    hostOwnsTopChrome: Boolean = false,
    technicalBackSignal: Int = 0,
    onRouteChanged: (WalletDemoReviewRoute, WalletDemoReviewIsland?) -> Unit = { _, _ -> },
) {
    val islands = review.toReviewIslands(context)
    val optionByIslandId = islands
        .filter { it.kind == WalletDemoReviewIslandKind.Credential }
        .zip(review.credentialOptions)
        .associate { (island, option) -> island.id to option }
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
                island.kind != WalletDemoReviewIslandKind.Credential
            },
            islandHeaderContent = { island ->
                optionByIslandId[island.id]?.let { option ->
                    CredentialSelectionControl(
                        option = option,
                        style = review.selectionStyle(option),
                        selected = option.selection in selectedCredentialOptions,
                        enabled = enabled,
                        readOnly = readOnly,
                        onToggleCredential = onToggleCredential,
                    )
                }
            },
            hasCustomExpandedContent = { island ->
                island.kind == WalletDemoReviewIslandKind.Verifier ||
                    (island.kind == WalletDemoReviewIslandKind.Credential && optionByIslandId[island.id] != null)
            },
            technicalBackSignal = technicalBackSignal,
            onRouteChanged = onRouteChanged,
            showTechnicalHeader = !hostOwnsTopChrome,
        ) { island ->
            when (island.kind) {
                WalletDemoReviewIslandKind.Verifier -> VerifierReviewFacts(review.request)
                WalletDemoReviewIslandKind.Credential -> optionByIslandId[island.id]?.let { option ->
                    SharingCredentialContent(
                        option = option,
                        credentialSelected = option.selection in selectedCredentialOptions,
                        selectedDisclosureOptions = selectedDisclosureOptions,
                        enabled = enabled,
                        readOnly = readOnly,
                        onToggleDisclosure = onToggleDisclosure,
                        onCredentialClick = onCredentialClick,
                    )
                }
                WalletDemoReviewIslandKind.Issuer,
                WalletDemoReviewIslandKind.Information,
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
        modifier = modifier.testTag(WalletUiTestTags.PresentationActions),
        tertiaryLabel = "Reject".takeIf { onReject != null },
        tertiaryEnabled = enabled,
        onTertiary = onReject,
        tertiaryTestTag = WalletUiTestTags.PresentationRejectButton.takeIf { onReject != null },
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
private fun SharingCredentialContent(
    option: WalletDemoPresentationCredentialOption,
    credentialSelected: Boolean,
    selectedDisclosureOptions: Set<WalletDemoPresentationDisclosureSelection>,
    enabled: Boolean,
    readOnly: Boolean,
    onToggleDisclosure: (WalletDemoPresentationDisclosureSelection) -> Unit,
    onCredentialClick: (String) -> Unit,
) {
    val details = option.toCredentialDetails()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (option.disclosures.isNotEmpty()) {
            SharingDisclosureList(
                option = option,
                credentialSelected = credentialSelected,
                selectedDisclosureOptions = selectedDisclosureOptions,
                requestedDisclosureItems = option.toRequestedDisclosureGroup()?.items.orEmpty(),
                enabled = enabled,
                readOnly = readOnly,
                onToggleDisclosure = onToggleDisclosure,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { onCredentialClick(details.summary.id) }
                .testTag(WalletUiTestTags.credentialCard(details.summary.id))
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "View credential",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
            )
        }
    }
}

private enum class ReviewSelectionStyle { Checkbox, Radio, Included }

private fun WalletDemoSharingReview.selectionStyle(
    option: WalletDemoPresentationCredentialOption,
): ReviewSelectionStyle {
    if (option.multiple) return ReviewSelectionStyle.Checkbox
    val candidateCount = credentialOptions.count { it.queryId == option.queryId }
    val belongsToAlternative = credentialRequirements.any { requirement ->
        requirement.options.size > 1 && requirement.options.any { option.queryId in it }
    }
    return if (candidateCount > 1 || belongsToAlternative) {
        ReviewSelectionStyle.Radio
    } else {
        ReviewSelectionStyle.Included
    }
}

@Composable
private fun CredentialSelectionControl(
    option: WalletDemoPresentationCredentialOption,
    style: ReviewSelectionStyle,
    selected: Boolean,
    enabled: Boolean,
    readOnly: Boolean,
    onToggleCredential: (WalletDemoPresentationCredentialSelection) -> Unit,
) {
    ReviewSelectionControl(
        selected = selected,
        style = style,
        enabled = enabled && !readOnly && style != ReviewSelectionStyle.Included,
        accessibilityLabel = when (style) {
            ReviewSelectionStyle.Included -> "${option.label} included"
            ReviewSelectionStyle.Radio -> "Select ${option.label}"
            ReviewSelectionStyle.Checkbox -> "Include ${option.label}"
        },
        onClick = { onToggleCredential(option.selection) },
        modifier = Modifier.testTag(WalletUiTestTags.presentationCredentialToggle(option.selection.id)),
    )
}

@Composable
private fun ReviewSelectionControl(
    selected: Boolean,
    style: ReviewSelectionStyle,
    enabled: Boolean,
    accessibilityLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val indicatorShape: Shape = if (style == ReviewSelectionStyle.Radio) CircleShape else RoundedCornerShape(6.dp)
    val accent = if (enabled || selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val role = if (style == ReviewSelectionStyle.Radio) Role.RadioButton else Role.Checkbox
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .then(if (enabled) Modifier.clickable(role = role, onClick = onClick) else Modifier)
            .semantics {
                contentDescription = accessibilityLabel
                toggleableState = if (selected) ToggleableState.On else ToggleableState.Off
                if (!enabled) disabled()
                stateDescription = when {
                    style == ReviewSelectionStyle.Included && selected -> "Included"
                    selected -> "Selected"
                    else -> "Not selected"
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(indicatorShape)
                .background(if (selected && style != ReviewSelectionStyle.Radio) accent else Color.Transparent)
                .border(2.dp, accent, indicatorShape),
            contentAlignment = Alignment.Center,
        ) {
            when {
                selected && style == ReviewSelectionStyle.Radio -> Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
                selected -> Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
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
                ReviewSelectionControl(
                    selected = if (disclosure.selectable) {
                        selection in selectedDisclosureOptions
                    } else {
                        credentialSelected
                    },
                    style = if (disclosure.selectable) {
                        ReviewSelectionStyle.Checkbox
                    } else {
                        ReviewSelectionStyle.Included
                    },
                    enabled = disclosure.selectable && !readOnly && enabled && credentialSelected,
                    accessibilityLabel = if (disclosure.selectable) {
                        "Include ${disclosure.label}"
                    } else {
                        "${disclosure.label} included"
                    },
                    onClick = { onToggleDisclosure(selection) },
                    modifier = Modifier.testTag(WalletUiTestTags.presentationDisclosureToggle(selection.id)),
                )
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
