package id.walt.walletdemo.compose.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import id.walt.walletdemo.compose.logic.*
import id.walt.walletdemo.compose.ui.WalletUiTestTags

@Composable
internal fun SharingCredentialRow(
    option: WalletDemoPresentationCredentialOption,
    selectedCredentialOptions: Set<WalletDemoPresentationCredentialSelection>,
    enabled: Boolean,
    readOnly: Boolean,
    onToggleCredential: (WalletDemoPresentationCredentialSelection) -> Unit,
    hasAlternatives: Boolean = false,
) {
    val details = remember(option) { option.toCredentialDetails() }
    val extra = remember(option) { option.additionalInformationLabels() }
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
        CredentialSummaryRow(details.toCardDisplayData().toCardArt(), details.toCardDisplayData().issuer,
            Modifier.testTag(WalletUiTestTags.presentationCredential(option.selection.id)))
    }
}
