package id.walt.walletdemo.compose.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import id.walt.walletdemo.compose.logic.CredentialDisplayNormalizer
import id.walt.walletdemo.compose.logic.WalletDemoMetadataDisplay
import id.walt.walletdemo.compose.logic.WalletDemoPresentationCredentialOption
import id.walt.walletdemo.compose.logic.WalletDemoPresentationCredentialRequirement
import id.walt.walletdemo.compose.logic.WalletDemoPresentationCredentialSelection
import id.walt.walletdemo.compose.logic.WalletDemoPresentationDisclosure
import id.walt.walletdemo.compose.logic.WalletDemoReaderTrust
import id.walt.walletdemo.compose.logic.WalletDemoSharingDetail
import id.walt.walletdemo.compose.logic.WalletDemoSharingEncryptionMechanism
import id.walt.walletdemo.compose.logic.WalletDemoSharingRequest
import id.walt.walletdemo.compose.logic.WalletDemoSharingRequester
import id.walt.walletdemo.compose.logic.WalletDemoSharingResponseProtection
import id.walt.walletdemo.compose.logic.WalletDemoSharingReview
import id.walt.walletdemo.compose.logic.WalletDemoSharingSelection
import id.walt.walletdemo.compose.logic.WalletDemoTransactionDataItem
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The platform-invoked sharing review, exercised through the same screen a Digital Credentials
 * provider host shows.
 *
 * These cover what a provider surface has to get right and what an in-app OpenID4VP review never
 * exercises: reviewing a request with no protocol rejection channel, and rendering the concepts a
 * platform transport does have (verified origin, reader authentication, session encryption) without
 * inventing the ones it does not.
 */
@OptIn(ExperimentalTestApi::class)
class WalletDemoSharingReviewTestScenarios {

    /**
     * The `dc_api.jwt` case. The signature covers transaction_data, so every authorized value has to be
     * on the review, and the encryption the response mode implies has to be stated.
     */
    fun digitalCredentialReviewShowsOriginTransactionDataAndEncryption() = runComposeUiTest {
        setContent {
            WalletDemoSharingReviewScreen(
                review = digitalCredentialReview(),
                title = "Share digital credential?",
                onSubmit = {},
                onCancel = {},
            )
        }

        onNodeWithTag(WalletDemoSharingReviewTestTags.Review).assertIsDisplayed()
        onNodeWithTag(WalletDemoSharingReviewTestTags.RequesterSection).performScrollTo().assertIsDisplayed()
        // An unsigned Digital Credentials request has no verifier metadata, so the authenticated origin
        // is the requester identity - and is shown once, not also repeated as a labelled row.
        onNodeWithText("https://verifier.example").performScrollTo().assertIsDisplayed()
        onAllNodesWithText("https://verifier.example").assertCountEquals(1)
        onAllNodesWithText("Verified website").assertCountEquals(0)
        onNodeWithText("Payment Authorization").performScrollTo().assertIsDisplayed()
        onNodeWithText("42.00").performScrollTo().assertIsDisplayed()
        onNodeWithText("EUR").performScrollTo().assertIsDisplayed()
        onNodeWithText("ACME Corp").performScrollTo().assertIsDisplayed()
        onNodeWithTag(WalletDemoSharingReviewTestTags.ResponseProtectionSection).performScrollTo().assertIsDisplayed()
        onNodeWithText("OpenID4VP dc_api.jwt").performScrollTo().assertIsDisplayed()
        onNodeWithText("Given name").performScrollTo().assertIsDisplayed()
        onNodeWithText("Ada").performScrollTo().assertIsDisplayed()
    }

    /**
     * Self-asserted verifier metadata heads the section, and the authenticated origin stays visible
     * beside it under its own label: the two have different weight, and a review that showed only the
     * name a request asked to be called would hide the one requester fact that was actually verified.
     */
    fun verifiedOriginStaysVisibleBesideSelfAssertedVerifierMetadata() = runComposeUiTest {
        setContent {
            WalletDemoSharingReviewScreen(
                review = digitalCredentialReview().let { review ->
                    review.copy(
                        request = review.request.copy(
                            requester = WalletDemoSharingRequester(
                                display = WalletDemoMetadataDisplay(
                                    name = "Example Verifier",
                                    logoUri = null,
                                    logoAltText = null,
                                    description = null,
                                ),
                                fallbackName = "https://verifier.example",
                                verifiedOrigin = "https://verifier.example",
                            ),
                        ),
                    )
                },
                title = "Share digital credential?",
                onSubmit = {},
                onCancel = {},
            )
        }

        onNodeWithText("Example Verifier").performScrollTo().assertIsDisplayed()
        onNodeWithText("Verified website").performScrollTo().assertIsDisplayed()
        onNodeWithText("https://verifier.example").performScrollTo().assertIsDisplayed()
    }

