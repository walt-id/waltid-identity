package id.walt.walletdemo.compose.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assert
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import id.walt.walletdemo.compose.logic.*
import id.walt.walletdemo.compose.ui.components.OfferedCredentialRow
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
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * The platform-invoked sharing review, exercised through the same screen a Digital Credentials
 * provider host shows.
 *
 * These cover what an in-app OpenID4VP review never exercises: a request with no protocol rejection
 * channel, and the concepts only a platform transport has - verified origin, reader authentication,
 * session encryption.
 */
@OptIn(ExperimentalTestApi::class)
class WalletDemoSharingReviewTestScenarios {

    fun resizingAnOfferPreservesSelectionAndKeepsItsTitleReadable() = runComposeUiTest {
        val credential = WalletVisualFixtures.offer.offeredCredentials.last()
        val title = credential.resolvedCardTitle()
        val width = mutableStateOf(393)
        val fontScale = mutableStateOf(1f)
        val copies = mutableStateOf(2)
        val selectionTag = "issuance-select-${credential.configurationId}"
        setContent {
            WalletDemoTheme {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale.value)) {
                    Box(Modifier.width(width.value.dp)) {
                        OfferedCredentialRow(credential, "Example City", "https://issuer.example",
                            copies.value, 3, true, { copies.value = it })
                    }
                }
            }
        }
        onAllNodesWithTag(selectionTag).assertCountEquals(1)
        onNodeWithTag(selectionTag).assertIsOn()
        runOnIdle { width.value = 320; fontScale.value = 1.5f }
        onAllNodesWithTag(selectionTag).assertCountEquals(1)
        onNodeWithText(title).assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        onNodeWithText(title).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        repeat(layout.lineCount - 1) { line ->
            val end = layout.getLineEnd(line)
            assertTrue(title[end - 1].isWhitespace() || title.getOrNull(end)?.isWhitespace() == true,
                "Enlarged text must not split a word while the selection control takes its space")
        }
        assertEquals(2, copies.value)
        onNodeWithTag(selectionTag).performClick().assertIsOff()
        runOnIdle { width.value = 393; fontScale.value = 1f }
        onAllNodesWithTag(selectionTag).assertCountEquals(1)
        onNodeWithTag(selectionTag).assertIsOff().performClick().assertIsOn()
        assertEquals(1, copies.value)
    }

    fun unsignedConfirmationIsInvalidatedByNewConsentAndDisabledState() = runComposeUiTest {
        val consent = WalletDemoPaymentConsent("first", "en", null, null, "Pay", null, true, emptyList())
        val review = mutableStateOf<WalletDemoPaymentReview>(WalletDemoPaymentReview.Ready(consent))
        val enabled = mutableStateOf(true)
        var submissions = 0
        setContent {
            id.walt.walletdemo.compose.ui.components.SharingActionsRow(enabled.value, true,
                onSubmit = { submissions++ }, onCancel = {}, onReject = null, paymentReview = review.value)
        }
        onNodeWithText("Pay").performClick()
        onNodeWithTag("payment-unsigned-confirm").assertIsDisplayed()
        runOnIdle { review.value = WalletDemoPaymentReview.Ready(consent.copy(revision = "second")) }
        onNodeWithTag("payment-unsigned-confirm").assertDoesNotExist()
        onNodeWithText("Pay").performClick()
        runOnIdle { enabled.value = false }
        onNodeWithTag("payment-unsigned-confirm").assertDoesNotExist()
        onNodeWithText("Pay").assertIsNotEnabled()
        assertEquals(0, submissions)
        runOnIdle { enabled.value = true }
        onNodeWithText("Pay").performClick()
        onNodeWithTag("payment-unsigned-confirm").performClick()
        assertEquals(1, submissions)
    }

    fun changingHostPreservesDisclosureChoicesAndConsentRevision() = runComposeUiTest {
        val option = credentialOption(disclosures = listOf(requiredDisclosure(), optionalDisclosure()))
        val optional = disclosureSelection(option, OPTIONAL_DISCLOSURE_PATH)
        val presentation = mutableStateOf(WalletReviewPresentation.FullScreen)
        var prepared = 0
        var submitted: WalletDemoSharingSelection? = null
        val visible = mutableStateOf(true)
        val review = digitalCredentialReview(listOf(option))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val owner = WalletDemoSharingReviewController(review, scope) {
            prepared++
            WalletDemoPaymentConsent("revision-$prepared", "en", "Payment", null, "Approve", "Cancel", false, emptyList())
        }
        setContent {
            if (visible.value) WalletDemoSharingReviewScreen(review = review, controller = owner, title = "Review request",
                compact = false, presentation = presentation.value, onSubmit = { submitted = it }, onCancel = {},
                onBackAtRoot = {})
        }
        onNodeWithTag(WalletUiTestTags.presentationClaimsToggle(option.selection.id)).performScrollTo().performClick()
        onNodeWithTag(WalletUiTestTags.presentationDisclosureToggle(optional.id)).performScrollTo().performClick()
        onNodeWithTag(WalletUiTestTags.PresentationClaimsClose).performClick()
        waitForIdle()
        val revision = prepared
        assertEquals(2, revision) // Initial selection and the explicit optional disclosure.
        runOnIdle { visible.value = false }
        waitForIdle()
        runOnIdle { visible.value = true; presentation.value = WalletReviewPresentation.Sheet }
        onNodeWithTag(WalletUiTestTags.presentationClaimsToggle(option.selection.id)).performScrollTo().performClick()
        onNodeWithTag(WalletUiTestTags.presentationDisclosureToggle(optional.id)).performScrollTo().assertIsOn()
        onNodeWithTag(WalletUiTestTags.PresentationClaimsClose).performClick()
        onNodeWithText("Approve").performClick()
        assertEquals(revision, prepared)
        assertEquals(setOf(optional), submitted?.disclosures)
        assertEquals("revision-$revision", submitted?.paymentConsentRevision)
        owner.close(); scope.cancel()
    }

    fun inspectingAllCredentialInformationDoesNotChangeDisclosureConsent() = runComposeUiTest {
        var submitted: WalletDemoSharingSelection? = null
        val option = credentialOption(disclosures = listOf(requiredDisclosure(), optionalDisclosure())).copy(
            credentialDataJson = """{"org.iso.18013.5.1":{"given_name":"Ada","private_note":"For my own reference"}}""",
        )
        val optional = disclosureSelection(option, OPTIONAL_DISCLOSURE_PATH)
        setContent {
            WalletDemoSharingReviewScreen(compact = false, review = digitalCredentialReview(listOf(option)),
                title = "Share digital credential?", onSubmit = { submitted = it }, onCancel = {})
        }
        onNodeWithTag(WalletUiTestTags.presentationClaimsToggle(option.selection.id)).performScrollTo().performClick()
        val inInformation = hasAnyAncestor(hasTestTag(WalletUiTestTags.PresentationClaimsDialog))
        onAllNodes(hasTestTag("wallet.screen.header") and inInformation).assertCountEquals(1)
        onAllNodes(hasText("Driving licence") and inInformation).assertCountEquals(1)
        onNodeWithTag(WalletUiTestTags.presentationDisclosureToggle(optional.id)).performScrollTo().performClick()
        onNodeWithTag("review-all-credential-information").performScrollTo().performClick()
        onNode(hasText("Includes information outside this request.") and hasAnyAncestor(hasTestTag("review-all-information-details"))).assertIsDisplayed()
        onNodeWithText("For my own reference").performScrollTo().assertIsDisplayed()
        val group = option.toCredentialDetails().groups.first { it.id != "requested" && it.id != "technical" }
        onNodeWithTag(WalletUiTestTags.claimGroup(group.title)).performScrollTo().performClick()
        onNodeWithTag("credential-technical-details").performScrollTo().performClick()
        onAllNodes(hasTestTag("wallet.screen.header") and inInformation).assertCountEquals(1)
        onNodeWithTag("wallet-detail-back").performClick()
        onNodeWithTag(WalletUiTestTags.claimGroup(group.title)).performScrollTo()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))
        onNodeWithText("For my own reference").assertDoesNotExist()
        onNodeWithTag("wallet-detail-back").performClick()
        onNodeWithTag(WalletUiTestTags.presentationDisclosureToggle(optional.id)).performScrollTo().assertIsOn()
        onNodeWithText("For my own reference").assertDoesNotExist()
        onNodeWithTag(WalletUiTestTags.PresentationClaimsClose).performClick()
        onNodeWithTag(WalletDemoSharingReviewTestTags.ShareButton).performClick()
        assertEquals(setOf(option.selection), submitted?.credentials)
        assertEquals(setOf(optional), submitted?.disclosures)
    }

    fun closingTechnicalInformationReturnsToReviewWithoutSubmitting() = runComposeUiTest {
        var submissions = 0
        val option = credentialOption(disclosures = listOf(requiredDisclosure(), optionalDisclosure())).copy(
            credentialDataJson = """{"org.iso.18013.5.1":{"given_name":"Ada","private_note":"Private"}}""",
        )
        val optional = disclosureSelection(option, OPTIONAL_DISCLOSURE_PATH)
        setContent {
            WalletDemoSharingReviewScreen(compact = false, review = digitalCredentialReview(listOf(option)),
                title = "Share digital credential?", onSubmit = { submissions++ }, onCancel = {})
        }
        onNodeWithTag(WalletUiTestTags.presentationClaimsToggle(option.selection.id)).performScrollTo().performClick()
        onNodeWithTag(WalletUiTestTags.presentationDisclosureToggle(optional.id)).performScrollTo().performClick()
        onNodeWithTag("review-all-credential-information").performScrollTo().performClick()
        onNodeWithTag("credential-technical-details").performScrollTo().performClick()
        onNodeWithTag(WalletUiTestTags.PresentationClaimsClose).performClick()
        onAllNodesWithTag(WalletUiTestTags.PresentationClaimsDialog).assertCountEquals(0)
        onAllNodesWithTag("wallet.screen.header").assertCountEquals(1)
        assertEquals(0, submissions)
        onNodeWithTag(WalletUiTestTags.presentationClaimsToggle(option.selection.id)).performScrollTo().performClick()
        onNodeWithTag(WalletUiTestTags.presentationDisclosureToggle(optional.id)).performScrollTo().assertIsOn()
        onNodeWithTag("review-all-credential-information").performScrollTo().assertIsDisplayed()
    }

    fun paymentReviewUsesResolvedLabelsActionsAndAllFourPlacements() = runComposeUiTest {
        val consent = WalletDemoPaymentConsent("revision", "de", "Zahlung prüfen", null, "Zahlen", "Ablehnen", true,
            listOf(
                WalletDemoPaymentField("Betrag", null, "11.56 EUR", WalletDemoPaymentFieldPlacement.Prominent),
                WalletDemoPaymentField("Empfänger", null, "Super Store", WalletDemoPaymentFieldPlacement.Main),
                WalletDemoPaymentField("Transaktions-ID", null, "txn-1", WalletDemoPaymentFieldPlacement.Details),
                WalletDemoPaymentField("Omitted", null, "bound-but-hidden", WalletDemoPaymentFieldPlacement.Omitted),
            ))
        var submitted: WalletDemoSharingSelection? = null
        setContent {
            WalletDemoSharingReviewScreen(review = digitalCredentialReview(), title = "Payment", compact = false,
                onSubmit = { submitted = it }, onCancel = {}, preparePaymentConsent = { consent })
        }
        onNodeWithText("Zahlung prüfen").performScrollTo().assertIsDisplayed()
        onNodeWithText("Betrag").performScrollTo().assertIsDisplayed()
        onNodeWithText("11.56 EUR").performScrollTo().assertIsDisplayed()
        onNodeWithText("Empfänger").performScrollTo().assertIsDisplayed()
        onNodeWithTag("payment-unsigned-warning").performScrollTo().assertIsDisplayed()
        onNodeWithTag("payment-security-hint").assertDoesNotExist()
        onNodeWithText("bound-but-hidden").assertDoesNotExist()
        onNodeWithText("txn-1").assertDoesNotExist()
        onNodeWithTag("payment-details-toggle").performScrollTo().performClick()
        onNodeWithText("txn-1").performScrollTo().assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.presentationClaimsToggle(WalletDemoPresentationCredentialSelection("pid", "credential-1").id))
            .performScrollTo().assertIsDisplayed() // Ordinary requested credentials remain reviewable.
        onNodeWithText("Ablehnen").assertIsDisplayed()
        onNodeWithText("Zahlen").performClick()
        assertEquals(null, submitted)
        onNodeWithTag("payment-unsigned-back").performClick()
        assertEquals(null, submitted)
        onNodeWithText("Zahlen").performClick()
        onNodeWithTag("payment-unsigned-confirm").performClick()
        assertEquals("revision", submitted?.paymentConsentRevision)
    }

    fun unavailablePaymentInstructionsBlockSubmissionWithoutGenericFallback() = runComposeUiTest {
        val review = digitalCredentialReview().let { it.copy(request = it.request.copy(transactionData = it.request.transactionData.map { group ->
            group.copy(transactionType = "urn:eudi:sca:payment:1")
        })) }
        var submitted = false
        setContent {
            WalletDemoSharingReviewScreen(review = review, title = "Payment", compact = false,
                onSubmit = { submitted = true }, onCancel = {},
                preparePaymentConsent = { error("Required payment instructions are unavailable in your preferred languages.") })
        }
        onNodeWithTag("payment-consent-blocked").performScrollTo().assertIsDisplayed()
        onNodeWithText("42.00").assertDoesNotExist()
        onNodeWithText("Share").assertIsNotEnabled()
        assertEquals(false, submitted)
    }

    /**
     * The `dc_api.jwt` case. The signature covers transaction_data, so every authorized value has to be
     * on the review, and the encryption the response mode implies has to be stated.
     */
    fun digitalCredentialReviewShowsOriginTransactionDataAndEncryption() = runComposeUiTest {
        setContent {
            WalletDemoSharingReviewScreen(
                compact = false,
                review = digitalCredentialReview(),
                title = "Share digital credential?",
                onSubmit = {},
                onCancel = {},
            )
        }

        onNodeWithTag(WalletDemoSharingReviewTestTags.Review).assertIsDisplayed()
        onNodeWithTag(WalletDemoSharingReviewTestTags.RequesterSection).performScrollTo().assertIsDisplayed()
        // An unsigned Digital Credentials request has no verifier metadata, so the authenticated origin
        // is the requester identity: shown once, and captioned as verified, because an uncaptioned origin
        // reads as one more self-asserted requester claim.
        onNodeWithText("https://verifier.example").assertIsDisplayed()
        onAllNodesWithText("https://verifier.example").assertCountEquals(1)
        onNodeWithText("Verified website").assertIsDisplayed()
        onAllNodesWithText("Verified website").assertCountEquals(1)
        onNodeWithText("Payment Authorization").performScrollTo().assertIsDisplayed()
        onNodeWithText("42.00").performScrollTo().assertIsDisplayed()
        onNodeWithText("EUR").performScrollTo().assertIsDisplayed()
        onNodeWithText("ACME Corp").assertIsDisplayed()
        onNodeWithContentDescription("Show Verifier details").performScrollTo().performClick()
        onNodeWithTag(WalletDemoSharingReviewTestTags.ResponseProtectionSection, useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
        onNodeWithText("OpenID4VP dc_api.jwt").performScrollTo().assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.presentationClaimsToggle(WalletDemoPresentationCredentialSelection("pid", "credential-1").id)).performScrollTo().performClick()
        onNodeWithText("Given name").performScrollTo().assertIsDisplayed()
        onNodeWithText("Ada").performScrollTo().assertIsDisplayed()
    }

    /**
     * Self-asserted verifier metadata heads the section, and the authenticated origin stays visible beside
     * it under its own label. Showing only the name a request asked to be called would hide the one
     * requester fact that was actually verified.
     */
    fun verifiedOriginStaysVisibleBesideSelfAssertedVerifierMetadata() = runComposeUiTest {
        setContent {
            WalletDemoSharingReviewScreen(
                compact = false,
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

        onNodeWithText("Example Verifier").assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.PresentationRequesterDetailsToggle).performClick()
        onNodeWithText("Verified website").assertIsDisplayed()
        onNodeWithText("https://verifier.example").assertIsDisplayed()
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
                compact = false,
                review = digitalCredentialReview(),
                title = "Share digital credential?",
                onSubmit = { submitted = it },
                onCancel = { cancelled = true },
            )
        }

        onNodeWithTag(WalletDemoSharingReviewTestTags.CancelButton).assertIsDisplayed()
        onAllNodesWithTag(WalletUiTestTags.PresentationRejectButton).assertCountEquals(0)
        onAllNodesWithText("Reject").assertCountEquals(0)

        onNodeWithTag(WalletDemoSharingReviewTestTags.ShareButton).performClick()
        assertEquals(
            setOf(WalletDemoPresentationCredentialSelection("pid", "credential-1")),
            submitted?.credentials,
        )
        assertEquals(false, cancelled)

        onNodeWithTag(WalletDemoSharingReviewTestTags.CancelButton).performClick()
        assertEquals(true, cancelled)
    }

    /** A submission in flight must not be able to start a second one for the same request. */
    fun disabledReviewCannotBeSubmittedTwice() = runComposeUiTest {
        setContent {
            WalletDemoSharingReviewScreen(
                compact = false,
                review = digitalCredentialReview(),
                title = "Share digital credential?",
                onSubmit = {},
                onCancel = {},
                enabled = false,
            )
        }

        onNodeWithTag(WalletDemoSharingReviewTestTags.ShareButton).assertIsNotEnabled()
        onNodeWithTag(WalletDemoSharingReviewTestTags.CancelButton).assertIsNotEnabled()
    }

    /**
     * A protocol without reader authentication gets no reader section at all, rather than one reporting
     * an absent reader: the OpenID4VP Digital Credentials API has no reader to be trusted or not.
     */
    fun reviewWithoutReaderAuthenticationShowsNoReaderSection() = runComposeUiTest {
        setContent {
            WalletDemoSharingReviewScreen(
                compact = false,
                review = digitalCredentialReview(),
                title = "Share digital credential?",
                onSubmit = {},
                onCancel = {},
            )
        }

        onAllNodesWithTag(WalletDemoSharingReviewTestTags.ReaderTrustSection).assertCountEquals(0)
    }

    /**
     * Annex C does have reader authentication, and an unrecognised reader is described as a trust decision
     * rather than a verification failure: a request whose reader signature failed never reaches a review.
     */
    fun untrustedReaderIsDescribedAsATrustDecisionNotASignatureFailure() = runComposeUiTest {
        setContent {
            WalletDemoSharingReviewScreen(
                compact = false,
                review = annexCReview(WalletDemoReaderTrust.Untrusted("No reader trust policy is configured")),
                title = "Share mobile document?",
                onSubmit = {},
                onCancel = {},
            )
        }

        onNodeWithTag(WalletUiTestTags.PresentationRequesterDetailsToggle).performClick()
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
                compact = false,
                review = annexCReview(WalletDemoReaderTrust.Trusted("CN=Example Reader")),
                title = "Share mobile document?",
                onSubmit = {},
                onCancel = {},
            )
        }

        onNodeWithTag(WalletUiTestTags.PresentationRequesterDetailsToggle).performClick()
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
                compact = false,
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
        onNodeWithTag(WalletDemoSharingReviewTestTags.ShareButton).assertIsNotEnabled()
        assertNull(submitted)

        onNodeWithTag(WalletUiTestTags.presentationCredentialToggle(photoId.selection.id))
            .performScrollTo()
            .performClick()
        onNodeWithTag(WalletDemoSharingReviewTestTags.ShareButton).performClick()
        assertEquals(setOf(mdl.selection, photoId.selection), submitted?.credentials)
    }

    /**
     * Two wallet credentials satisfying the same DCQL credential query are alternatives, not an
     * accumulation. Choosing the second deselects the first *and* drops the disclosures approved for it:
     * permission to disclose an attribute from one document is not permission to disclose it from another.
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
                compact = true,
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

        onNodeWithTag(WalletUiTestTags.presentationClaimsToggle(first.selection.id)).performScrollTo().performClick()
        // Approving the first credential's optional disclosure gives the switch something to leak;
        // without it, an implementation that never dropped disclosures would still pass.
        val firstOptionalDisclosure = disclosureSelection(first, OPTIONAL_DISCLOSURE_PATH)
        onNodeWithTag(WalletUiTestTags.presentationDisclosureToggle(firstOptionalDisclosure.id))
            .performScrollTo()
            .performClick()
        onNodeWithTag(WalletUiTestTags.presentationDisclosureToggle(firstOptionalDisclosure.id))
            .performScrollTo()
            .assertIsOn()
        onNodeWithTag(WalletUiTestTags.PresentationClaimsClose).performClick()

        onNodeWithTag(WalletUiTestTags.presentationCredentialToggle(second.selection.id))
            .performScrollTo()
            .performClick()
        onNodeWithTag(WalletUiTestTags.presentationCredentialToggle(first.selection.id))
            .performScrollTo()
            .assertIsOff()

        onNodeWithTag(WalletDemoSharingReviewTestTags.ShareButton).performClick()
        assertEquals(setOf(second.selection), submitted?.credentials)
        assertEquals(emptySet<WalletDemoPresentationDisclosureSelection>(), submitted?.disclosures)
    }

    /**
     * A disclosure the credential can withhold is the user's decision; one the request requires is not. The
     * optional one gets a toggle that starts off, and the required one gets none, because offering a
     * control the wallet cannot honour would misdescribe what Share does.
     */
    fun optionalDisclosuresStartOffAndTravelOnlyWhenTurnedOn() = runComposeUiTest {
        var submitted: WalletDemoSharingSelection? = null
        val option = credentialOption(disclosures = listOf(requiredDisclosure(), optionalDisclosure()))
        setContent {
            WalletDemoSharingReviewScreen(
                compact = false,
                review = digitalCredentialReview(credentialOptions = listOf(option)),
                title = "Share digital credential?",
                onSubmit = { submitted = it },
                onCancel = {},
            )
        }

        val required = disclosureSelection(option, REQUIRED_DISCLOSURE_PATH)
        val optional = disclosureSelection(option, OPTIONAL_DISCLOSURE_PATH)
        onNodeWithTag(WalletUiTestTags.presentationClaimsToggle(option.selection.id)).performScrollTo().performClick()
        onNodeWithTag(WalletUiTestTags.presentationDisclosure(required.id)).performScrollTo().assertIsDisplayed()
        onAllNodesWithTag(WalletUiTestTags.presentationDisclosureToggle(required.id)).assertCountEquals(0)
        onNodeWithText("Required by request").performScrollTo().assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.presentationDisclosureToggle(optional.id)).performScrollTo().assertIsOff()
        onNodeWithText("Optional disclosure").performScrollTo().assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.PresentationClaimsClose).performClick()

        // A required disclosure is not carried as a selection, so an empty disclosure set is what
        // "the user approved nothing optional" looks like.
        onNodeWithTag(WalletDemoSharingReviewTestTags.ShareButton).performClick()
        assertEquals(emptySet<WalletDemoPresentationDisclosureSelection>(), submitted?.disclosures)

        onNodeWithTag(WalletUiTestTags.presentationClaimsToggle(option.selection.id)).performScrollTo().performClick()
        onNodeWithTag(WalletUiTestTags.presentationDisclosureToggle(optional.id)).performScrollTo().performClick()
        onNodeWithTag(WalletUiTestTags.PresentationClaimsClose).performClick()
        onNodeWithTag(WalletDemoSharingReviewTestTags.ShareButton).performClick()
        assertEquals(setOf(optional), submitted?.disclosures)

        onNodeWithTag(WalletUiTestTags.presentationCredentialToggle(option.selection.id))
            .performScrollTo()
            .performClick()
        onNodeWithTag(WalletUiTestTags.presentationClaimsToggle(option.selection.id)).performScrollTo().performClick()
        onNodeWithTag(WalletUiTestTags.presentationDisclosureToggle(optional.id))
            .performScrollTo()
            .assertIsNotEnabled()
    }

}
