package id.walt.walletdemo.compose.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import id.walt.walletdemo.compose.logic.WalletDemoMetadataDisplay
import id.walt.walletdemo.compose.logic.WalletDemoPresentationCredentialSelection
import id.walt.walletdemo.compose.logic.WalletDemoPresentationDisclosureSelection
import id.walt.walletdemo.compose.logic.WalletDemoReaderTrust
import id.walt.walletdemo.compose.logic.WalletDemoSharingRequester
import id.walt.walletdemo.compose.logic.WalletDemoSharingSelection
import id.walt.walletdemo.compose.ui.WalletDemoSharingReviewFixtures.OPTIONAL_DISCLOSURE_PATH
import id.walt.walletdemo.compose.ui.WalletDemoSharingReviewFixtures.REQUIRED_DISCLOSURE_PATH
import id.walt.walletdemo.compose.ui.WalletDemoSharingReviewFixtures.annexCReview
import id.walt.walletdemo.compose.ui.WalletDemoSharingReviewFixtures.credentialOption
import id.walt.walletdemo.compose.ui.WalletDemoSharingReviewFixtures.digitalCredentialReview
import id.walt.walletdemo.compose.ui.WalletDemoSharingReviewFixtures.disclosureSelection
import id.walt.walletdemo.compose.ui.WalletDemoSharingReviewFixtures.optionalDisclosure
import id.walt.walletdemo.compose.ui.WalletDemoSharingReviewFixtures.requiredDisclosure
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

    /**
     * Two wallet credentials can satisfy the same credential query, and they are alternatives rather
     * than an accumulation: a DCQL credential query asks for one match unless it allows several.
     * Choosing the second must therefore deselect the first *and* drop the disclosures approved for it -
     * permission to disclose an attribute from one document is not permission to disclose it from
     * another, and a review that carried the choice over would share what the user never approved.
     */
    fun choosingAnotherCredentialForOneQueryReplacesItAndItsDisclosures() = runComposeUiTest {
        var submitted: WalletDemoSharingSelection? = null
        val first = credentialOption(
            queryId = "org.iso.18013.5.1.mDL",
            credentialId = "credential-1",
            disclosures = listOf(requiredDisclosure(), optionalDisclosure()),
        )
        val second = credentialOption(
            queryId = "org.iso.18013.5.1.mDL",
            credentialId = "credential-2",
            label = "Driving licence (renewed)",
            disclosures = listOf(requiredDisclosure(), optionalDisclosure()),
        )
        setContent {
            WalletDemoSharingReviewScreen(
                review = annexCReview(
                    readerTrust = WalletDemoReaderTrust.NotAuthenticated,
                    credentialOptions = listOf(first, second),
                ),
                title = "Share mobile document?",
                onSubmit = { submitted = it },
                onCancel = {},
            )
        }

        onNodeWithTag(WalletUiTestTags.presentationCredentialToggle(first.selection.id))
            .performScrollTo()
            .assertIsOn()
        onNodeWithTag(WalletUiTestTags.presentationCredentialToggle(second.selection.id))
            .performScrollTo()
            .assertIsOff()

        // Approving the first credential's optional disclosure is what gives the switch something to
        // leak: without it, an implementation that never dropped disclosures would still pass.
        val firstOptionalDisclosure = disclosureSelection(first, OPTIONAL_DISCLOSURE_PATH)
        onNodeWithTag(WalletUiTestTags.presentationDisclosureToggle(firstOptionalDisclosure.id))
            .performScrollTo()
            .performClick()
        onNodeWithTag(WalletUiTestTags.presentationDisclosureToggle(firstOptionalDisclosure.id))
            .performScrollTo()
            .assertIsOn()

        onNodeWithTag(WalletUiTestTags.presentationCredentialToggle(second.selection.id))
            .performScrollTo()
            .performClick()
        onNodeWithTag(WalletUiTestTags.presentationCredentialToggle(first.selection.id))
            .performScrollTo()
            .assertIsOff()

        onNodeWithTag(WalletDemoSharingReviewTestTags.ShareButton).performScrollTo().performClick()
        assertEquals(setOf(second.selection), submitted?.credentials)
        assertEquals(emptySet<WalletDemoPresentationDisclosureSelection>(), submitted?.disclosures)
    }

    /**
     * A disclosure the credential can withhold is the user's decision; one the request requires is not.
     * The optional one therefore gets a toggle that starts off and travels only once it is turned on,
     * and the required one gets no toggle at all - offering a control the wallet cannot honour would
     * misdescribe what Share does. Deselecting the credential also disables the toggle, because there is
     * no longer a document to disclose from.
     */
    fun optionalDisclosuresStartOffAndTravelOnlyWhenTurnedOn() = runComposeUiTest {
        var submitted: WalletDemoSharingSelection? = null
        val option = credentialOption(disclosures = listOf(requiredDisclosure(), optionalDisclosure()))
        setContent {
            WalletDemoSharingReviewScreen(
                review = digitalCredentialReview(credentialOptions = listOf(option)),
                title = "Share digital credential?",
                onSubmit = { submitted = it },
                onCancel = {},
            )
        }

        val required = disclosureSelection(option, REQUIRED_DISCLOSURE_PATH)
        val optional = disclosureSelection(option, OPTIONAL_DISCLOSURE_PATH)
        onNodeWithTag(WalletUiTestTags.presentationDisclosure(required.id)).performScrollTo().assertIsDisplayed()
        onAllNodesWithTag(WalletUiTestTags.presentationDisclosureToggle(required.id)).assertCountEquals(0)
        onNodeWithText("Required by request").performScrollTo().assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.presentationDisclosureToggle(optional.id)).performScrollTo().assertIsOff()
        onNodeWithText("Optional disclosure").performScrollTo().assertIsDisplayed()

        // Submitted untouched: a required disclosure is not carried as a selection, so an empty
        // disclosure set here is what "the user approved nothing optional" has to look like.
        onNodeWithTag(WalletDemoSharingReviewTestTags.ShareButton).performScrollTo().performClick()
        assertEquals(emptySet<WalletDemoPresentationDisclosureSelection>(), submitted?.disclosures)

        onNodeWithTag(WalletUiTestTags.presentationDisclosureToggle(optional.id)).performScrollTo().performClick()
        onNodeWithTag(WalletDemoSharingReviewTestTags.ShareButton).performScrollTo().performClick()
        assertEquals(setOf(optional), submitted?.disclosures)

        onNodeWithTag(WalletUiTestTags.presentationCredentialToggle(option.selection.id))
            .performScrollTo()
            .performClick()
        onNodeWithTag(WalletUiTestTags.presentationDisclosureToggle(optional.id))
            .performScrollTo()
            .assertIsNotEnabled()
    }

}
