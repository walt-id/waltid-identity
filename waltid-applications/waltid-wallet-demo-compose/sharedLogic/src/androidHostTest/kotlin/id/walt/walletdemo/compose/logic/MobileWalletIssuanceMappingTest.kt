package id.walt.walletdemo.compose.logic

import id.walt.wallet2.handlers.WalletIssuanceClaimPreview
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
                    ),
                ),
                transactionCode = null,
            ),
        )

        assertEquals(
            "Driving licence logo",
            session.toDemoIssuanceSession().preview.offeredCredentials.single().display?.logoAltText,
        )
    }

    @Test
    fun advertisedClaimTextReachesTheOfferReviewVerbatim() {
        val session = WalletIssuanceSession(
            id = "session-1",
            offer = WalletIssuanceOfferPreview(
                grant = WalletIssuanceGrant.PRE_AUTHORIZED_CODE,
                issuer = WalletIssuanceIssuerPreview(
                    identifier = "https://issuer.example",
                    name = "Example Issuer",
                    locale = "en",
                    logoUri = null,
                    logoAltText = null,
                    metadataProvenance = WalletIssuanceMetadataProvenance.Unsigned,
                ),
                credentials = listOf(
                    WalletIssuanceCredentialPreview(
                        configurationId = "example-id",
                        format = "vc+sd-jwt",
                        name = "Example credential",
                        descriptionText = null,
                        logoUri = null,
                        claims = listOf(
                            WalletIssuanceClaimPreview(
                                path = listOf("given_name"),
                                mandatory = true,
                                displayName = "  Preferred given name  ",
                            )
                        ),
                    )
                ),
                transactionCode = null,
            ),
        )

        val claim = session.toDemoIssuanceSession().preview.offeredCredentials.single().claims.single()

        assertEquals("  Preferred given name  ", claim.displayName)
        assertEquals(listOf("given_name"), claim.path)
        assertEquals(true, claim.mandatory)
    }
}