    /**
     * A transport with no protocol-level refusal offers Share and Cancel only. A Reject button here
     * would promise the requester is told something the platform has no channel to tell it.
     */
    fun credentialManagerReviewOffersShareAndCancelWithoutReject() = runComposeUiTest {
        var submitted: WalletDemoSharingSelection? = null
        var cancelled = false
        setContent {
            WalletDemoSharingReviewScreen(
                review = digitalCredentialReview(),
                title = "Share digital credential?",
                onSubmit = { submitted = it },
                onCancel = { cancelled = true },
            )
        }

        onNodeWithTag(WalletDemoSharingReviewTestTags.CancelButton).performScrollTo().assertIsDisplayed()
        onAllNodesWithTag(WalletUiTestTags.PresentationRejectButton).assertCountEquals(0)
        onAllNodesWithText("Reject").assertCountEquals(0)

        onNodeWithTag(WalletDemoSharingReviewTestTags.ShareButton).performScrollTo().performClick()
        assertEquals(
            setOf(WalletDemoPresentationCredentialSelection("pid", "credential-1")),
            submitted?.credentials,
        )
        assertEquals(false, cancelled)

        onNodeWithTag(WalletDemoSharingReviewTestTags.CancelButton).performScrollTo().performClick()
        assertEquals(true, cancelled)
    }

    /** A submission in flight must not be able to start a second one for the same request. */
    fun disabledReviewCannotBeSubmittedTwice() = runComposeUiTest {
        setContent {
            WalletDemoSharingReviewScreen(
                review = digitalCredentialReview(),
                title = "Share digital credential?",
                onSubmit = {},
                onCancel = {},
                enabled = false,
            )
        }

        onNodeWithTag(WalletDemoSharingReviewTestTags.ShareButton).performScrollTo().assertIsNotEnabled()
        onNodeWithTag(WalletDemoSharingReviewTestTags.CancelButton).performScrollTo().assertIsNotEnabled()
    }

    /**
     * A protocol without reader authentication gets no reader section at all, rather than one reporting
     * an absent reader: the OpenID4VP Digital Credentials API has no reader to be trusted or not.
     */
    fun reviewWithoutReaderAuthenticationShowsNoReaderSection() = runComposeUiTest {
        setContent {
            WalletDemoSharingReviewScreen(
                review = digitalCredentialReview(),
                title = "Share digital credential?",
                onSubmit = {},
                onCancel = {},
            )
        }

        onAllNodesWithTag(WalletDemoSharingReviewTestTags.ReaderTrustSection).assertCountEquals(0)
    }

    /**
     * Annex C does have reader authentication, and an unrecognised reader is described as a trust
     * decision rather than as a verification failure - a request whose reader signature failed never
     * reaches a review at all, so calling this one a bad signature would misdescribe it.
     */
    fun untrustedReaderIsDescribedAsATrustDecisionNotASignatureFailure() = runComposeUiTest {
        setContent {
            WalletDemoSharingReviewScreen(
                review = annexCReview(WalletDemoReaderTrust.Untrusted("No reader trust policy is configured")),
                title = "Share mobile document?",
                onSubmit = {},
                onCancel = {},
            )
        }

        onNodeWithTag(WalletDemoSharingReviewTestTags.ReaderTrustSection).performScrollTo().assertIsDisplayed()
        onNodeWithText("Reader identity not trusted by this wallet").performScrollTo().assertIsDisplayed()
        onNodeWithText("No reader trust policy is configured").performScrollTo().assertIsDisplayed()
        onAllNodesWithText("Trusted reader").assertCountEquals(0)
        onNodeWithText("ISO 18013-7 Annex C HPKE").performScrollTo().assertIsDisplayed()
    }

    /** A trusted reader is named, which is the only state in which the wallet can identify the reader. */
    fun trustedReaderIsNamedOnTheReview() = runComposeUiTest {
        setContent {
            WalletDemoSharingReviewScreen(
                review = annexCReview(WalletDemoReaderTrust.Trusted("CN=Example Reader")),
                title = "Share mobile document?",
                onSubmit = {},
                onCancel = {},
            )
        }

        onNodeWithText("Trusted reader").performScrollTo().assertIsDisplayed()
        onNodeWithText("CN=Example Reader").performScrollTo().assertIsDisplayed()
    }

