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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import id.walt.walletdemo.compose.logic.ClaimItem
import id.walt.walletdemo.compose.logic.CredentialDetails
import id.walt.walletdemo.compose.logic.WalletDemoPresentationCredentialOption
import id.walt.walletdemo.compose.logic.WalletDemoPresentationCredentialSelection
import id.walt.walletdemo.compose.logic.WalletDemoPresentationDisclosureSelection
import id.walt.walletdemo.compose.logic.WalletDemoSharingReview
import id.walt.walletdemo.compose.logic.resolvedCardTitle
import id.walt.walletdemo.compose.logic.toCredentialDetails
import id.walt.walletdemo.compose.logic.toRequestedDisclosureGroup
import id.walt.walletdemo.compose.ui.SystemBackHandler
import id.walt.walletdemo.compose.ui.WalletUiTestTags
import id.walt.walletdemo.compose.ui.exportTestTagsForPlatformAutomation
import id.walt.walletdemo.compose.ui.resources.Res
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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(WalletUiTestTags.PresentationReview),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SharingRequestSections(review.request)

        if (compact) {
            var claimsOptionId by rememberSaveable { mutableStateOf<String?>(null) }
            val claimsOption = review.credentialOptions.firstOrNull { it.selection.id == claimsOptionId }
            SystemBackHandler(enabled = claimsOption != null) {
                claimsOptionId = null
            }
            CredentialCardStack(
                details = review.credentialOptions.map { it.toCredentialDetails() },
                onOpenDetails = { detailsId ->
                    claimsOptionId = detailsId
                },
            )
            claimsOption?.let { option ->
                SharingClaimsDialog(
                    option = option,
                    details = option.toCredentialDetails(),
                    credentialSelected = option.selection in selectedCredentialOptions,
                    selectedDisclosureOptions = selectedDisclosureOptions,
                    requestedDisclosureItems = option.toRequestedDisclosureGroup()?.items.orEmpty(),
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
    val details = option.toCredentialDetails()
    val requestedDisclosureItems = option.toRequestedDisclosureGroup()?.items.orEmpty()
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
        Box(
            modifier = Modifier
                .weight(1f)
                .testTag(WalletUiTestTags.presentationClaimsToggle(option.selection.id))
                .clickable { claimsOpen = true },
        ) {
            CredentialCard(
                details = details,
                compact = true,
                onClick = { claimsOpen = true },
            )
        }
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
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .exportTestTagsForPlatformAutomation()
                .testTag(WalletUiTestTags.PresentationClaimsDialog),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        option.resolvedCardTitle(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag(WalletUiTestTags.PresentationClaimsClose),
                    ) {
                        Text("Close")
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    CredentialOverviewSection(details)
                    if (option.disclosures.isEmpty()) {
                        Text(
                            "No additional claims to review",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        SharingDisclosureList(
                            option = option,
                            credentialSelected = credentialSelected,
                            selectedDisclosureOptions = selectedDisclosureOptions,
                            requestedDisclosureItems = requestedDisclosureItems,
                            enabled = enabled,
                            readOnly = readOnly,
                            onToggleDisclosure = onToggleDisclosure,
                        )
                    }
                }
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
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Requested disclosures",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
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

@Composable
internal fun SharingActionsRow(
    enabled: Boolean,
    selectionComplete: Boolean,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    onReject: (() -> Unit)?,
    presentation: ReviewActionPresentation = ReviewActionPresentation.Sharing,
) {
    val submitLabel = when (presentation) {
        ReviewActionPresentation.Sharing -> "Share"
        ReviewActionPresentation.Proximity -> stringResource(Res.string.proximity_approve)
    }
    val rejectLabel = when (presentation) {
        ReviewActionPresentation.Sharing -> "Reject"
        ReviewActionPresentation.Proximity -> stringResource(Res.string.proximity_decline)
    }
    val cancelLabel = when (presentation) {
        ReviewActionPresentation.Sharing -> if (onReject == null) "Cancel" else "Cancel review"
        ReviewActionPresentation.Proximity -> stringResource(Res.string.proximity_cancel)
    }
    Row(
        modifier = Modifier.testTag(WalletUiTestTags.PresentationActions),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = onSubmit,
            enabled = enabled && selectionComplete,
            modifier = Modifier.testTag(presentation.submitTestTag),
        ) {
            Text(submitLabel)
        }
        TextButton(
            onClick = onCancel,
            enabled = enabled,
            modifier = Modifier.testTag(presentation.cancelTestTag),
        ) {
            Text(cancelLabel)
        }
        onReject?.let { reject ->
            TextButton(
                onClick = reject,
                enabled = enabled,
                modifier = Modifier.testTag(presentation.rejectTestTag),
            ) {
                Text(rejectLabel)
            }
        }
    }
}

internal enum class ReviewActionPresentation {
    Sharing,
    Proximity;

    val submitTestTag: String
        get() = when (this) {
            Sharing -> WalletUiTestTags.PresentationSubmitButton
            Proximity -> WalletUiTestTags.ProximityApprove
        }

    val rejectTestTag: String
        get() = when (this) {
            Sharing -> WalletUiTestTags.PresentationRejectButton
            Proximity -> WalletUiTestTags.ProximityDecline
        }

    val cancelTestTag: String
        get() = when (this) {
            Sharing -> WalletUiTestTags.PresentationCancelButton
            Proximity -> WalletUiTestTags.ProximityCancel
        }
}
