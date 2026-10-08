package id.walt.walletdemo.compose.ui.components

import androidx.compose.runtime.Composable
import id.walt.walletdemo.compose.logic.*
import id.walt.walletdemo.compose.ui.resources.*
import org.jetbrains.compose.resources.stringResource

internal enum class CredentialInformationPage { Requested, All, Technical }

@Composable
internal fun credentialInformationTitle(page: CredentialInformationPage): String = stringResource(when (page) {
    CredentialInformationPage.Requested -> Res.string.issuance_information
    CredentialInformationPage.All -> Res.string.credential_all_information
    CredentialInformationPage.Technical -> Res.string.credential_technical_details
})

/** Full stored-information layout with consent annotations; controls remain on the review screen. */
@Composable
internal fun SharingCredentialInformation(
    details: CredentialDetails,
    informationFields: List<SharingInformationField>,
    page: CredentialInformationPage,
    onPageChange: (CredentialInformationPage) -> Unit,
) {
    if (page == CredentialInformationPage.Technical) CredentialTechnicalInformation(details)
    else {
        CredentialSummaryRow(details.toCardDisplayData().toCardArt())
        CredentialDetailsBody(details, onTechnicalDetails = { onPageChange(CredentialInformationPage.Technical) },
            claimStatus = { informationFields.disclosureStatus(it) })
    }
}
