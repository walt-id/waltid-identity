package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.*
import id.walt.walletdemo.compose.ui.WalletUiTestTags

@Composable
internal fun SharingInformationSection(
    review: WalletDemoSharingReview,
    credentials: Set<WalletDemoPresentationCredentialSelection>,
    disclosures: Set<WalletDemoPresentationDisclosureSelection>,
    enabled: Boolean,
    readOnly: Boolean,
    onToggleDisclosure: (WalletDemoPresentationDisclosureSelection) -> Unit,
) {
    val groups = remember(review, credentials, disclosures) { review.informationToShare(credentials, disclosures) }
    WalletSection(title = "Information to share", modifier = Modifier.testTag("review-information-to-share")) {
        if (groups.isEmpty()) Text("Select a credential to see what will be shared.",
            modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
        groups.forEachIndexed { index, group ->
            if (index > 0) MetadataRowDivider()
            SharingInformationContent(group, disclosures, enabled, readOnly, onToggleDisclosure)
        }
    }
}

@Composable
private fun SharingInformationContent(
    group: SharingInformationGroup,
    disclosures: Set<WalletDemoPresentationDisclosureSelection>,
    enabled: Boolean,
    readOnly: Boolean,
    onToggleDisclosure: (WalletDemoPresentationDisclosureSelection) -> Unit,
) {
    val option = group.option
    val details = remember(option) { option.toCredentialDetails() }
    val summary = details.toCardDisplayData()
    val navigation = LocalWalletReviewNavigation.current
    var open by rememberSaveable(option.selection.id) { mutableStateOf(false) }
    ReviewInformationGroup(summary.title, summary.issuer,
        onDetails = { if (navigation != null) navigation.openSharing(option.selection.id) else open = true },
        detailsModifier = Modifier.testTag(WalletUiTestTags.presentationClaimsToggle(option.selection.id))) {
        if (group.fields.isEmpty()) Text("No additional information to share.", style = MaterialTheme.typography.bodySmall)
        group.fields.forEachIndexed { index, field ->
            if (index > 0) MetadataRowDivider()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.testTag(WalletUiTestTags.presentationDisclosure(field.selections.sortedBy { it.id }.first().id))) {
                if (field.optionalSelections.isNotEmpty() && !readOnly) {
                    Checkbox(field.included, enabled = enabled, onCheckedChange = { include ->
                        field.optionalSelections.filter { (it in disclosures) != include }.forEach(onToggleDisclosure)
                    }, modifier = Modifier.testTag(WalletUiTestTags.presentationDisclosureToggle(field.optionalSelections.sortedBy { it.id }.first().id)))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ClaimValueRow(field.item)
                    if (field.alwaysIncluded || field.optionalSelections.isNotEmpty()) Text(
                        when { !field.included -> "Not shared"; field.alwaysIncluded -> "Always included by this credential"; else -> "Optional" },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (open) ReviewCredentialDetailsSheet(details, claimStatus = { group.fields.disclosureStatus(it) },
        onDismiss = { open = false })
}
