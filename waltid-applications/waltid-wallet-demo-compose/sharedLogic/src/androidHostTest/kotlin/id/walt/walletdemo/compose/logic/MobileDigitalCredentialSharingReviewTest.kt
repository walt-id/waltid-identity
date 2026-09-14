package id.walt.walletdemo.compose.logic

import id.walt.wallet2.mobile.MobileWalletAnnexCDocumentRequest
import id.walt.wallet2.mobile.MobileWalletAnnexCParsedRequest
import id.walt.wallet2.mobile.MobileWalletAnnexCPreview
import id.walt.wallet2.mobile.MobileWalletDigitalCredentialPreview
import id.walt.wallet2.mobile.MobileWalletDigitalCredentialRequestInfo
import id.walt.wallet2.mobile.MobileWalletMetadataDisplay
import id.walt.wallet2.mobile.MobileWalletPresentationCredentialOption
import id.walt.wallet2.mobile.MobileWalletPresentationDisclosure
import id.walt.wallet2.mobile.MobileWalletReaderTrust
import id.walt.wallet2.mobile.MobileWalletTransactionDataItem
import id.walt.wallet2.mobile.MobileWalletVerifierMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The mapping from a platform Digital Credentials preview onto what the user reviews.
 *
 * Everything the review UI can show is decided here, so a dropped transaction or an invented protocol
 * field fails a test instead of shipping a screen that misdescribes the request.
 */
class MobileDigitalCredentialSharingReviewTest {
    /**
     * The presentation signs over transaction_data, so a review that omits it asks the user to
     * authorize a payment they were never shown.
     */
    @Test
    fun reviewCarriesEveryTransactionThePresentationWillAuthorize() {
        val review = digitalCredentialPreview(
            transactionData = listOf(
                transactionDataItem(
                    displayName = "Payment Authorization",
                    detailsJson = """{"merchant_name":"ACME Corp","amount":"42.00","currency":"EUR"}""",
                ),
            ),
        ).toSharingReview()

        val payment = review.request.transactionData.single()
        assertEquals("Payment Authorization", payment.title)
        assertEquals("ACME Corp", payment.textValue("Merchant name"))
        assertEquals("42.00", payment.textValue("Amount"))
        assertEquals("EUR", payment.textValue("Currency"))
    }

    /** Each item must survive the mapping: showing only the first authorizes the rest unseen. */
    @Test
    fun reviewCarriesEachOfSeveralTransactionDataItems() {
        val review = digitalCredentialPreview(
            transactionData = listOf(
                transactionDataItem("Payment Authorization", """{"amount":"42.00"}"""),
                transactionDataItem("Document Signing", """{"documentDigest":"abc123"}"""),
            ),
        ).toSharingReview()

        assertEquals(
            listOf("Payment Authorization", "Document Signing"),
            review.request.transactionData.map { it.title },
        )
        assertEquals("42.00", review.request.transactionData[0].textValue("Amount"))
        assertEquals("abc123", review.request.transactionData[1].textValue("Document digest"))
    }

    /**
     * Details that are not a decodable object are still carried verbatim, under a generic label rather
     * than as named fields: dropping them would hide part of what the signature will authorize.
     */
    @Test
    fun undecodableTransactionDetailsStayReviewableVerbatim() {
        val review = digitalCredentialPreview(
            transactionData = listOf(transactionDataItem("Odd Profile", "not-json")),
        ).toSharingReview()

        val odd = review.request.transactionData.single()
        assertEquals("Odd Profile", odd.title)
        assertEquals("not-json", odd.textValue("Details"))
    }

    /**
     * An unsigned Digital Credentials request has no authenticated `client_id`, so the requester is
     * headed by the origin the platform authenticated and nothing is invented to fill the gap.
     */
    @Test
    fun unsignedRequestNamesTheVerifiedOriginAndInventsNoRequestFields() {
        val review = digitalCredentialPreview().toSharingReview()

        val requester = requireNotNull(review.request.requester) { "review has no requester" }
        assertEquals("https://verifier.example", requester.fallbackName)
        assertEquals("https://verifier.example", requester.verifiedOrigin)
        assertNull(requester.display)
        assertEquals("openid4vp-v1-unsigned", review.request.technicalDetails.textValue("Protocol"))
        assertNull(review.request.technicalDetails.textValue("Client ID"))
        assertTrue(
            review.request.technicalDetails.none { it.label == "State" || it.label == "Response URI" },
            "OpenID4VP fields the DC API request does not carry were invented: ${review.request.technicalDetails}",
        )
        // No transaction data was requested, so no authorization section may be offered.
        assertTrue(review.request.transactionData.isEmpty())
    }

