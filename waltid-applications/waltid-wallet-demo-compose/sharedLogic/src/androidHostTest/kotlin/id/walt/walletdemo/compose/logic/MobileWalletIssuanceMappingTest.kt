package id.walt.walletdemo.compose.logic

import id.walt.wallet2.handlers.WalletIssuanceCredentialPreview
import id.walt.wallet2.handlers.WalletIssuanceGrant
import id.walt.wallet2.handlers.WalletIssuanceIssuerPreview
import id.walt.wallet2.handlers.WalletIssuanceMetadataProvenance
import id.walt.wallet2.handlers.WalletIssuanceOfferPreview
import id.walt.wallet2.handlers.WalletIssuanceSession
import id.walt.wallet2.handlers.WalletIssuanceOutcome
import id.walt.wallet2.handlers.WalletIssuanceError
import id.walt.wallet2.handlers.WalletIssuanceErrorCode
import id.walt.wallet2.handlers.WalletDeferredCredential
import id.walt.wallet2.handlers.CredentialIssuanceFailure
import id.walt.wallet2.handlers.CredentialIssuanceStage
import id.waltid.openid4vci.wallet.credential.CredentialIssuanceTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MobileWalletIssuanceMappingTest {
    @Test
    fun acceptedCopiesReachTheMobileSdkWithTheirOwnKeysAndDids() {
        val result = listOf(WalletDemoCredentialSelection("pid", listOf(
            WalletDemoHolderBinding("first", "did:key:first"), WalletDemoHolderBinding("second", "did:key:second"),
        ))).toMobileSelections().single()
        assertEquals("pid", result.credentialConfigurationId)
        assertEquals(listOf("first", "second"), result.holderBindings.map { it.keyId })
        assertEquals(listOf("did:key:first", "did:key:second"), result.holderBindings.map { it.did })
    }

    @Test
    fun failedTargetPreservesStoredAndDeferredProgressIncludingDatasetIdentity() {
        val result = WalletIssuanceOutcome.Failed(
            sessionId = "issuance",
            error = WalletIssuanceError(WalletIssuanceErrorCode.NETWORK, "Later target failed"),
            storedCredentialIds = listOf("stored"),
            deferredCredentials = listOf(WalletDeferredCredential(
                id = "pending", credentialConfigurationId = "pid", credentialIdentifier = "dataset-2", intervalSeconds = 7,
            )),
            failure = CredentialIssuanceFailure(CredentialIssuanceTarget("pid", "dataset-3"), CredentialIssuanceStage.REQUEST,
                notAttempted = listOf(CredentialIssuanceTarget("pid", "dataset-4"), CredentialIssuanceTarget("mdl"))),
        ).toDemoIssuanceOutcome()
        val failed = assertIs<WalletDemoIssuanceOutcome.Failed>(result)
        assertEquals(listOf("stored"), failed.storedCredentialIds)
        assertEquals(listOf(WalletDemoDeferredCredential("pending", "pid", 7, "dataset-2")), failed.deferredCredentials)
        assertTrue(failed.offerConsumed)
        assertEquals(1, failed.failedTargetCount)
        assertEquals(2, failed.notAttemptedTargetCount)
    }

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
                batchSize = 4,
            ),
        )

        val offered = session.toDemoIssuanceSession().preview.offeredCredentials.single()
        assertEquals(4, session.toDemoIssuanceSession().preview.batchSize)
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
