package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.*
import id.walt.walletdemo.compose.ui.SystemBackHandler
import id.walt.walletdemo.compose.ui.WalletUiTestTags
import id.walt.walletdemo.compose.ui.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SharingCredentialRow(
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

    SystemBackHandler(enabled = claimsOpen) { claimsOpen = false }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag(WalletUiTestTags.presentationCredential(option.selection.id)),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!readOnly) {
            Checkbox(
                checked = option.selection in selectedCredentialOptions,
                onCheckedChange = { onToggleCredential(option.selection) },
                enabled = enabled,
                modifier = Modifier.testTag(WalletUiTestTags.presentationCredentialToggle(option.selection.id))
                    .semantics { contentDescription = option.resolvedCardTitle() },
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
                        modifier = Modifier.testTag(WalletUiTestTags.presentationDisclosureToggle(selection.id))
                            .semantics { contentDescription = item?.label ?: disclosure.label },
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