    /**
     * A request for two documents cannot be answered with one, so Share stays disabled until every
     * requirement has a credential.
     */
    fun shareStaysDisabledUntilEveryRequestedDocumentHasACredential() = runComposeUiTest {
        var submitted: WalletDemoSharingSelection? = null
        val mdl = credentialOption(queryId = "org.iso.18013.5.1.mDL", credentialId = "credential-1")
        val photoId = credentialOption(queryId = "org.iso.23220.photoid.1", credentialId = "credential-2")
        setContent {
            WalletDemoSharingReviewScreen(
                review = annexCReview(
                    readerTrust = WalletDemoReaderTrust.NotAuthenticated,
                    credentialOptions = listOf(mdl, photoId),
                ),
                title = "Share mobile document?",
                onSubmit = { submitted = it },
                onCancel = {},
            )
        }

        onNodeWithTag(WalletUiTestTags.presentationCredentialToggle(photoId.selection.id))
            .performScrollTo()
            .performClick()
        onNodeWithTag(WalletDemoSharingReviewTestTags.ShareButton).performScrollTo().assertIsNotEnabled()
        assertNull(submitted)

        onNodeWithTag(WalletUiTestTags.presentationCredentialToggle(photoId.selection.id))
            .performScrollTo()
            .performClick()
        onNodeWithTag(WalletDemoSharingReviewTestTags.ShareButton).performScrollTo().performClick()
        assertEquals(setOf(mdl.selection, photoId.selection), submitted?.credentials)
    }

    private fun digitalCredentialReview(): WalletDemoSharingReview = WalletDemoSharingReview(
        request = WalletDemoSharingRequest(
            requester = WalletDemoSharingRequester(
                fallbackName = "https://verifier.example",
                verifiedOrigin = "https://verifier.example",
            ),
            responseProtection = WalletDemoSharingResponseProtection.Encrypted(
                mechanism = WalletDemoSharingEncryptionMechanism.DcApiJwt,
            ),
            transactionData = CredentialDisplayNormalizer.transactionDataGroups(
                listOf(
                    WalletDemoTransactionDataItem(
                        type = "org.waltid.transaction-data.payment-authorization",
                        displayName = "Payment Authorization",
                        credentialQueryIds = listOf("pid"),
                        supportedFields = listOf("amount", "currency", "payee"),
                        rawJson = """{"amount":"42.00","currency":"EUR","payee":"ACME Corp"}""",
                        detailsJson = """{"amount":"42.00","currency":"EUR","payee":"ACME Corp"}""",
                    ),
                )
            ),
            technicalDetails = listOf(
                WalletDemoSharingDetail("Protocol", "openid4vp-v1-unsigned"),
                WalletDemoSharingDetail("Response mode", "dc_api.jwt"),
            ),
        ),
        credentialOptions = listOf(credentialOption()),
    )

    private fun annexCReview(
        readerTrust: WalletDemoReaderTrust,
        credentialOptions: List<WalletDemoPresentationCredentialOption> = listOf(
            credentialOption(queryId = "org.iso.18013.5.1.mDL"),
        ),
    ): WalletDemoSharingReview = WalletDemoSharingReview(
        request = WalletDemoSharingRequest(
            requester = WalletDemoSharingRequester(
                fallbackName = "https://verifier.example",
                verifiedOrigin = "https://verifier.example",
            ),
            readerTrust = readerTrust,
            responseProtection = WalletDemoSharingResponseProtection.Encrypted(
                mechanism = WalletDemoSharingEncryptionMechanism.AnnexCHpke,
                keyManagementAlgorithm = "DHKEM(P-256, HKDF-SHA256)",
                contentEncryptionAlgorithm = "AES-128-GCM",
            ),
        ),
        credentialOptions = credentialOptions,
        credentialRequirements = credentialOptions.map { option ->
            WalletDemoPresentationCredentialRequirement(options = listOf(listOf(option.queryId)))
        },
    )

    private fun credentialOption(
        queryId: String = "pid",
        credentialId: String = "credential-1",
    ): WalletDemoPresentationCredentialOption = WalletDemoPresentationCredentialOption(
        queryId = queryId,
        credentialId = credentialId,
        label = "Driving licence",
        issuer = "Test Issuer",
        format = "mso_mdoc",
        credentialDataJson = "{}",
        disclosures = listOf(
            WalletDemoPresentationDisclosure(
                label = "Given name",
                path = "org.iso.18013.5.1/given_name",
                valueJson = "\"Ada\"",
                displayValue = "Ada",
                selectivelyDisclosable = false,
            ),
        ),
    )
}
