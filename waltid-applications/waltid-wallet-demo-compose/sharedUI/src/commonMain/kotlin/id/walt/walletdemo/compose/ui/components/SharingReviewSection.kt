package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.ClaimItem
import id.walt.walletdemo.compose.logic.WalletDemoPresentationCredentialOption
import id.walt.walletdemo.compose.logic.WalletDemoPresentationCredentialSelection
import id.walt.walletdemo.compose.logic.WalletDemoPresentationDisclosureSelection
import id.walt.walletdemo.compose.logic.WalletDemoSharingReview
import id.walt.walletdemo.compose.logic.toCardDisplayData
import id.walt.walletdemo.compose.logic.toCredentialDetails
import id.walt.walletdemo.compose.logic.toRequestedDisclosureGroup
import id.walt.walletdemo.compose.ui.WalletUiTestTags

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
    onCredentialClick: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    onReject: (() -> Unit)? = null,
    readOnly: Boolean = false,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(WalletUiTestTags.PresentationReview),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SharingRequestSections(review.request, compact = compact)

        if (compact) {
            CredentialCardStack(
                details = review.credentialOptions.map { it.toCredentialDetails() },
                onOpenDetails = onCredentialClick,
            )
            if (!readOnly) {
                SharingActionsRow(
                    enabled = enabled,
                    selectionComplete = selectionComplete,
                    onSubmit = onSubmit,
                    onCancel = onCancel,
                    onReject = onReject,
                )
            }
        } else {
        Text(
            "Select credentials to share",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        review.credentialOptions.forEach { option ->
            val details = option.toCredentialDetails()
            val credentialDisplay = details.toCardDisplayData()
            val requestedDisclosureItems = option.toRequestedDisclosureGroup()?.items.orEmpty()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(WalletUiTestTags.presentationCredential(option.selection.id)),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (!readOnly) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Checkbox(
                            checked = option.selection in selectedCredentialOptions,
                            onCheckedChange = { onToggleCredential(option.selection) },
                            enabled = enabled,
                            modifier = Modifier.testTag(WalletUiTestTags.presentationCredentialToggle(option.selection.id)),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(option.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(credentialDisplay.issuer, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            option.subject?.let {
                                Text("Subject: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(option.format, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                CredentialCard(
                    details = details,
                    modifier = Modifier.padding(start = if (readOnly) 0.dp else 48.dp),
                    onClick = { onCredentialClick(details.summary.id) },
                )
                if (option.disclosures.isNotEmpty()) {
                    SharingDisclosureList(
                        option = option,
                        credentialSelected = option.selection in selectedCredentialOptions,
                        selectedDisclosureOptions = selectedDisclosureOptions,
                        requestedDisclosureItems = requestedDisclosureItems,
                        enabled = enabled,
                        readOnly = readOnly,
                        onToggleDisclosure = onToggleDisclosure,
                    )
                }
                HorizontalDivider()
            }
        }

        if (!readOnly) {
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
        modifier = Modifier.padding(start = if (readOnly) 0.dp else 48.dp),
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
private fun SharingActionsRow(
    enabled: Boolean,
    selectionComplete: Boolean,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    onReject: (() -> Unit)?,
) {
    Row(
        modifier = Modifier.testTag(WalletUiTestTags.PresentationActions),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = onSubmit,
            enabled = enabled && selectionComplete,
            modifier = Modifier.testTag(WalletUiTestTags.PresentationSubmitButton),
        ) {
            Text("Share")
        }
        onReject?.let { reject ->
            TextButton(
                onClick = reject,
                enabled = enabled,
                modifier = Modifier.testTag(WalletUiTestTags.PresentationRejectButton),
            ) {
                Text("Reject")
            }
        }
        TextButton(
            onClick = onCancel,
            enabled = enabled,
            modifier = Modifier.testTag(WalletUiTestTags.PresentationCancelButton),
        ) {
            Text(if (onReject == null) "Cancel" else "Cancel review")
        }
    }
}
