package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.*
import id.walt.walletdemo.compose.ui.WalletUiTestTags
import id.walt.walletdemo.compose.ui.resources.*
import org.jetbrains.compose.resources.stringResource

internal enum class CredentialInformationPage { Requested, All, Technical }

@Composable
internal fun credentialInformationTitle(page: CredentialInformationPage): String = stringResource(when (page) {
    CredentialInformationPage.Requested -> Res.string.issuance_information
    CredentialInformationPage.All -> Res.string.credential_all_information
    CredentialInformationPage.Technical -> Res.string.credential_technical_details
})

/** Information content shared by pushed and independently presented details. */
@Composable
internal fun SharingCredentialInformation(
    option: WalletDemoPresentationCredentialOption,
    details: CredentialDetails,
    credentialSelected: Boolean,
    selectedDisclosureOptions: Set<WalletDemoPresentationDisclosureSelection>,
    enabled: Boolean,
    readOnly: Boolean,
    onToggleDisclosure: (WalletDemoPresentationDisclosureSelection) -> Unit,
    page: CredentialInformationPage,
    onPageChange: (CredentialInformationPage) -> Unit,
) {
        when (page) {
            CredentialInformationPage.Requested -> {
                val summary = details.toCardDisplayData()
                CredentialSummaryRow(summary.toCardArt())
                CredentialOverviewSection(details)
                if (option.disclosures.isEmpty()) {
                    Text("No additional claims to review", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    SharingDisclosureList(option, credentialSelected, selectedDisclosureOptions, option.toRequestedDisclosureGroup()?.items.orEmpty(),
                        enabled, readOnly, onToggleDisclosure)
                }
                if (details.groups.any { it.id != "requested" }) WalletSection {
                    WalletNavigationRow(stringResource(Res.string.credential_all_information),
                        summary = stringResource(Res.string.credential_all_information_hint),
                        icon = { WalletIcon(WalletSymbol.Info, null) },
                        modifier = Modifier.testTag("review-all-credential-information"),
                        onClick = { onPageChange(CredentialInformationPage.All) })
                }
            }
            CredentialInformationPage.All -> Column(Modifier.testTag("review-all-information-details"),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(Res.string.credential_all_information_hint), style = MaterialTheme.typography.bodyMedium)
                val summary = details.toCardDisplayData()
                CredentialSummaryRow(summary.toCardArt())
                CredentialDetailsBody(details, onTechnicalDetails = { onPageChange(CredentialInformationPage.Technical) })
            }
            CredentialInformationPage.Technical -> CredentialTechnicalInformation(details)
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
    WalletSection(title = "Requested disclosures") {
      Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        option.disclosures.withIndex().sortedBy { requestedDisclosureItems.getOrNull(it.index)?.displayOrder ?: Int.MAX_VALUE }.forEachIndexed { position, (index, disclosure) ->
            if (position > 0) MetadataRowDivider()
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
                verticalAlignment = Alignment.CenterVertically,
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
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (item != null) {
                        ClaimValueRow(item = item)
                    } else {
                        CredentialDataRow(disclosure.label) {
                            Text(disclosure.displayValue ?: disclosure.valueJson, style = MaterialTheme.typography.bodyLarge)
                        }
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
}
