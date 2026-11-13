package id.walt.walletdemo.compose.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import id.walt.walletdemo.compose.logic.WalletDemoCredentialStatus
import id.walt.walletdemo.compose.logic.WalletDemoCredentialStatuses
import id.walt.walletdemo.compose.logic.WalletDemoPaymentConsent
import id.walt.walletdemo.compose.logic.WalletDemoPaymentField
import id.walt.walletdemo.compose.logic.WalletDemoPaymentFieldPlacement
import id.walt.walletdemo.compose.logic.WalletDemoPaymentReview
import id.walt.walletdemo.compose.logic.WalletDemoPresentationCredentialRequirement
import id.walt.walletdemo.compose.logic.WalletDemoSharingRequest
import id.walt.walletdemo.compose.logic.WalletDemoSharingReview
import id.walt.walletdemo.compose.logic.WalletDemoSharingReviewController
import id.walt.walletdemo.compose.logic.WalletDemoStatusReadState
import id.walt.walletdemo.compose.logic.resolvedCardTitle
import id.walt.walletdemo.compose.ui.components.OfferedCredentialRow
import id.walt.walletdemo.compose.ui.components.PaymentConsentSection
import id.walt.walletdemo.compose.ui.components.SharingActionsRow
import id.walt.walletdemo.compose.ui.components.rememberSharingSubmission
import id.walt.walletdemo.compose.ui.components.ReviewChangedNotice
import id.walt.walletdemo.compose.ui.components.ReviewScaffold
import id.walt.walletdemo.compose.ui.components.CredentialStatusUiContext
import id.walt.walletdemo.compose.ui.components.CredentialStatusSection
import id.walt.walletdemo.compose.ui.components.LocalCredentialStatusUi
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

    fun statusConfirmationUsesOnlyDistinctSelectedCredentialsAndCapturedRevisions() = runComposeUiTest {
        val first = credentialOption(queryId = "one", credentialId = "selected", label = "Selected licence")
        val repeated = first.copy(queryId = "two")
        val other = first.copy(credentialId = "unselected", label = "Unselected card")
        val review = WalletDemoSharingReview(WalletDemoSharingRequest(null), listOf(first, other, repeated),
            listOf(WalletDemoPresentationCredentialRequirement(listOf(listOf("one", "two")))))
        val adverse = WalletVisualFixtures.credentialStatus("revoked_current")
        val snapshot = WalletDemoCredentialStatuses(mapOf("selected" to adverse, "unselected" to adverse))
        val submissions = mutableListOf<WalletDemoSharingSelection>()
        setContent {
            TestSharingReviewScreen(review, "Share documents", { submissions += it }, {}, credentialStatuses = snapshot)
        }
        onNodeWithTag(WalletUiTestTags.PresentationSubmitButton).assertIsDisplayed().performClick()
        onNodeWithText("Share with status issues?").assertIsDisplayed()
        onNodeWithText("Unselected card:", substring = true).assertDoesNotExist()
        onNodeWithText("2 selected credentials", substring = true).assertDoesNotExist()
        onNodeWithTag("sharing-credential-status-advisory").assertDoesNotExist()
        val confirm = onNodeWithTag("credential-status-confirm").fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        runOnIdle { confirm(); confirm() }
        assertEquals(1, submissions.size)
        assertEquals(setOf(first.selection, repeated.selection), submissions.single().credentials)
        assertEquals(mapOf("selected" to adverse.advisoryRevision), submissions.single().credentialStatusRevisions)
    }

    fun statusConfirmationInvalidatesReviewedChangesButNotUnselectedOrTimestampChanges() = runComposeUiTest {
        val first = credentialOption(disclosures = listOf(requiredDisclosure(), optionalDisclosure()))
        val other = first.copy(credentialId = "other", label = "Alternative licence")
        val review = WalletDemoSharingReview(WalletDemoSharingRequest(null), listOf(first, other))
        val adverse = WalletVisualFixtures.credentialStatus("revoked_current")
        val statuses = mutableStateOf(WalletDemoCredentialStatuses(mapOf(first.credentialId to adverse, "other" to adverse)))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val owner = WalletDemoSharingReviewController(review, scope)
        var submissions = 0
        try {
            setContent {
                TestSharingReviewScreen(review, "Share documents", { submissions++ }, {},
                    controller = owner, credentialStatuses = statuses.value)
            }
            onNodeWithTag(WalletUiTestTags.PresentationSubmitButton).performClick()
            runOnIdle { statuses.value = statuses.value.copy(credentials = statuses.value.credentials +
                ("other" to adverse.copy(advisoryRevision = "unselected-change"))) }
            onNodeWithTag("credential-status-confirm").assertIsDisplayed()
            runOnIdle { statuses.value = statuses.value.copy(credentials = statuses.value.credentials +
                (first.credentialId to adverse.copy(lastCheckedAt = "2026-11-12T19:30:00Z"))) }
            onNodeWithTag("credential-status-confirm").assertIsDisplayed()
            runOnIdle { statuses.value = statuses.value.copy(credentials = statuses.value.credentials +
                (first.credentialId to adverse.copy(advisoryRevision = "selected-change"))) }
            onNodeWithTag("credential-status-confirm").assertDoesNotExist()
            onNodeWithTag(WalletUiTestTags.PresentationSubmitButton).performClick()
            val staleClick = onNodeWithTag("credential-status-confirm").fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
            // Exercise a host change before recomposition, as well as the rendered invalidation.
            runOnIdle { owner.toggleDisclosure(disclosureSelection(first, OPTIONAL_DISCLOSURE_PATH)); staleClick() }
            onNodeWithTag("credential-status-confirm").assertDoesNotExist()
            assertEquals(0, submissions)
            onNodeWithTag(WalletUiTestTags.PresentationSubmitButton).performClick()
            onNodeWithTag("credential-status-back").performClick()
            assertEquals(0, submissions)
        } finally { owner.close(); scope.cancel() }
    }

    fun unavailableStatusCannotInventAcknowledgementAndLegacyFallbackIsExplicit() = runComposeUiTest {
        val option = credentialOption()
        val review = WalletDemoSharingReview(WalletDemoSharingRequest(null), listOf(option))
        val snapshot = mutableStateOf(WalletDemoCredentialStatuses())
        var submitted: WalletDemoSharingSelection? = null
        setContent {
            TestSharingReviewScreen(review, "Share documents", { submitted = it }, {}, credentialStatuses = snapshot.value)
        }
        onNodeWithText("Status unavailable").performScrollToContent(this).assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.PresentationSubmitButton).assertIsNotEnabled()
        runOnIdle { snapshot.value = WalletDemoCredentialStatuses(mapOf(option.credentialId to
            WalletVisualFixtures.credentialStatus("clear").copy(storeIndex = null))) }
        onNodeWithTag(WalletUiTestTags.PresentationSubmitButton).assertIsNotEnabled()
        runOnIdle { snapshot.value = WalletDemoCredentialStatuses(mapOf(option.credentialId to
            WalletVisualFixtures.credentialStatus("revoked_current")), readState = WalletDemoStatusReadState.Unavailable) }
        onNodeWithText("Last known: revoked").performScrollToContent(this).assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.PresentationSubmitButton).assertIsNotEnabled()
        runOnIdle { snapshot.value = WalletDemoCredentialStatuses(mapOf(option.credentialId to
            WalletDemoCredentialStatus.ServerUnsupported), legacySubmission = true) }
        onNodeWithTag(WalletUiTestTags.PresentationSubmitButton).performClick()
        assertEquals(emptyMap(), submitted?.credentialStatusRevisions)
        onNodeWithTag("credential-status-confirm").assertDoesNotExist()
    }

    fun unsignedPaymentAndAdverseStatusUseOneDialogAndPreservePaymentActions() = runComposeUiTest {
        val option = credentialOption()
        val review = WalletDemoSharingReview(WalletDemoSharingRequest(null), listOf(option))
        val consent = WalletDemoPaymentConsent("payment-revision", "en", null, null, "Authorize payment", "Decline payment", true, emptyList())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val owner = WalletDemoSharingReviewController(review, scope) { consent }
        val snapshot = WalletDemoCredentialStatuses(mapOf(option.credentialId to WalletVisualFixtures.credentialStatus("revoked_current")))
        var submitted: WalletDemoSharingSelection? = null
        var refused = 0
        try {
            setContent {
                TestSharingReviewScreen(review, "Payment", { submitted = it }, { refused++ }, onReject = { refused++ },
                    controller = owner, credentialStatuses = snapshot)
            }
            onNodeWithTag(WalletUiTestTags.PresentationSubmitButton).performClick()
            onNodeWithText("Unsigned payment request").assertIsDisplayed()
            onNodeWithText("This request has no signature", substring = true).assertIsDisplayed()
            onNodeWithText("revoked. The recipient", substring = true).assertIsDisplayed()
            onNodeWithTag("credential-status-confirm").assertDoesNotExist()
            onNodeWithTag("payment-unsigned-back").performClick()
            assertEquals(0, refused)
            assertNull(submitted)
            onNodeWithTag(WalletUiTestTags.PresentationSubmitButton).performClick()
            onNodeWithTag("payment-unsigned-confirm").assert(hasText("Authorize payment")).performClick()
            assertEquals("payment-revision", submitted?.paymentConsentRevision)
            assertEquals(0, refused)
        } finally { owner.close(); scope.cancel() }
    }

    fun choosingFromTheWholeRowUpdatesInformationAndSubmission() = runComposeUiTest {
        val options = WalletVisualFixtures.informationCredentials.take(2)
        val review = WalletDemoSharingReview(WalletDemoSharingRequest(null), options)
        val city = options[1]
        var submitted: WalletDemoSharingSelection? = null
        setContent {
            TestSharingReviewScreen(review = review, title = "Share credentials", compact = false,
                onSubmit = { submitted = it }, onCancel = {})
        }
        onNodeWithTag(WalletUiTestTags.presentationCredentialToggle(options[0].selection.id)).performScrollToContent(this).assertIsSelected()
        onNodeWithText("Credential information").assertDoesNotExist()
        onNodeWithTag(WalletUiTestTags.presentationCredentialToggle(city.selection.id)).performScrollToContent(this).assertIsNotSelected().performClick()
        onNodeWithText("Lovelace").performScrollToContent(this).assertIsDisplayed()
        onNodeWithText("Always included by this credential").performScrollToContent(this).assertIsDisplayed()
        onNodeWithText("Ada").performScrollToContent(this).assertIsDisplayed()
        onNodeWithText("Vienna").assertDoesNotExist()
        onNodeWithText("Details").assertDoesNotExist()
        onAllNodesWithTag("wallet.screen.header").assertCountEquals(1)
        onNodeWithTag(WalletUiTestTags.presentationCredentialToggle(city.selection.id)).performScrollToContent(this).assertIsSelected()
        onNodeWithTag(WalletUiTestTags.PresentationSubmitButton).performClick()
        assertEquals(setOf(city.selection), submitted?.credentials)
        assertTrue(submitted?.disclosures.orEmpty().isEmpty())
    }

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
                            copies.value, 3, true, true, { copies.value = it })
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

    fun compactStatusRefreshRequiresAnExplicitActionAndRespectsHostCapability() = runComposeUiTest {
        val id = "identity"
        val snapshot = mutableStateOf(WalletDemoCredentialStatuses(mapOf(id to WalletVisualFixtures.credentialStatus("clear"))))
        val capability = mutableStateOf(true)
        var checks = 0
        setContent {
            CompositionLocalProvider(LocalCredentialStatusUi provides CredentialStatusUiContext(snapshot = snapshot.value,
                checking = snapshot.value.refreshing.isNotEmpty(),
                check = if (capability.value) ({ _: String -> checks++; snapshot.value = snapshot.value.copy(refreshing = setOf(id)) }) else null)) {
                Column(Modifier.width(320.dp)) { CredentialStatusSection(id, compact = true) }
            }
        }
        onNodeWithText("Status clear").assertIsDisplayed()
        onNodeWithTag("credential-status-check-$id").assertDoesNotExist()
        onNodeWithTag("credential-status-details-$id").performClick()
        onNodeWithTag("credential-status-last-checked-$id").assertIsDisplayed()
        assertEquals(0, checks, "Expansion must not check issuer status")
        onNodeWithTag("credential-status-check-$id").performClick().assertIsNotEnabled()
        assertEquals(1, checks)
        onNodeWithTag("credential-status-details-$id").performClick()
        onNodeWithTag("credential-status-check-$id").assertDoesNotExist()
        onNodeWithTag("credential-status-details-$id").performClick()
        onNodeWithTag("credential-status-check-$id").assertIsNotEnabled()
        assertEquals(1, checks, "Collapsing/reopening does not restart the owned request")
        runOnIdle { capability.value = false }
        onNodeWithTag("credential-status-check-$id").assertDoesNotExist()
        runOnIdle { snapshot.value = WalletDemoCredentialStatuses(mapOf(id to WalletVisualFixtures.credentialStatus("not_provided"))) }
        onNodeWithText("Status not provided").assertIsDisplayed()
        onNodeWithTag("credential-status-details-$id").assertDoesNotExist()
    }

    fun unsignedConfirmationIsInvalidatedByNewConsentAndDisabledState() = runComposeUiTest {
        val consent = WalletDemoPaymentConsent("first", "en", null, null, "Pay", null, true, emptyList())
        val review = mutableStateOf<WalletDemoPaymentReview>(WalletDemoPaymentReview.Ready(consent))
        val enabled = mutableStateOf(true)
        val statuses = mutableStateOf(WalletDemoCredentialStatuses())
        var submissions = 0
        setContent {
            CompositionLocalProvider(LocalCredentialStatusUi provides CredentialStatusUiContext(snapshot = statuses.value)) {
                val submission = rememberSharingSubmission(enabled.value, true, reviewKey = "unsigned-payment", credentialStatuses = emptyList(),
                    onSubmit = { submissions++ }, paymentReview = review.value)
                ReviewScaffold(actions = { SharingActionsRow(submission, enabled.value, onCancel = {}, onReject = null, paymentReview = review.value) }) {
                    ReviewChangedNotice(submission)
                }
            }
        }
        val footerHeight = onNodeWithTag(WalletUiTestTags.PresentationActions).fetchSemanticsNode().boundsInRoot.height
        onNodeWithText("Pay").performClick()
        onNodeWithTag("payment-unsigned-confirm").assertIsDisplayed()
        runOnIdle { statuses.value = statuses.value.copy(reviewEpoch = 1) }
        onNodeWithTag("payment-unsigned-confirm").assertDoesNotExist()
        onNodeWithTag("credential-status-review-changed").assert(hasAnyAncestor(hasTestTag("wallet.review.content")))
        assertEquals(footerHeight, onNodeWithTag(WalletUiTestTags.PresentationActions).fetchSemanticsNode().boundsInRoot.height)
        onNodeWithText("Pay").performClick()
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

    fun recreatingSheetPreservesDisclosureChoicesAndConsentRevision() = runComposeUiTest {
        val option = credentialOption(disclosures = listOf(requiredDisclosure(), optionalDisclosure()))
        val optional = disclosureSelection(option, OPTIONAL_DISCLOSURE_PATH)
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
            if (visible.value) TestSharingReviewScreen(review = review, controller = owner, title = "Review request",
                compact = false, onSubmit = { submitted = it }, onCancel = {},
                onBackAtRoot = {})
        }
        onNodeWithTag(WalletUiTestTags.presentationDisclosureToggle(optional.id)).performScrollToContent(this).performClick()
        waitForIdle()
        val revision = prepared
        assertEquals(2, revision) // Initial selection and the explicit optional disclosure.
        runOnIdle { visible.value = false }
        waitForIdle()
        runOnIdle { visible.value = true }
        onNodeWithTag(WalletUiTestTags.presentationDisclosureToggle(optional.id)).performScrollToContent(this).assertIsOn()
        onNodeWithText("Approve").performClick()
        assertEquals(revision, prepared)
        assertEquals(setOf(optional), submitted?.disclosures)
        assertEquals("revision-$revision", submitted?.paymentConsentRevision)
        owner.close(); scope.cancel()
    }

    fun reviewShowsRequestedValuesWithoutExposingUnsharedStoredInformation() = runComposeUiTest {
        var submitted: WalletDemoSharingSelection? = null
        val option = credentialOption(disclosures = listOf(requiredDisclosure(), optionalDisclosure())).copy(
            credentialDataJson = """{"org.iso.18013.5.1":{"given_name":"Ada","private_note":"For my own reference"}}""",
        )
        val optional = disclosureSelection(option, OPTIONAL_DISCLOSURE_PATH)
        setContent {
            TestSharingReviewScreen(compact = false, review = digitalCredentialReview(listOf(option)),
                title = "Share digital credential?", onSubmit = { submitted = it }, onCancel = {})
        }
        onNodeWithText("Given name").performScrollToContent(this).assertIsDisplayed()
        onNodeWithText("Ada").performScrollToContent(this).assertIsDisplayed()
        onNodeWithText("For my own reference").assertDoesNotExist()
        onNodeWithText("Details").assertDoesNotExist()
        onAllNodesWithTag("wallet.screen.header").assertCountEquals(1)
        onNodeWithTag(WalletUiTestTags.presentationDisclosureToggle(optional.id)).performScrollToContent(this).performClick().assertIsOn()
        onNodeWithTag(WalletDemoSharingReviewTestTags.ShareButton).performClick()
        assertEquals(setOf(option.selection), submitted?.credentials)
        assertEquals(setOf(optional), submitted?.disclosures)
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
            TestSharingReviewScreen(review = digitalCredentialReview(), title = "Payment", compact = false,
                onSubmit = { submitted = it }, onCancel = {}, preparePaymentConsent = { consent })
        }
        onNodeWithText("Zahlung prüfen").performScrollToContent(this).assertIsDisplayed()
        onNodeWithText("Betrag").performScrollToContent(this).assertIsDisplayed()
        onNodeWithText("11.56 EUR").performScrollToContent(this).assertIsDisplayed()
        onNodeWithText("Empfänger").performScrollToContent(this).assertIsDisplayed()
        onNodeWithTag("payment-unsigned-warning").performScrollToContent(this).assertIsDisplayed()
        onNodeWithTag("payment-security-hint").assertDoesNotExist()
        onNodeWithText("bound-but-hidden").assertDoesNotExist()
        onNodeWithText("txn-1").assertDoesNotExist()
        onNodeWithTag("payment-details-toggle").performScrollToContent(this).performClick()
        onNodeWithText("txn-1").performScrollToContent(this).assertIsDisplayed()
        onNodeWithText("Given name").performScrollToContent(this).assertIsDisplayed()
        onNodeWithText("Ablehnen").assertIsDisplayed()
        onNodeWithText("Zahlen").performClick()
        assertEquals(null, submitted)
        onNodeWithTag("payment-unsigned-back").performClick()
        assertEquals(null, submitted)
        onNodeWithText("Zahlen").performClick()
        onNodeWithTag("payment-unsigned-confirm").performClick()
        assertEquals("revision", submitted?.paymentConsentRevision)
    }

    fun payeeWebsiteOpensOnlyOnExplicitTap() = runComposeUiTest {
        val openedWebsites = mutableListOf<String>()
        val consent = WalletDemoPaymentConsent("revision", "en", null, null, "Confirm", "Cancel", false,
            listOf(
                WalletDemoPaymentField("Payee", null, "Super Store", WalletDemoPaymentFieldPlacement.Prominent,
                    listOf("payee", "name")),
                WalletDemoPaymentField("Payee website", null, "https://shop.example", WalletDemoPaymentFieldPlacement.Details,
                    listOf("payee", "website")),
            ))
        setContent {
            CompositionLocalProvider(LocalUriHandler provides object : UriHandler {
                override fun openUri(uri: String) { openedWebsites += uri }
            }) {
                PaymentConsentSection(WalletDemoPaymentReview.Ready(consent))
            }
        }
        onNodeWithTag("payment-payee-details-toggle").performClick()
        onNodeWithText("https://shop.example ↗").assertIsDisplayed()
        assertTrue(openedWebsites.isEmpty())
        onNodeWithText("https://shop.example ↗").performClick()
        runOnIdle { assertEquals(listOf("https://shop.example"), openedWebsites) }
    }

    fun paymentMoneyUsesAvailableWidthAtLargeText() = runComposeUiTest {
        val amount = mutableStateOf("11.56")
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)) {
                Box(Modifier.width(320.dp)) {
                    val consent = WalletDemoPaymentConsent("revision", "en", "Payment", null, "Confirm", "Cancel", false,
                        listOf(
                            WalletDemoPaymentField("Amount", null, amount.value, WalletDemoPaymentFieldPlacement.Prominent,
                                path = listOf("amount")),
                            WalletDemoPaymentField("Currency", "Charged in the indicated ISO currency.", "EUR", WalletDemoPaymentFieldPlacement.Prominent,
                                path = listOf("currency")),
                        ))
                    PaymentConsentSection(WalletDemoPaymentReview.Ready(consent))
                }
            }
        }
        onNodeWithText("Amount").assertIsDisplayed()
        onNodeWithText("Currency").assertDoesNotExist()
        onNodeWithText("Charged in the indicated ISO currency.").assertIsDisplayed()
        val shortAmount = onNodeWithText("11.56").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val currency = onNodeWithText("EUR").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals(shortAmount.top, currency.top, "Short money values fit on one line even at large text size")
        assertTrue(shortAmount.right <= currency.left)
        runOnIdle { amount.value = "9007199254740993.01" }
        val longAmount = onNodeWithText("9007199254740993.01").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val wrappedCurrency = onNodeWithText("EUR").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(wrappedCurrency.top >= longAmount.bottom,
            "Wrap when the exact amount cannot fit beside the currency: amount=$longAmount, currency=$wrappedCurrency")
    }

    fun unavailablePaymentInstructionsBlockSubmissionWithoutGenericFallback() = runComposeUiTest {
        val review = digitalCredentialReview().let { it.copy(request = it.request.copy(transactionData = it.request.transactionData.map { group ->
            group.copy(transactionType = "urn:eudi:sca:payment:1")
        })) }
        var submitted = false
        setContent {
            TestSharingReviewScreen(review = review, title = "Payment", compact = false,
                onSubmit = { submitted = true }, onCancel = {},
                preparePaymentConsent = { error("Required payment instructions are unavailable in your preferred languages.") })
        }
        onNodeWithTag("payment-consent-blocked").performScrollToContent(this).assertIsDisplayed()
        onNodeWithText("42.00").assertDoesNotExist()
        onNodeWithText("Confirm payment").assertIsNotEnabled()
        assertEquals(false, submitted)
    }

    /**
     * The `dc_api.jwt` case. The signature covers transaction_data, so every authorized value has to be
     * on the review, and the encryption the response mode implies has to be stated.
     */
    fun digitalCredentialReviewShowsOriginTransactionDataAndEncryption() = runComposeUiTest {
        setContent {
            TestSharingReviewScreen(
                compact = false,
                review = digitalCredentialReview(),
                title = "Share digital credential?",
                onSubmit = {},
                onCancel = {},
            )
        }

        onNodeWithTag(WalletDemoSharingReviewTestTags.Review).assertIsDisplayed()
        onNodeWithTag(WalletDemoSharingReviewTestTags.RequesterSection).performScrollToContent(this).assertIsDisplayed()
        // An unsigned Digital Credentials request has no verifier metadata, so the authenticated origin
        // is the requester identity: shown once, and captioned as verified, because an uncaptioned origin
        // reads as one more self-asserted requester claim.
        onNodeWithText("https://verifier.example").assertIsDisplayed()
        onAllNodesWithText("https://verifier.example").assertCountEquals(1)
        onNodeWithText("Verified website").assertIsDisplayed()
        onAllNodesWithText("Verified website").assertCountEquals(1)
        onNodeWithText("Review payment").performScrollToContent(this).assertIsDisplayed()
        onNodeWithText("Currency").assertDoesNotExist()
        for ((field, label, value) in listOf(
            Triple("amount", "Amount", "42.00"),
            Triple("currency", null, "EUR"),
            Triple("merchant_name", "Merchant name", "ACME Corp"),
        )) {
            val row = hasAnyAncestor(hasTestTag(WalletUiTestTags.claim("transactionData[0].details.$field")))
            label?.let { onNode(hasText(it) and row).performScrollToContent(this).assertIsDisplayed() }
            onNode(hasText(value) and row).performScrollToContent(this).assertIsDisplayed()
        }
        onNodeWithContentDescription("Show Verifier details").performScrollToContent(this).performClick()
        onNodeWithTag(WalletDemoSharingReviewTestTags.ResponseProtectionSection, useUnmergedTree = true)
            .performScrollToContent(this)
            .assertIsDisplayed()
        onNodeWithText("OpenID4VP dc_api.jwt").performScrollToContent(this).assertIsDisplayed()
        onNodeWithText("Given name").performScrollToContent(this).assertIsDisplayed()
        onNodeWithText("Ada").performScrollToContent(this).assertIsDisplayed()
    }

    /**
     * Self-asserted verifier metadata heads the section, and the authenticated origin stays visible beside
     * it under its own label. Showing only the name a request asked to be called would hide the one
     * requester fact that was actually verified.
     */
    fun verifiedOriginStaysVisibleBesideSelfAssertedVerifierMetadata() = runComposeUiTest {
        setContent {
            TestSharingReviewScreen(
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
            TestSharingReviewScreen(
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
            TestSharingReviewScreen(
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
            TestSharingReviewScreen(
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
            TestSharingReviewScreen(
                compact = false,
                review = annexCReview(WalletDemoReaderTrust.Untrusted("No reader trust policy is configured")),
                title = "Share mobile document?",
                onSubmit = {},
                onCancel = {},
            )
        }

        onNodeWithTag(WalletUiTestTags.PresentationRequesterDetailsToggle).performClick()
        onNodeWithTag(WalletDemoSharingReviewTestTags.ReaderTrustSection).performScrollToContent(this).assertIsDisplayed()
        onNodeWithText("Reader identity not trusted by this wallet").performScrollToContent(this).assertIsDisplayed()
        onNodeWithText("No reader trust policy is configured").performScrollToContent(this).assertIsDisplayed()
        onAllNodesWithText("Trusted reader").assertCountEquals(0)
        onNodeWithText("ISO 18013-7 Annex C HPKE").performScrollToContent(this).assertIsDisplayed()
    }

    /** A trusted reader is named, which is the only state in which the wallet can identify the reader. */
    fun trustedReaderIsNamedOnTheReview() = runComposeUiTest {
        setContent {
            TestSharingReviewScreen(
                compact = false,
                review = annexCReview(WalletDemoReaderTrust.Trusted("CN=Example Reader")),
                title = "Share mobile document?",
                onSubmit = {},
                onCancel = {},
            )
        }

        onNodeWithTag(WalletUiTestTags.PresentationRequesterDetailsToggle).performClick()
        onNodeWithText("Trusted reader").performScrollToContent(this).assertIsDisplayed()
        onNodeWithText("CN=Example Reader").performScrollToContent(this).assertIsDisplayed()
    }

    /**
     * A request for two documents cannot be answered with one, so Share stays disabled until every
     * requirement has a credential.
     */
    fun shareStaysDisabledUntilEveryRequestedDocumentHasACredential() = runComposeUiTest {
        var submitted: WalletDemoSharingSelection? = null
        val mdl = credentialOption(queryId = "org.iso.18013.5.1.mDL", credentialId = "credential-1")
        val photoId = credentialOption(queryId = "org.iso.23220.photoid.1", credentialId = "credential-2")
        val completeReview = annexCReview(
            readerTrust = WalletDemoReaderTrust.NotAuthenticated,
            credentialOptions = listOf(mdl, photoId),
        )
        // The request requires both documents, but the second match is not available yet.
        val review = mutableStateOf(completeReview.copy(credentialOptions = listOf(mdl)))
        setContent {
            TestSharingReviewScreen(
                compact = false,
                review = review.value,
                title = "Share mobile document?",
                onSubmit = { submitted = it },
                onCancel = {},
            )
        }
        onNodeWithTag(WalletUiTestTags.presentationCredentialToggle(mdl.selection.id), useUnmergedTree = true)
            .performScrollToContent(this)
        onNodeWithTag(WalletDemoSharingReviewTestTags.ShareButton).assertIsNotEnabled()
        assertNull(submitted)

        runOnIdle { review.value = completeReview }
        onNodeWithTag(WalletUiTestTags.presentationCredentialToggle(mdl.selection.id), useUnmergedTree = true)
            .performScrollToContent(this).assertHasNoClickAction()
        onNodeWithTag(WalletUiTestTags.presentationCredentialToggle(photoId.selection.id), useUnmergedTree = true)
            .performScrollToContent(this).assertHasNoClickAction()
        onNodeWithText("Credential information").assertDoesNotExist()
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
            TestSharingReviewScreen(
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
            .performScrollToContent(this)
            .assertIsSelected()
        onNodeWithTag(WalletUiTestTags.presentationCredentialToggle(second.selection.id))
            .performScrollToContent(this)
            .assertIsNotSelected()

        // Approving the first credential's optional disclosure gives the switch something to leak;
        // without it, an implementation that never dropped disclosures would still pass.
        val firstOptionalDisclosure = disclosureSelection(first, OPTIONAL_DISCLOSURE_PATH)
        onNodeWithTag(WalletUiTestTags.presentationDisclosureToggle(firstOptionalDisclosure.id))
            .performScrollToContent(this)
            .performClick()
        onNodeWithTag(WalletUiTestTags.presentationDisclosureToggle(firstOptionalDisclosure.id))
            .performScrollToContent(this)
            .assertIsOn()

        onNodeWithTag(WalletUiTestTags.presentationCredentialToggle(second.selection.id))
            .performScrollToContent(this)
            .performClick()
        onNodeWithTag(WalletUiTestTags.presentationCredentialToggle(first.selection.id))
            .performScrollToContent(this)
            .assertIsNotSelected()

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
            TestSharingReviewScreen(
                compact = false,
                review = digitalCredentialReview(credentialOptions = listOf(option, credentialOption(queryId = "other", credentialId = "other"))),
                title = "Share digital credential?",
                onSubmit = { submitted = it },
                onCancel = {},
            )
        }

        val required = disclosureSelection(option, REQUIRED_DISCLOSURE_PATH)
        val optional = disclosureSelection(option, OPTIONAL_DISCLOSURE_PATH)
        onNodeWithTag(WalletUiTestTags.presentationDisclosure(required.id)).performScrollToContent(this).assertIsDisplayed()
        onAllNodesWithTag(WalletUiTestTags.presentationDisclosureToggle(required.id)).assertCountEquals(0)
        onNodeWithTag(WalletUiTestTags.presentationDisclosureToggle(optional.id)).performScrollToContent(this).assertIsOff()
        onNodeWithText("Not shared").performScrollToContent(this).assertIsDisplayed()

        // A required disclosure is not carried as a selection, so an empty disclosure set is what
        // "the user approved nothing optional" looks like.
        onNodeWithTag(WalletDemoSharingReviewTestTags.ShareButton).performClick()
        assertEquals(emptySet<WalletDemoPresentationDisclosureSelection>(), submitted?.disclosures)

        onNodeWithTag(WalletUiTestTags.presentationDisclosureToggle(optional.id)).performScrollToContent(this).performClick()
        onNodeWithTag(WalletDemoSharingReviewTestTags.ShareButton).performClick()
        assertEquals(setOf(optional), submitted?.disclosures)

        onNodeWithTag(WalletUiTestTags.presentationCredentialToggle(option.selection.id))
            .performScrollToContent(this)
            .performClick()
        onAllNodesWithTag(WalletUiTestTags.presentationDisclosureToggle(optional.id)).assertCountEquals(0)
        onNodeWithTag(WalletDemoSharingReviewTestTags.ShareButton).assertIsNotEnabled()
        onNodeWithText("Select a credential to see what will be shared.").performScrollToContent(this).assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.presentationCredentialToggle(option.selection.id)).performScrollToContent(this).performClick()
        onNodeWithTag(WalletUiTestTags.presentationDisclosureToggle(optional.id)).performScrollToContent(this).assertIsOff()
    }

}
