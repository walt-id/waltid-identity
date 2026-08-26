package id.walt.walletdemo.compose.logic

import id.walt.wallet2.handlers.WalletIssuanceCredentialPreview
import id.walt.wallet2.handlers.WalletIssuanceGrant
import id.walt.wallet2.handlers.WalletIssuanceIssuerPreview
import id.walt.wallet2.handlers.WalletIssuanceMetadataProvenance
import id.walt.wallet2.handlers.WalletIssuanceOfferPreview
import id.walt.wallet2.handlers.WalletIssuanceSession
import kotlin.test.Test
import kotlin.test.assertEquals

class MobileWalletIssuanceMappingTest {
    @Test
    fun credentialLogoAccessibilityTextReachesTheOfferReviewModel() {
        val session = WalletIssuanceSession(
            id = "issuance-1",
            offer = WalletIssuanceOfferPreview(
                grant = WalletIssuanceGrant.PRE_AUTHORIZED_CODE,
                issuer = WalletIssuanceIssuerPreview(
                    identifier = "https://issuer.example",
                    name = "Example issuer",
                    locale = "en",
                    logoUri = null,
                    logoAltText = null,
                    metadataProvenance = WalletIssuanceMetadataProvenance.Unsigned,
                ),
                credentials = listOf(
                    WalletIssuanceCredentialPreview(
                        configurationId = "mdl",
                        format = "mso_mdoc",
                        name = "Mobile Driving Licence",
                        descriptionText = null,
                        logoUri = "https://issuer.example/mdl.png",
                        logoAltText = "Driving licence logo",
                        backgroundColor = "#12107c",
                        backgroundImageUri = "https://issuer.example/mdl-bg.png",
                        textColor = "#FFFFFF",
                        doctype = "org.iso.18013.5.1.mDL",
                    ),
                ),
                transactionCode = null,
            ),
        )

        val offered = session.toDemoIssuanceSession().preview.offeredCredentials.single()
        assertEquals("Driving licence logo", offered.display?.logoAltText)
        assertEquals("#12107c", offered.display?.backgroundColor)
        assertEquals("https://issuer.example/mdl-bg.png", offered.display?.backgroundImageUri)
        assertEquals("#FFFFFF", offered.display?.textColor)
        assertEquals("org.iso.18013.5.1.mDL", offered.doctype)
        assertEquals("Mobile Driving Licence", offered.resolvedCardTitle())
    }

    @Test
    fun namelessMdocOfferUsesSharedFriendlyTitleNotConfigurationId() {
        val offered = WalletDemoOfferedCredentialMetadata(
            configurationId = "org.iso.18013.5.1.mDL",
            format = "mso_mdoc",
            vct = null,
            doctype = "org.iso.18013.5.1.mDL",
            display = null,
            claims = emptyList(),
        )
        assertEquals("Mobile Driving Licence", offered.resolvedCardTitle())
    }

    @Test
    fun presentationOptionUsesResolvedCardTitleNotRawFormat() {
        val option = WalletDemoPresentationCredentialOption(
            queryId = "mdl",
            credentialId = "cred-1",
            label = "mso_mdoc",
            issuer = null,
            format = "mso_mdoc",
            credentialDataJson = """{"docType":"org.iso.18013.5.1.mDL"}""",
            disclosures = emptyList(),
        )
        assertEquals("Mobile Driving Licence", option.resolvedCardTitle())
    }

    @Test
    fun presentationOptionUsesStoredLabelWhenMetadataAndPayloadHaveNoTitle() {
        val option = WalletDemoPresentationCredentialOption(
            queryId = "pid",
            credentialId = "cred-1",
            label = "Personal ID",
            issuer = null,
            format = "mso_mdoc",
            credentialDataJson = """{"given_name":"Ada"}""",
            disclosures = emptyList(),
        )
        assertEquals("Personal ID", option.resolvedCardTitle())
    }
}