    /** Verifier metadata heads the requester when the request published any, over the bare origin. */
    @Test
    fun verifierMetadataOutranksTheOriginAsRequesterIdentity() {
        val review = digitalCredentialPreview(
            verifierMetadata = MobileWalletVerifierMetadata(
                display = MobileWalletMetadataDisplay(
                    name = "Example Verifier",
                    locale = null,
                    logoUri = null,
                    logoAltText = null,
                    description = null,
                ),
                clientUri = "https://verifier.example/about",
                policyUri = "https://verifier.example/privacy",
                termsOfServiceUri = "https://verifier.example/terms",
            ),
        ).toSharingReview()

        val requester = requireNotNull(review.request.requester) { "review has no requester" }
        assertEquals("Example Verifier", requester.display?.name)
        assertEquals("https://verifier.example/about", requester.details.textValue("Client URI"))
        assertEquals("https://verifier.example/privacy", requester.details.textValue("Privacy policy"))
        assertEquals("https://verifier.example/terms", requester.details.textValue("Terms of service"))
    }

    /**
     * Stored issuer card art is sidecar metadata, not credential data. Dropping it on the DC API
     * present path leaves the review painting constructed fallback art.
     */
    @Test
    fun reviewCarriesStoredCredentialDisplayMetadata() {
        val metadataJson = """
            {
              "credentialDisplay": [
                {
                  "name": "Personal ID",
                  "background_image": { "uri": "https://issuer.example/pid-bg.png" }
                }
              ]
            }
        """.trimIndent()
        val review = digitalCredentialPreview(
            credentialOptions = listOf(credentialOption(metadataJson = metadataJson)),
        ).toSharingReview()

        val option = review.credentialOptions.single()
        assertEquals(metadataJson, option.metadataJson)
        assertEquals(
            "https://issuer.example/pid-bg.png",
            option.toCredentialDetails().toCardDisplayData().backgroundImageUri,
        )
    }

    /** The claims the request asks for are reviewed through the ordinary disclosure model. */
    @Test
    fun requestedClaimsAreReviewableAsDisclosures() {
        val review = digitalCredentialPreview().toSharingReview()

        val option = review.credentialOptions.single()
        assertEquals("Driving licence", option.label)
        val disclosure = option.disclosures.single()
        assertEquals("Given name", disclosure.label)
        assertEquals("Ada", disclosure.displayValue)
    }

    /** `dc_api.jwt` is the only DC API response mode that encrypts, so it is the only one reported so. */
    @Test
    fun responseProtectionFollowsTheResponseMode() {
        val encrypted = digitalCredentialPreview(responseMode = "dc_api.jwt").toSharingReview()
        val encryption = encrypted.request.responseProtection
        assertTrue(encryption is WalletDemoSharingResponseProtection.Encrypted, "expected encryption, got $encryption")
        assertEquals(WalletDemoSharingEncryptionMechanism.DcApiJwt, encryption.mechanism)

        assertEquals(
            WalletDemoSharingResponseProtection.None,
            digitalCredentialPreview(responseMode = "dc_api").toSharingReview().request.responseProtection,
        )
    }

    /**
     * The OpenID4VP DC API has no reader authentication, which maps to no reader-trust state at all rather
     * than to a state that reads as a failed check.
     */
    @Test
    fun protocolWithoutReaderAuthenticationHasNoReaderTrustState() {
        assertNull(digitalCredentialPreview().toSharingReview().request.readerTrust)
    }

    @Test
    fun annexCReaderTrustStatesMapOntoReviewStates() {
        assertEquals(
            WalletDemoReaderTrust.NotAuthenticated,
            annexCPreview(MobileWalletReaderTrust.NotAuthenticated).toSharingReview().request.readerTrust,
        )
        assertEquals(
            WalletDemoReaderTrust.PendingVerification,
            annexCPreview(MobileWalletReaderTrust.PendingRawRequest).toSharingReview().request.readerTrust,
        )
        assertEquals(
            WalletDemoReaderTrust.Untrusted("No reader trust policy is configured"),
            annexCPreview(MobileWalletReaderTrust.Untrusted("No reader trust policy is configured"))
                .toSharingReview().request.readerTrust,
        )
        assertEquals(
            WalletDemoReaderTrust.Trusted("CN=Example Reader"),
            annexCPreview(MobileWalletReaderTrust.Trusted("CN=Example Reader")).toSharingReview().request.readerTrust,
        )
    }

    /**
     * Annex C always session-encrypts and carries no OpenID4VP request parameters, so the review states
     * the fixed HPKE suite and lists only the documents the DeviceRequest asked for.
     */
    @Test
    fun annexCReviewStatesItsFixedEncryptionAndRequestedDocuments() {
        val review = annexCPreview(MobileWalletReaderTrust.NotAuthenticated).toSharingReview()

        val encryption = review.request.responseProtection
        assertTrue(encryption is WalletDemoSharingResponseProtection.Encrypted, "expected encryption, got $encryption")
        assertEquals(WalletDemoSharingEncryptionMechanism.AnnexCHpke, encryption.mechanism)
        assertEquals("DHKEM(P-256, HKDF-SHA256)", encryption.keyManagementAlgorithm)
        assertEquals("AES-128-GCM", encryption.contentEncryptionAlgorithm)
        assertEquals(MDL_DOC_TYPE, review.request.technicalDetails.textValue("Requested documents"))
        assertEquals("https://verifier.example", review.request.requester?.verifiedOrigin)
    }

