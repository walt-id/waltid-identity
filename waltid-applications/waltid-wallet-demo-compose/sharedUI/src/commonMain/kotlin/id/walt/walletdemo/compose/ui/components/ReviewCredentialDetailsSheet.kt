package id.walt.walletdemo.compose.ui.components

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import id.walt.walletdemo.compose.logic.*
import id.walt.walletdemo.compose.ui.WalletUiTestTags

/** Fallback for consumers without a task navigation host; the same full-detail body is reused. */
@Composable
internal fun ReviewCredentialDetailsSheet(
    details: CredentialDetails,
    claimStatus: (ClaimItem) -> String?,
    onDismiss: () -> Unit,
) {
    var technical by rememberSaveable(details.summary.id) { mutableStateOf(false) }
    WalletDetailSheet(if (technical) "Technical details" else "Credential information",
        onDismiss = onDismiss, onBack = if (technical) ({ technical = false }) else null,
        pagePath = if (technical) listOf("information", "technical") else listOf("information"),
        modifier = Modifier.testTag(WalletUiTestTags.PresentationClaimsDialog)) {
        if (technical) CredentialTechnicalInformation(details)
        else {
            CredentialSummaryRow(details.toCardDisplayData().toCardArt())
            CredentialDetailsBody(details, onTechnicalDetails = { technical = true }, claimStatus = claimStatus)
        }
    }
}
