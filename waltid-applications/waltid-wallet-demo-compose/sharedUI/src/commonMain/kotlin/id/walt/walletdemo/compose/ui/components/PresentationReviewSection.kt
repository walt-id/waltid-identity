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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.ClaimItem
import id.walt.walletdemo.compose.logic.WalletDemoPresentationCredentialOption
import id.walt.walletdemo.compose.logic.WalletDemoPresentationCredentialSelection
import id.walt.walletdemo.compose.logic.WalletDemoPresentationDisclosureSelection
import id.walt.walletdemo.compose.logic.WalletDemoPresentationPreview
import id.walt.walletdemo.compose.logic.toCardDisplayData
import id.walt.walletdemo.compose.logic.toCredentialDetails
import id.walt.walletdemo.compose.logic.toRequestedDisclosureGroup
import id.walt.walletdemo.compose.ui.WalletUiTestTags

@Composable
internal fun PresentationReviewSection(
    preview: WalletDemoPresentationPreview,
    selectedCredentialOptions: Set<WalletDemoPresentationCredentialSelection>,
    selectedDisclosureOptions: Set<WalletDemoPresentationDisclosureSelection>,
    selectionComplete: Boolean,
    enabled: Boolean,
    readOnly: Boolean = false,
    onToggleCredential: (WalletDemoPresentationCredentialSelection) -> Unit,
    onToggleDisclosure: (WalletDemoPresentationDisclosureSelection) -> Unit,
    onCredentialClick: (String) -> Unit,
    onSubmit: () -> Unit,
    onReject: () -> Unit,
    onCancel: () -> Unit,
    showActions: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val optionsByQuery = preview.credentialOptions.groupBy { it.queryId }
    val queryLabels = optionsByQuery.keys.mapIndexed { index, queryId -> queryId to "Request ${index + 1}" }.toMap()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(WalletUiTestTags.PresentationReview),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        VerifierReviewSections(preview)

        Text(
            "Select credentials to share",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        if (preview.credentialRequirements.isNotEmpty()) {
            ReviewMetadataSection(title = "Required credential combinations") {
                preview.credentialRequirements.forEachIndexed { index, requirement ->
                    if (index > 0) HorizontalDivider()
                    Text("Requirement ${index + 1}", style = MaterialTheme.typography.labelMedium)
                    Text(
                        requirement.options.joinToString(separator = "  or  ") { option ->
                            option.joinToString(separator = " + ") { queryId -> queryLabels[queryId] ?: queryId }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        optionsByQuery.entries.forEachIndexed { queryIndex, (queryId, options) ->
            var page by remember(preview.previewHandle.value, queryId) { mutableIntStateOf(0) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("presentation-query-$queryId"),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    queryLabels[queryId] ?: "Request ${queryIndex + 1}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                PresentationCredentialOptionContent(
                    option = options[page],
                    selectedCredentialOptions = selectedCredentialOptions,
                    selectedDisclosureOptions = selectedDisclosureOptions,
                    enabled = enabled,
                    readOnly = readOnly,
                    onToggleCredential = onToggleCredential,
                    onToggleDisclosure = onToggleDisclosure,
                    onCredentialClick = onCredentialClick,
                )
                CarouselControls(
                    page = page,
                    pageCount = options.size,
                    itemName = "credential",
                    onPrevious = { page -= 1 },
                    onNext = { page += 1 },
                )
                HorizontalDivider()
            }
        }

        if (!readOnly && showActions) {
            PresentationReviewActions(
                enabled = enabled,
                selectionComplete = selectionComplete,
                onSubmit = onSubmit,
                onReject = onReject,
                onCancel = onCancel,
            )
        }
    }
}

@Composable
private fun PresentationCredentialOptionContent(
    option: WalletDemoPresentationCredentialOption,
    selectedCredentialOptions: Set<WalletDemoPresentationCredentialSelection>,
    selectedDisclosureOptions: Set<WalletDemoPresentationDisclosureSelection>,
    enabled: Boolean,
    readOnly: Boolean,
    onToggleCredential: (WalletDemoPresentationCredentialSelection) -> Unit,
    onToggleDisclosure: (WalletDemoPresentationDisclosureSelection) -> Unit,
    onCredentialClick: (String) -> Unit,
) {
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
            PresentationDisclosureList(
                option = option,
                credentialSelected = option.selection in selectedCredentialOptions,
                selectedDisclosureOptions = selectedDisclosureOptions,
                requestedDisclosureItems = requestedDisclosureItems,
                enabled = enabled,
                readOnly = readOnly,
                onToggleDisclosure = onToggleDisclosure,
            )
        }
    }
}

@Composable
private fun PresentationDisclosureList(
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
internal fun PresentationReviewActions(
    enabled: Boolean,
    selectionComplete: Boolean,
    onSubmit: () -> Unit,
    onReject: () -> Unit,
    onCancel: () -> Unit,
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
        TextButton(
            onClick = onReject,
            enabled = enabled,
            modifier = Modifier.testTag(WalletUiTestTags.PresentationRejectButton),
        ) {
            Text("Reject")
        }
        TextButton(
            onClick = onCancel,
            enabled = enabled,
            modifier = Modifier.testTag(WalletUiTestTags.PresentationCancelButton),
        ) {
            Text("Cancel review")
        }
    }
}
