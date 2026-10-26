package id.walt.walletdemo.compose.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import id.walt.walletdemo.compose.logic.*
import id.walt.walletdemo.compose.ui.WalletUiTestTags

@Composable
internal fun SharingCredentialRow(
    information: SharingCredentialInformation,
    selectedCredentialOptions: Set<WalletDemoPresentationCredentialSelection>,
    enabled: Boolean,
    readOnly: Boolean,
    onToggleCredential: (WalletDemoPresentationCredentialSelection) -> Unit,
    hasAlternatives: Boolean = false,
) {
    val option = information.option
    val summary = information.summary
    val extra = information.additionalInformationLabels
    ReviewCredentialChoice(
        selected = option.selection in selectedCredentialOptions,
        multiple = option.multiple || !hasAlternatives,
        enabled = enabled,
        readOnly = readOnly,
        hint = extra.takeIf { it.isNotEmpty() }?.let {
            "Also shares ${it.take(2).joinToString()}" + if (it.size > 2) " and ${it.size - 2} more" else ""
        },
        modifier = Modifier.testTag(WalletUiTestTags.presentationCredentialToggle(option.selection.id)),
        onSelect = {
            if (option.multiple || !hasAlternatives || option.selection !in selectedCredentialOptions) onToggleCredential(option.selection)
        },
    ) {
        CredentialSummaryRow(summary.toCardArt(), summary.issuer,
            Modifier.testTag(WalletUiTestTags.presentationCredential(option.selection.id)))
    }
}
