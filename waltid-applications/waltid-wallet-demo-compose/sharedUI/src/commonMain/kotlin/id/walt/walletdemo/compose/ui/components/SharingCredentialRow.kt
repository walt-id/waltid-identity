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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.*
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
    val navigation = LocalWalletReviewNavigation.current
    var claimsOpen by rememberSaveable(option.selection.id) { mutableStateOf(false) }

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
            onClick = { if (navigation != null) navigation.openSharing(option.selection.id) else claimsOpen = true },
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
    var page by rememberSaveable(option.selection.id) { mutableStateOf("Requested") }
    val current = CredentialInformationPage.valueOf(page)
    WalletDetailSheet(credentialInformationTitle(current), onDismiss,
        onBack = if (current == CredentialInformationPage.Requested) null else ({
            page = CredentialInformationPage.entries[current.ordinal - 1].name
        }),
        pagePath = CredentialInformationPage.entries.take(current.ordinal + 1).map { it.name },
        modifier = Modifier.testTag(WalletUiTestTags.PresentationClaimsDialog),
        closeTag = WalletUiTestTags.PresentationClaimsClose) {
        SharingCredentialInformation(option, details, credentialSelected, selectedDisclosureOptions,
            enabled, readOnly, onToggleDisclosure, CredentialInformationPage.valueOf(it),
            onPageChange = { next -> page = next.name })
    }
}
