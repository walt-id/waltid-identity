package id.walt.walletdemo.compose.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WalletDemoReviewIslandsTest {

    @Test
    fun storedCredentialReusesIslandsWithoutPromotingProtocolData() {
        val details = CredentialSummary(
            id = "credential-1",
            format = "vc+sd-jwt",
            issuer = "https://issuer.example",
            subject = "did:key:holder",
            label = "Personal ID",
            addedAt = "2026-08-20T08:00:00Z",
            credentialDataJson = """{"given_name":"Ada","vct":"urn:eudi:pid:1"}""",
            metadataJson = """{"issuerDisplay":[{"name":"Example Issuer"}]}""",
        ).toCredentialDetails()

        val islands = details.toStoredReviewIslands()

        assertEquals(
            listOf(
                WalletDemoReviewIslandKind.Credential,
                WalletDemoReviewIslandKind.Issuer,
                WalletDemoReviewIslandKind.Information,
                WalletDemoReviewIslandKind.ValidityAndStatus,
            ),
            islands.map(WalletDemoReviewIsland::kind),
        )
        assertTrue(islands.all { it.context == WalletDemoReviewSurfaceContext.Stored })
        assertEquals("Example Issuer", islands[1].title)
        assertTrue(islands[2].visibleExpandedValues.any { it.label == "Given name" && it.value == "Ada" })

        val normalText = islands.flatMap { it.visibleSummaryValues + it.visibleExpandedValues }
            .flatMap { listOfNotNull(it.label, it.value, it.supportingText) }
        assertFalse("vc+sd-jwt" in normalText)
        assertFalse("urn:eudi:pid:1" in normalText)
        assertTrue(
            islands.first().visibleTechnicalSections
                .flatMap { it.visibleValues }
                .any { it.value == "vc+sd-jwt" }
        )
    }

    @Test
    fun issuanceUsesSemanticOrderAndKeepsProtocolIdentifiersInTechnicalDetails() {
        val preview = offerPreview(
            transactionCode = WalletDemoTransactionCodeRequirement(
                inputMode = WalletDemoTransactionCodeInputMode.Numeric,
                length = 6,
                description = "Use the code from the Issuer",
            )
        )

        val islands = preview.toReviewIslands(WalletDemoReviewSurfaceContext.PlatformInvoked)

        assertEquals(
            listOf(
                WalletDemoReviewIslandKind.Issuer,
                WalletDemoReviewIslandKind.Credential,
                WalletDemoReviewIslandKind.Information,
                WalletDemoReviewIslandKind.RequiredAction,
            ),
            islands.map(WalletDemoReviewIsland::kind),
        )
        assertEquals("Photo ID", islands[1].title)
        assertEquals("Transaction code", islands.last().title)
        assertTrue(islands.all { it.context == WalletDemoReviewSurfaceContext.PlatformInvoked })

        val summaryText = islands.flatMap { it.visibleSummaryValues + it.visibleExpandedValues }
            .flatMap { listOfNotNull(it.label, it.value, it.supportingText) }
        assertFalse("org.iso.23220.photoid.1" in summaryText)
        assertFalse("mso_mdoc" in summaryText)

        val credentialTechnicalText = islands[1].visibleTechnicalSections
            .flatMap { it.visibleValues }
            .flatMap { listOfNotNull(it.label, it.value) }
        assertTrue("org.iso.23220.photoid.1" in credentialTechnicalText)
        assertTrue("mso_mdoc" in credentialTechnicalText)

        val actionTechnicalText = islands.last().visibleTechnicalSections
            .flatMap { it.visibleValues }
            .flatMap { listOfNotNull(it.label, it.value) }
        assertTrue("Expected length" in actionTechnicalText)
        assertTrue("6" in actionTechnicalText)
    }

    @Test
    fun authorizationCodeCreatesAnIssuerSignInIslandWithoutAnInformationPlaceholder() {
        val preview = offerPreview(
            requiresIssuerAuthentication = true,
            claims = emptyList(),
        )

        val islands = preview.toReviewIslands()

        assertEquals(
            listOf(
                WalletDemoReviewIslandKind.Issuer,
                WalletDemoReviewIslandKind.Credential,
                WalletDemoReviewIslandKind.RequiredAction,
            ),
            islands.map(WalletDemoReviewIsland::kind),
        )
        assertEquals("Issuer sign-in", islands.last().title)
        assertTrue(islands.last().visibleExpandedValues.single().value.orEmpty().contains("credential is added"))
    }

    @Test
    fun credentialSummaryUsesFriendlyStandardNameWithoutExposingDocumentType() {
        val preview = offerPreview().copy(
            offeredCredentials = listOf(
                offerPreview().offeredCredentials.single().copy(
                    configurationId = "org.iso.18013.5.1.mDL",
                    doctype = "org.iso.18013.5.1.mDL",
                    display = null,
                )
            )
        )

        val credentialIsland = preview.toReviewIslands()
            .single { it.kind == WalletDemoReviewIslandKind.Credential }

        assertEquals("Mobile driving licence", credentialIsland.title)
        assertFalse(
            (credentialIsland.visibleSummaryValues + credentialIsland.visibleExpandedValues)
                .any { it.value == "org.iso.18013.5.1.mDL" }
        )
        assertTrue(
            credentialIsland.visibleTechnicalSections
                .flatMap { it.visibleValues }
                .any { it.value == "org.iso.18013.5.1.mDL" }
        )
    }

    @Test
    fun sharingUsesVerifierCredentialInformationAndTransactionOrder() {
        val review = WalletDemoSharingReview(
            request = WalletDemoSharingRequest(
                verifier = WalletDemoSharingVerifier(
                    fallbackName = "https://verifier.example",
                    verifiedOrigin = "https://verifier.example",
                ),
                responseProtection = WalletDemoSharingResponseProtection.Encrypted(
                    mechanism = WalletDemoSharingEncryptionMechanism.DcApiJwt,
                ),
                transactionData = listOf(
                    ClaimGroup(
                        title = "Payment authorization",
                        items = listOf(
                            ClaimItem(
                                path = ClaimItemPath.topLevel("amount"),
                                label = "Amount",
                                value = DisplayValue.Text("42.00 EUR"),
                            )
                        ),
                    )
                ),
                technicalDetails = listOf(WalletDemoSharingDetail("Response mode", "dc_api.jwt")),
            ),
            credentialOptions = listOf(
                WalletDemoPresentationCredentialOption(
                    queryId = "pid",
                    credentialId = "credential-1",
                    label = "Photo ID",
                    issuer = "Example Issuer",
                    format = "mso_mdoc",
                    credentialDataJson = "{}",
                    disclosures = listOf(
                        WalletDemoPresentationDisclosure(
                            label = "Given name",
                            path = "given_name",
                            valueJson = "\"Ada\"",
                            displayValue = "Ada",
                            selectivelyDisclosable = false,
                        )
                    ),
                )
            ),
        )

        val islands = review.toReviewIslands(WalletDemoReviewSurfaceContext.PlatformInvoked)

        assertEquals(
            listOf(
                WalletDemoReviewIslandKind.Verifier,
                WalletDemoReviewIslandKind.Credential,
                WalletDemoReviewIslandKind.Information,
                WalletDemoReviewIslandKind.PurposeAndTransaction,
            ),
            islands.map(WalletDemoReviewIsland::kind),
        )
        assertEquals("Verified website", islands.first().subtitle)
        assertTrue(islands.first().initiallyExpanded)
        assertTrue(islands[1].initiallyExpanded)
        assertTrue(islands[2].initiallyExpanded)
        assertEquals("Payment authorization", islands.last().title)

        val normalText = islands.flatMap { it.visibleSummaryValues + it.visibleExpandedValues }
            .flatMap { listOfNotNull(it.label, it.value, it.supportingText) }
        assertFalse("dc_api.jwt" in normalText)
        assertTrue("Protected response" in normalText)

        val verifierTechnicalText = islands.first().visibleTechnicalSections
            .flatMap { it.visibleValues }
            .flatMap { listOfNotNull(it.label, it.value) }
        assertTrue("OpenID4VP encrypted response" in verifierTechnicalText)
    }

    @Test
    fun platformVerifiedAppOriginIsNotDescribedAsAWebsite() {
        val review = WalletDemoSharingReview(
            request = WalletDemoSharingRequest(
                verifier = WalletDemoSharingVerifier(
                    fallbackName = "android:apk-key-hash:example",
                    verifiedOrigin = "android:apk-key-hash:example",
                ),
            ),
            credentialOptions = emptyList(),
        )

        val verifier = review.toReviewIslands(WalletDemoReviewSurfaceContext.PlatformInvoked).first()

        assertEquals("Verified app", verifier.subtitle)
    }

    @Test
    fun unknownVerifiedOriginSchemeUsesNeutralCopy() {
        assertEquals("Verified origin", "custom:origin".verifiedOriginLabel())
    }

    private fun offerPreview(
        transactionCode: WalletDemoTransactionCodeRequirement? = null,
        requiresIssuerAuthentication: Boolean = false,
        claims: List<WalletDemoCredentialClaimMetadata> = listOf(
            WalletDemoCredentialClaimMetadata(
                path = listOf("org.iso.23220.1", "given_name"),
                mandatory = true,
                displayName = "Given name",
            )
        ),
    ): WalletDemoOfferPreview = WalletDemoOfferPreview(
        issuer = WalletDemoIssuerMetadata(
            credentialIssuer = "https://issuer.example",
            display = WalletDemoMetadataDisplay(
                name = "Example Issuer",
                logoUri = null,
                logoAltText = null,
            ),
        ),
        offeredCredentials = listOf(
            WalletDemoOfferedCredentialMetadata(
                configurationId = "org.iso.23220.photoid.1",
                format = "mso_mdoc",
                vct = null,
                doctype = "org.iso.23220.photoid.1",
                display = WalletDemoMetadataDisplay(
                    name = "Photo ID",
                    logoUri = null,
                    logoAltText = null,
                ),
                claims = claims,
            )
        ),
        transactionCode = transactionCode,
        requiresIssuerAuthentication = requiresIssuerAuthentication,
    )
}
