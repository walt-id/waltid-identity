package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import id.walt.walletdemo.compose.logic.*
import id.walt.walletdemo.compose.logic.ClaimItem
import id.walt.walletdemo.compose.logic.CredentialDetails
import id.walt.walletdemo.compose.logic.WalletDemoPresentationCredentialOption
import id.walt.walletdemo.compose.logic.WalletDemoPresentationCredentialSelection
import id.walt.walletdemo.compose.logic.WalletDemoPresentationDisclosureSelection
import id.walt.walletdemo.compose.logic.WalletDemoSharingReview
import id.walt.walletdemo.compose.logic.resolvedCardTitle
import id.walt.walletdemo.compose.logic.toCardDisplayData
import id.walt.walletdemo.compose.logic.toCredentialDetails
import id.walt.walletdemo.compose.logic.toRequestedDisclosureGroup
import id.walt.walletdemo.compose.ui.SystemBackHandler
import id.walt.walletdemo.compose.ui.WalletUiTestTags
import id.walt.walletdemo.compose.ui.exportTestTagsForPlatformAutomation
import id.walt.walletdemo.compose.ui.resources.*
import id.walt.walletdemo.compose.ui.resources.proximity_approve
import id.walt.walletdemo.compose.ui.resources.proximity_cancel
import id.walt.walletdemo.compose.ui.resources.proximity_decline
import org.jetbrains.compose.resources.stringResource

/**
 * The wallet's single presentation-review surface, shared by every transport that can ask for a
 * credential.
 *
 * @param review What the user is being asked to share, already mapped off the transport's preview.
 * @param onReject Sends a protocol-level refusal to the requester. Pass null for transports with no
 * such message - the platform Digital Credentials APIs return a cancellation instead, and offering
 * both a Reject and a Cancel button there would promise the requester gets told two different things.
 */
