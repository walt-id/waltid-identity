package id.walt.walletdemo.compose.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import id.walt.walletdemo.compose.logic.CredentialDetails
import id.walt.walletdemo.compose.logic.toCardDisplayData
import id.walt.walletdemo.compose.ui.resources.*
import org.jetbrains.compose.resources.stringResource

/** One information presentation retains each page's expansion and scroll state on Back. */
@Composable
internal fun CredentialInformationSheet(details: CredentialDetails, onDismiss: () -> Unit) {
    var technical by rememberSaveable(details.summary.id) { mutableStateOf(false) }
    WalletDetailSheet(stringResource(if (technical) Res.string.credential_technical_details else Res.string.issuance_information),
        onDismiss = onDismiss, onBack = if (technical) ({ technical = false }) else null,
        pagePath = if (technical) listOf("credential", "technical") else listOf("credential")) { pageKey ->
        if (pageKey == "technical") CredentialTechnicalInformation(details)
        else {
            val summary = details.toCardDisplayData()
            CredentialSummaryRow(summary.toCardArt())
            CredentialDetailsBody(details, onTechnicalDetails = { technical = true })
        }
    }
}