    /**
     * Annex C requests every listed document, so each becomes its own requirement; otherwise a partial
     * answer would look complete and Share would enable too early.
     */
    @Test
    fun annexCRequiresOneCredentialPerRequestedDocument() {
        val review = annexCPreview(
            readerTrust = MobileWalletReaderTrust.NotAuthenticated,
            credentialOptions = listOf(
                credentialOption(queryId = MDL_DOC_TYPE, credentialId = "credential-1"),
                credentialOption(queryId = PHOTO_ID_DOC_TYPE, credentialId = "credential-2"),
            ),
        ).toSharingReview()

        assertEquals(
            listOf(listOf(listOf(MDL_DOC_TYPE)), listOf(listOf(PHOTO_ID_DOC_TYPE))),
            review.credentialRequirements.map { it.options },
        )
        assertTrue(
            review.hasCompleteCredentialSelection(review.defaultCredentialSelection()),
            "the default selection does not answer both requested documents",
        )
        assertTrue(
            !review.hasCompleteCredentialSelection(
                setOf(WalletDemoPresentationCredentialSelection(MDL_DOC_TYPE, "credential-1")),
            ),
            "one document out of two counted as a complete answer",
        )
    }

    private fun digitalCredentialPreview(
        transactionData: List<MobileWalletTransactionDataItem> = emptyList(),
        verifierMetadata: MobileWalletVerifierMetadata? = null,
        responseMode: String? = "dc_api",
        credentialOptions: List<MobileWalletPresentationCredentialOption> = listOf(credentialOption()),
    ): MobileWalletDigitalCredentialPreview = MobileWalletDigitalCredentialPreview(
        requestId = "request-1",
        protocol = "openid4vp-v1-unsigned",
        verifiedOrigin = "https://verifier.example",
        request = MobileWalletDigitalCredentialRequestInfo(
            clientId = null,
            verifierMetadata = verifierMetadata,
            nonce = "nonce-123",
            responseMode = responseMode,
            transactionData = transactionData,
        ),
        credentialOptions = credentialOptions,
        credentialRequirements = emptyList(),
        readerTrust = MobileWalletReaderTrust.NotApplicable,
    )

    private fun annexCPreview(
        readerTrust: MobileWalletReaderTrust,
        credentialOptions: List<MobileWalletPresentationCredentialOption> = listOf(
            credentialOption(queryId = MDL_DOC_TYPE),
        ),
    ): MobileWalletAnnexCPreview = MobileWalletAnnexCPreview(
        requestId = "request-1",
        verifiedOrigin = "https://verifier.example",
        parsedRequest = MobileWalletAnnexCParsedRequest(
            documents = credentialOptions.map { option ->
                MobileWalletAnnexCDocumentRequest(
                    docType = option.queryId,
                    namespaces = mapOf(MDL_NAMESPACE to listOf("given_name")),
                )
            },
        ),
        credentialOptions = credentialOptions,
        readerTrust = readerTrust,
    )

    private fun credentialOption(
        queryId: String = "pid",
        credentialId: String = "credential-1",
        metadataJson: String? = null,
    ): MobileWalletPresentationCredentialOption = MobileWalletPresentationCredentialOption(
        queryId = queryId,
        credentialId = credentialId,
        format = "mso_mdoc",
        issuer = "Test Issuer",
        subject = null,
        label = "Driving licence",
        credentialDataJson = "{}",
        disclosures = listOf(
            MobileWalletPresentationDisclosure(
                path = "$MDL_NAMESPACE/given_name",
                name = "given_name",
                valueJson = "\"Ada\"",
                displayValue = "Ada",
                selectivelyDisclosable = false,
            ),
        ),
        metadataJson = metadataJson,
    )

    private fun transactionDataItem(
        displayName: String,
        detailsJson: String,
    ): MobileWalletTransactionDataItem = MobileWalletTransactionDataItem(
        type = "org.waltid.transaction-data.${displayName.lowercase().replace(' ', '-')}",
        displayName = displayName,
        credentialQueryIds = listOf("pid"),
        supportedFields = emptyList(),
        rawJson = detailsJson,
        detailsJson = detailsJson,
    )

    private fun ClaimGroup.textValue(label: String): String? =
        items.firstOrNull { it.label == label }?.let { (it.value as? DisplayValue.Text)?.value ?: it.rawValue }

    private fun List<WalletDemoSharingDetail>.textValue(label: String): String? =
        firstOrNull { it.label == label }?.value

    private companion object {
        const val MDL_DOC_TYPE = "org.iso.18013.5.1.mDL"
        const val PHOTO_ID_DOC_TYPE = "org.iso.23220.photoid.1"
        const val MDL_NAMESPACE = "org.iso.18013.5.1"
    }
}