@Composable
internal fun SharingReviewSection(
    review: WalletDemoSharingReview,
    selectedCredentialOptions: Set<WalletDemoPresentationCredentialSelection>,
    selectedDisclosureOptions: Set<WalletDemoPresentationDisclosureSelection>,
    selectionComplete: Boolean,
    enabled: Boolean,
    onToggleCredential: (WalletDemoPresentationCredentialSelection) -> Unit,
    onToggleDisclosure: (WalletDemoPresentationDisclosureSelection) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    onReject: (() -> Unit)? = null,
    readOnly: Boolean = false,
    compact: Boolean = false,
    showActions: Boolean = true,
    paymentReview: WalletDemoPaymentReview = WalletDemoPaymentReview.NotRequired,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(WalletUiTestTags.PresentationReview),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SharingRequestSections(if (paymentReview is WalletDemoPaymentReview.NotRequired) review.request else
            review.request.copy(transactionData = review.request.transactionData.filterNot { it.transactionType == "urn:eudi:sca:payment:1" }))
        PaymentConsentSection(paymentReview)

        if (compact) {
            var claimsOptionId by rememberSaveable { mutableStateOf<String?>(null) }
            val claimsOption = review.credentialOptions.firstOrNull { it.selection.id == claimsOptionId }
            SystemBackHandler(enabled = claimsOption != null) {
                claimsOptionId = null
            }
            CredentialCardStack(
                cards = remember(review.credentialOptions) {
                    review.credentialOptions.map { it.toCredentialDetails().toCardDisplayData() }
                },
                onOpenDetails = { detailsId ->
                    claimsOptionId = detailsId
                },
            )
            claimsOption?.let { option ->
                SharingClaimsDialog(
                    option = option,
                    details = remember(option) { option.toCredentialDetails() },
                    credentialSelected = option.selection in selectedCredentialOptions,
                    selectedDisclosureOptions = selectedDisclosureOptions,
                    requestedDisclosureItems = remember(option) { option.toRequestedDisclosureGroup()?.items.orEmpty() },
                    enabled = enabled,
                    readOnly = readOnly,
                    onToggleDisclosure = onToggleDisclosure,
                    onDismiss = { claimsOptionId = null },
                )
            }
        } else {
            Text(
                "Select credentials to share",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (review.credentialOptions.isEmpty()) {
                Text(
                    "No credentials available",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            review.credentialOptions.forEach { option ->
                SelectableCredentialRow(
                    option = option,
                    selectedCredentialOptions = selectedCredentialOptions,
                    selectedDisclosureOptions = selectedDisclosureOptions,
                    enabled = enabled,
                    readOnly = readOnly,
                    onToggleCredential = onToggleCredential,
                    onToggleDisclosure = onToggleDisclosure,
                )
            }
        }

        if (!readOnly && showActions) {
            SharingActionsRow(
                enabled = enabled,
                selectionComplete = selectionComplete,
                paymentReview = paymentReview,
                onSubmit = onSubmit,
                onCancel = onCancel,
                onReject = onReject,
            )
        }
    }
}

@Composable
private fun SelectableCredentialRow(
    option: WalletDemoPresentationCredentialOption,
    selectedCredentialOptions: Set<WalletDemoPresentationCredentialSelection>,
    selectedDisclosureOptions: Set<WalletDemoPresentationDisclosureSelection>,
    enabled: Boolean,
    readOnly: Boolean,
    onToggleCredential: (WalletDemoPresentationCredentialSelection) -> Unit,
    onToggleDisclosure: (WalletDemoPresentationDisclosureSelection) -> Unit,
) {
    val details = remember(option) { option.toCredentialDetails() }
    val requestedDisclosureItems = remember(option) { option.toRequestedDisclosureGroup()?.items.orEmpty() }
    var claimsOpen by rememberSaveable(option.selection.id) { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(WalletUiTestTags.presentationCredential(option.selection.id)),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!readOnly) {
            Checkbox(
                checked = option.selection in selectedCredentialOptions,
                onCheckedChange = { onToggleCredential(option.selection) },
                enabled = enabled,
                modifier = Modifier.testTag(WalletUiTestTags.presentationCredentialToggle(option.selection.id)),
            )
        }
        CredentialCard(
            details = details,
            compact = true,
            modifier = Modifier.weight(1f).testTag(WalletUiTestTags.presentationClaimsToggle(option.selection.id)),
            onClick = { claimsOpen = true },
        )
    }

    if (claimsOpen) {
        SharingClaimsDialog(
            option = option,
            details = details,
            credentialSelected = option.selection in selectedCredentialOptions,
            selectedDisclosureOptions = selectedDisclosureOptions,
            requestedDisclosureItems = requestedDisclosureItems,
            enabled = enabled,
            readOnly = readOnly,
            onToggleDisclosure = onToggleDisclosure,
            onDismiss = { claimsOpen = false },
        )
    }
}

@Composable
private fun SharingClaimsDialog(
    option: WalletDemoPresentationCredentialOption,
    details: CredentialDetails,
    credentialSelected: Boolean,
    selectedDisclosureOptions: Set<WalletDemoPresentationDisclosureSelection>,
    requestedDisclosureItems: List<ClaimItem>,
    enabled: Boolean,
    readOnly: Boolean,
    onToggleDisclosure: (WalletDemoPresentationDisclosureSelection) -> Unit,
    onDismiss: () -> Unit,
) {
    var allInformationOpen by rememberSaveable(option.selection.id) { mutableStateOf(false) }
    WalletDetailSheet(option.resolvedCardTitle(), onDismiss,
        modifier = Modifier.testTag(WalletUiTestTags.PresentationClaimsDialog),
        closeTag = WalletUiTestTags.PresentationClaimsClose) {
        CredentialOverviewSection(details)
        if (option.disclosures.isEmpty()) {
            Text("No additional claims to review", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            SharingDisclosureList(option, credentialSelected, selectedDisclosureOptions, requestedDisclosureItems,
                enabled, readOnly, onToggleDisclosure)
        }
        if (details.groups.any { it.id != "requested" }) WalletSection {
            WalletNavigationRow(stringResource(Res.string.credential_all_information),
                summary = stringResource(Res.string.credential_all_information_hint),
                icon = { WalletIcon(WalletSymbol.Info, null) },
                modifier = Modifier.testTag("review-all-credential-information"),
                onClick = { allInformationOpen = true })
        }
    }
    if (allInformationOpen) WalletDetailSheet(stringResource(Res.string.credential_all_information), { allInformationOpen = false },
        modifier = Modifier.testTag("review-all-information-details")) {
        Text(stringResource(Res.string.credential_all_information_hint), style = MaterialTheme.typography.bodyMedium)
        val summary = details.toCardDisplayData()
        CredentialSummaryRow(summary.toCardArt(), summary.issuer)
        CredentialDetailsContent(details)
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
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Requested disclosures",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        option.disclosures.withIndex().sortedBy { requestedDisclosureItems.getOrNull(it.index)?.displayOrder ?: Int.MAX_VALUE }.forEach { (index, disclosure) ->
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
