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
                    ),
                ),
                transactionCode = null,
            ),
        )

        assertEquals("Driving licence logo", session.toDemoIssuanceSession().preview.offeredCredentials.single().display?.logoAltText)
        assertEquals("#12107c", session.toDemoIssuanceSession().preview.offeredCredentials.single().display?.backgroundColor)
        assertEquals("https://issuer.example/mdl-bg.png", session.toDemoIssuanceSession().preview.offeredCredentials.single().display?.backgroundImageUri)
        assertEquals("#FFFFFF", session.toDemoIssuanceSession().preview.offeredCredentials.single().display?.textColor)
    }
}
