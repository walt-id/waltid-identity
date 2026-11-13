package id.walt.walletdemo.compose.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertTextEquals

import id.walt.credentials.status.presentation
import id.walt.credentials.status.CredentialStatus
import id.walt.credentials.status.CredentialStatusPresentationData
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import id.walt.walletdemo.compose.ui.components.CredentialStatusSection
import id.walt.walletdemo.compose.ui.components.CredentialStatusUiContext
import id.walt.walletdemo.compose.ui.components.LocalCredentialStatusUi
import id.walt.walletdemo.compose.ui.components.WalletControlsPreview
import id.walt.walletdemo.compose.ui.components.WalletHeaderButton
import id.walt.walletdemo.compose.ui.components.title
import id.walt.walletdemo.compose.logic.DemoBiometricAuthenticator
import id.walt.walletdemo.compose.logic.DemoBiometricKind
import id.walt.walletdemo.compose.logic.DemoBiometricResult
import id.walt.walletdemo.compose.logic.DemoPinStore
import id.walt.walletdemo.compose.logic.DemoReaderTrustSettingsController
import id.walt.walletdemo.compose.logic.InMemoryDemoPinStore
import id.walt.walletdemo.compose.logic.InMemoryDemoReaderTrustSettingsStore
import id.walt.walletdemo.compose.logic.PinSetupStep
import id.walt.walletdemo.compose.logic.WalletAccessNotice
import id.walt.walletdemo.compose.logic.WalletAccessOperation
import id.walt.walletdemo.compose.logic.WalletAccessState
import id.walt.walletdemo.compose.logic.WalletAuthState
import id.walt.walletdemo.compose.logic.WalletDemoController
import id.walt.walletdemo.compose.logic.WalletDemoCredentialStatuses
import id.walt.walletdemo.compose.logic.WalletDemoPresentationCredentialRequirement
import id.walt.walletdemo.compose.logic.WalletDemoSharingRequest
import id.walt.walletdemo.compose.logic.WalletDemoSharingRequester
import id.walt.walletdemo.compose.logic.WalletDemoSharingReview
import id.walt.walletdemo.compose.logic.WalletDemoSharingSelection
import id.walt.walletdemo.compose.logic.WalletDemoStatusRefreshSummary
import id.walt.walletdemo.compose.logic.WalletExternalFlow
import id.walt.walletdemo.compose.logic.WalletPinChange
import id.walt.walletdemo.compose.logic.consent
import id.walt.walletdemo.compose.logic.defaultCredentialSelection
import id.walt.walletdemo.compose.logic.isBusy
import id.walt.walletdemo.compose.logic.problem
import id.walt.walletdemo.compose.logic.DemoBiometricAvailability
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import id.walt.walletdemo.compose.logic.ClaimItem
import id.walt.walletdemo.compose.logic.ClaimItemPath
import id.walt.walletdemo.compose.logic.DisplayValue
import id.walt.walletdemo.compose.logic.WalletDateTime
import id.walt.walletdemo.compose.ui.components.ClaimValueRow
import kotlin.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import id.walt.walletdemo.compose.logic.WalletDemoUiState
import id.walt.walletdemo.compose.logic.WalletDemoContinuationStatus
import id.walt.walletdemo.compose.logic.WalletDemoIssuanceProblem
import id.walt.walletdemo.compose.ui.components.ReviewScaffold
import id.walt.walletdemo.compose.ui.components.StatusCard
import id.walt.walletdemo.compose.ui.components.WalletFooter
import id.walt.walletdemo.compose.ui.components.WalletIcon
import id.walt.walletdemo.compose.ui.components.WalletSymbol
import id.walt.walletdemo.compose.logic.WalletDemoProximityUiState
import id.walt.walletdemo.compose.logic.WalletDemoProximityHostActionExecutor
import id.walt.wallet2.mobile.ProximityEngagement
import id.walt.wallet2.mobile.ProximityHostActionResult
import id.walt.wallet2.mobile.ProximityState
import id.walt.walletdemo.compose.ui.components.CredentialDetailsContent
import id.walt.walletdemo.compose.ui.components.CredentialCardStack
import id.walt.walletdemo.compose.ui.components.WalletCredentialContentMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment
import kotlin.test.assertTrue
import id.walt.walletdemo.compose.ui.components.OfferedCredentialDetails
import id.walt.walletdemo.compose.ui.screens.ReceiveTab
import id.walt.walletdemo.compose.ui.screens.SettingsScreen
import id.walt.walletdemo.compose.ui.screens.WalletHeader
import id.walt.walletdemo.compose.ui.components.WalletScreenHeader
import id.walt.walletdemo.compose.ui.screens.WalletScanScreen
import id.walt.walletdemo.compose.ui.screens.CredentialsTab
import id.walt.walletdemo.compose.logic.WalletSessionState
import id.walt.walletdemo.compose.logic.WalletDemoTab
import id.walt.walletdemo.compose.logic.WalletOperationState
import id.walt.walletdemo.compose.logic.WalletDemoPaymentFieldPlacement

/** Content and readiness are shared; the platform adapter owns the renderer and baseline path. */
@OptIn(ExperimentalTestApi::class)
internal class WalletVisualScenarios(
    private val test: ComposeUiTest,
    private val captureImage: (String, String) -> Unit,
    private val platformTheme: @Composable (@Composable () -> Unit) -> Unit = { it() },
) {
    private var layoutDirection = "ltr"
    private var statusContext = CredentialStatusUiContext(WalletVisualFixtures.noReferenceStatuses)
    private var captureIdentifier: String? = null
    private val captureFailures = mutableListOf<AssertionError>()

    fun execute(block: WalletVisualScenarios.() -> Unit) {
        block()
        captureFailures.firstOrNull()?.let { failure ->
            captureFailures.drop(1).forEach(failure::addSuppressed)
            throw failure
        }
    }

    private fun capture(id: String) {
        // Capture the settled Compose state after interactions and navigation.
        // Advancing virtual time keeps this independent of host rendering speed.
        test.mainClock.advanceTimeBy(1_000)
        test.waitForIdle()
        // Keep later states and interaction assertions in a journey reachable after a pixel diff.
        // The platform test still fails after the scenario; references are never accepted here.
        try { captureImage(captureIdentifier ?: id, layoutDirection) } catch (failure: AssertionError) { captureFailures += failure }
    }

    private fun content(body: @Composable () -> Unit) {
        test.setContent {
            platformTheme {
                WalletDemoTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        CompositionLocalProvider(LocalCredentialStatusUi provides statusContext, content = body)
                    }
                }
            }
        }
        test.waitUntil {
            test.onAllNodesWithTag(WalletUiTestTags.PresentationReview).fetchSemanticsNodes().isEmpty() ||
                test.onAllNodesWithTag("review-information-to-share").fetchSemanticsNodes().isNotEmpty()
        }
    }

    fun credentialStatusHome() {
        val ready = WalletVisualFixtures.partialResult.session as WalletSessionState.Ready
        statusContext = CredentialStatusUiContext(WalletDemoCredentialStatuses(
            ready.credentials.associate { it.id to WalletVisualFixtures.credentialStatus("revoked_partial") }))
        captureIdentifier = "status.home.issue"
        walletHome()
    }

    fun credentialStatusMixedArtwork(rtl: Boolean = false) = with(test) {
        val images = WalletVisualImages()
        images.install()
        statusContext = CredentialStatusUiContext(WalletArtworkFixtures.statuses)
        layoutDirection = if (rtl) "rtl" else "ltr"
        try {
            content {
                CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        .wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = WalletCredentialContentMaxWidth).padding(20.dp)) {
                        CredentialCardStack(WalletArtworkFixtures.cards, onOpenDetails = {})
                    }
                }
            }
            WalletArtworkFixtures.images.keys.forEach { uri -> waitUntil { images.isReady(uri) } }
            waitForIdle()
            WalletArtworkFixtures.cards.forEachIndexed { index, card ->
                val status = WalletArtworkFixtures.statuses.credentials.getValue(card.id)
                val badge = onNodeWithTag("credential-status-icon-${card.id}", useUnmergedTree = true)
                if (!status.hasIssue) badge.assertDoesNotExist()
                else {
                    badge.assertIsDisplayed()
                    val bounds = badge.fetchSemanticsNode().boundsInRoot
                    val cardBounds = onNodeWithTag(WalletUiTestTags.credentialCard(card.id)).fetchSemanticsNode().boundsInRoot
                    assertTrue(bounds.left > cardBounds.center.x, "Artwork decoration remains physically right in both reading directions")
                    assertTrue(bounds.right < cardBounds.right)
                    WalletArtworkFixtures.cards.getOrNull(index + 1)?.let { next ->
                        val nextTop = onNodeWithTag(WalletUiTestTags.credentialCard(next.id)).fetchSemanticsNode().boundsInRoot.top
                        assertTrue(bounds.bottom < nextTop, "The complete badge must clear the next stacked card")
                    }
                }
            }
            capture(if (rtl) "status.home.mixed_artwork.dark_large_text_rtl" else "status.home.mixed_artwork")
        } finally { images.close() }
    }

    fun credentialStatusDetails(adverse: Boolean = false, expanded: Boolean = false) = with(test) {
        val details = WalletVisualFixtures.credentialDetails
        val status = WalletVisualFixtures.credentialStatus(if (adverse) "revoked_partial" else "clear")
        statusContext = CredentialStatusUiContext(WalletDemoCredentialStatuses(mapOf(details.summary.id to status)), check = {})
        content {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                CredentialDetailsContent(details)
            }
        }
        onNodeWithTag("credential-status-${details.summary.id}").performScrollTo().assertIsDisplayed()
        onNodeWithText(if (adverse) "Last known: revoked" else "Status clear").assertIsDisplayed()
        onNodeWithTag("credential-status-check-${details.summary.id}").assertIsEnabled()
        if (expanded) {
            onNodeWithTag("credential-status-details-${details.summary.id}").performScrollTo().performClick()
            onNodeWithText("Revocation").assertExists()
            onNodeWithText("Partly checked").assertExists()
            onNodeWithText("The latest check could not complete. Previous results keep their original check time.")
                .performScrollTo().assertIsDisplayed()
        }
        capture(if (expanded) "status.details.expanded" else if (adverse) "status.details.adverse" else "status.details.clear")
    }

    fun credentialStatusKinds(compactRtl: Boolean = false) = with(test) {
        // Both UI renderers consume these shared semantic fixtures; validate them against the evaluator.
        WalletVisualFixtures.statusData.forEach { (_, raw) ->
            val value = raw.jsonObject
            val metadata = Json.decodeFromJsonElement<CredentialStatus>(value.getValue("status"))
            val expected = Json.decodeFromJsonElement<CredentialStatusPresentationData>(value.getValue("presentation"))
            assertEquals(expected, metadata.presentation(Instant.parse(value.getValue("evaluatedAt").jsonPrimitive.content)).toData())
        }
        val names = if (compactRtl) listOf("revoked_partial", "unverified", "unsupported")
            else listOf("not_provided", "not_checked", "clear", "stale", "unsupported", "unverified", "suspended")
        statusContext = CredentialStatusUiContext(WalletDemoCredentialStatuses(names.associateWith(WalletVisualFixtures::credentialStatus)))
        layoutDirection = if (compactRtl) "rtl" else "ltr"
        content {
            CompositionLocalProvider(LocalLayoutDirection provides if (compactRtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    names.forEach { CredentialStatusSection(it, compact = true) }
                }
            }
        }
        onNodeWithText(if (compactRtl) "Last known: revoked" else "Not checked").assertIsDisplayed()
        capture(if (compactRtl) "status.compact.dark_large_text_rtl" else "status.states")
    }

    fun credentialStatusSharing() = with(test) {
        val review = WalletVisualFixtures.providerReview
        val values = review.credentialOptions.associate { it.credentialId to WalletVisualFixtures.credentialStatus("revoked_partial") }
        statusContext = CredentialStatusUiContext(WalletDemoCredentialStatuses(values))
        var submitted: WalletDemoSharingSelection? = null
        content {
            WalletDemoSharingReviewScreen(review = review, title = "Share documents",
                onSubmit = { submitted = it }, onCancel = {}, onBackAtRoot = {})
        }
        onNodeWithTag(WalletUiTestTags.PresentationSubmitButton).assertIsDisplayed().assertIsEnabled()
        onNodeWithText("Share anyway").assertDoesNotExist()
        onNodeWithTag("sharing-credential-status-advisory").assertDoesNotExist()
        capture("status.sharing.review")
        val id = review.credentialOptions.first().credentialId
        onNodeWithTag("credential-status-details-$id").performScrollToContent(this).performClick()
        onNodeWithText("Revocation").assertExists()
        onNodeWithText("Partly checked").assertExists()
        onNodeWithTag(WalletUiTestTags.PresentationSubmitButton).performClick()
        onNodeWithText("Share with status issues?").assertIsDisplayed()
        assertEquals(null, submitted)
        onNodeWithTag("credential-status-back").performClick()
        assertEquals(null, submitted)
        onNodeWithTag(WalletUiTestTags.PresentationSubmitButton).performClick()
        onNodeWithTag("credential-status-confirm").performClick()
        assertEquals(review.defaultCredentialSelection(), submitted?.credentials,
            "Expanding status Details must not toggle the credential selection")
        assertEquals(values.mapValues { it.value.advisoryRevision }, submitted?.credentialStatusRevisions)
    }

    fun credentialStatusConfirmation(payment: Boolean = false) = with(test) {
        val review = if (payment) WalletVisualFixtures.paymentReview else WalletVisualFixtures.providerReview
        val affectedID = review.credentialOptions.first().credentialId
        val values = review.credentialOptions.associate {
            it.credentialId to WalletVisualFixtures.credentialStatus(if (it.credentialId == affectedID) "revoked_current" else "not_provided")
        }
        statusContext = CredentialStatusUiContext(WalletDemoCredentialStatuses(values))
        var submitted = false
        content {
            WalletDemoSharingReviewScreen(review = review, title = if (payment) "Payment" else "Share documents",
                onSubmit = { submitted = true }, onCancel = {}, compact = false,
                preparePaymentConsent = if (payment) ({ WalletVisualFixtures.payment }) else null)
        }
        val submit = onNodeWithTag(WalletUiTestTags.PresentationSubmitButton)
        submit.assertIsDisplayed().assertIsEnabled().performClick()
        val confirm = if (payment) "payment-unsigned-confirm" else "credential-status-confirm"
        val back = if (payment) "payment-unsigned-back" else "credential-status-back"
        onNodeWithTag(confirm).assertIsDisplayed()
        onNodeWithText(if (payment) "Unsigned payment request" else "Share with status issues?").assertIsDisplayed()
        assertFalse(submitted)
        capture(if (payment) "status.payment.confirmation" else "status.sharing.confirmation")
        onNodeWithTag(back).performClick()
        onNodeWithTag(confirm).assertDoesNotExist()
        assertFalse(submitted)
    }

    fun credentialStatusPayment() = with(test) {
        val review = WalletVisualFixtures.paymentReview
        val affected = review.credentialOptions.first()
        statusContext = CredentialStatusUiContext(WalletDemoCredentialStatuses(review.credentialOptions.associate {
            it.credentialId to WalletVisualFixtures.credentialStatus(
                if (it.credentialId == affected.credentialId) "revoked_partial" else "not_provided")
        }))
        content {
            WalletDemoSharingReviewScreen(review = review, title = "Payment", onSubmit = {}, onCancel = {},
                preparePaymentConsent = { WalletVisualFixtures.paymentTerms(recurring = false) })
        }
        onNodeWithTag(WalletUiTestTags.presentationCredential(affected.selection.id))
            .performScrollToContent(this).assertIsDisplayed()
        onNodeWithTag("credential-status-${review.credentialOptions.last().credentialId}")
            .performScrollToContent(this).assertIsDisplayed()
        onNodeWithText("Status not provided").assertIsDisplayed()
        onNodeWithText("Last known: revoked").assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.PresentationSubmitButton).assertIsDisplayed().assertIsEnabled()
            .assertTextEquals("Confirm payment")
        onAllNodesWithText("Share anyway").assertCountEquals(0)
        capture("status.payment.review")
    }

    fun credentialStatusSettings(checking: Boolean = false, helpExpanded: Boolean = false) {
        var checks = 0
        val preferenceChanges = mutableListOf<Boolean>()
        statusContext = CredentialStatusUiContext(automatic = false, checking = checking, checkAll = { checks++ },
            setAutomatic = { preferenceChanges += it },
            summary = if (checking) null else WalletDemoStatusRefreshSummary(2, 1, 1, 1))
        captureIdentifier = if (helpExpanded) "status.settings.help" else if (checking) "status.settings.checking" else "status.settings.partial"
        settingsRoot(if (helpExpanded) "credential_status_help" else "credential_status")
        with(test) {
            onNodeWithTag("settings-credential-status-automatic").assertIsOff()
            if (checking) onNodeWithTag("settings-credential-status-check-all").assertIsNotEnabled()
            else onNodeWithText("Some checks were deferred. Try again later.").assertIsDisplayed()
            if (helpExpanded) {
                val explanation = onNodeWithText("“With check errors” counts credentials with failed checks, conflicting results or unreadable status information. These credentials are also included in the other counts.")
                explanation.assertIsDisplayed()
                onNodeWithTag("settings-credential-status-how").performClick()
                explanation.assertDoesNotExist()
                onNodeWithTag("settings-credential-status-automatic").assertIsOff()
                onNodeWithText("Processed").assertIsDisplayed()
                assertEquals(0, checks)
                assertEquals(emptyList(), preferenceChanges)
            }
        }
    }

    fun controls(rtl: Boolean = false) = with(test) {
        layoutDirection = if (rtl) "rtl" else "ltr"
        content {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides
                if (rtl) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr) {
                id.walt.walletdemo.compose.ui.components.WalletControlsPreview()
            }
        }
        onNodeWithTag("preview.copies.less").assertIsDisplayed().assertIsNotEnabled()
        onNodeWithTag("preview.copies.more").assertIsDisplayed().assertIsEnabled()
        capture(if (rtl) "components.controls.rtl" else "components.controls.default")
        onNodeWithTag("preview.copies.more").performClick()
        onNodeWithText("Copies: 2").assertIsDisplayed()
    }

    fun dateValues(rtl: Boolean = false) = with(test) {
        layoutDirection = if (rtl) "rtl" else "ltr"
        val values = listOf(
            ClaimItem(ClaimItemPath.topLevel("iat"), label = "Issued at",
                value = DisplayValue.InstantValue(Instant.parse("2026-10-25T00:30:00Z")), rawValue = null),
            ClaimItem(ClaimItemPath.topLevel("exp"), label = "Expires",
                value = DisplayValue.InstantValue(Instant.parse("2026-10-25T01:30:00Z")), rawValue = null),
            ClaimItem(ClaimItemPath.topLevel("birth_date"), label = "Birth date",
                value = DisplayValue.Text("1990-05-21"), rawValue = null),
        )
        content {
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                LocalWalletTimeFormatter provides { WalletDateTime.format(it, "Europe/Budapest", if (rtl) "ar" else "en-GB", true) }) {
                Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    values.forEach { ClaimValueRow(it) }
                }
            }
        }
        values.forEach { onNodeWithTag(WalletUiTestTags.claim(it.path.id)).assertIsDisplayed() }
        onNodeWithText("1990-05-21").assertIsDisplayed()
        if (!rtl) {
            onNodeWithText("02:30 \u2068(Budapest, CEST)\u2069", substring = true).assertIsDisplayed()
            onNodeWithText("02:30 \u2068(Budapest, CET)\u2069", substring = true).assertIsDisplayed()
        }
        capture(if (rtl) "components.dates.compact_large_text_rtl" else "components.dates")
    }

    fun walletOpening(failure: Boolean = false) = with(test) {
        content { id.walt.walletdemo.compose.ui.screens.WalletOpeningScreen(
            if (failure) "Local storage could not be read. Try again." else null, {}) }
        if (failure) onNodeWithTag("wallet.openingRetry").assertIsDisplayed().assertIsEnabled()
        else onNodeWithTag(WalletUiTestTags.CredentialsLoading).assertIsDisplayed()
        capture(if (failure) "wallet.opening.failure" else "wallet.opening.loading")
    }

    fun pinStorageUnavailable() = with(test) {
        val controller = id.walt.walletdemo.compose.logic.WalletDemoController(WalletUiTestWallet(),
            id.walt.walletdemo.compose.logic.InMemoryDemoPinStore())
        content { id.walt.walletdemo.compose.ui.screens.PinStorageUnavailableScreen(controller,
            "Secure PIN storage could not be read") }
        onNodeWithText("PIN storage unavailable").assertIsDisplayed()
        onNodeWithTag("wallet.pinStorageRetryButton").assertIsDisplayed().assertIsEnabled()
        capture("access.storage.unavailable")
    }

    fun externalReceiving(unavailable: Boolean = false) = with(test) {
        val controller = id.walt.walletdemo.compose.logic.WalletDemoController(WalletUiTestWallet(), id.walt.walletdemo.compose.logic.InMemoryDemoPinStore())
        val flow = if (unavailable) id.walt.walletdemo.compose.logic.WalletExternalFlow.UnavailableCallback("openid://callback")
            else id.walt.walletdemo.compose.logic.WalletExternalFlow.Active("openid-credential-offer://fixture", id.walt.walletdemo.compose.logic.WalletExternalFlow.Kind.Offer)
        val state = WalletDemoUiState(access = id.walt.walletdemo.compose.logic.WalletAccessState(auth = id.walt.walletdemo.compose.logic.WalletAuthState.Unlocked),
            session = WalletVisualFixtures.partialResult.session, selectedTab = WalletDemoTab.Receive, externalFlow = flow,
            offerPreview = if (unavailable) null else WalletVisualFixtures.offer, issuanceCopyCounts = WalletVisualFixtures.copies)
        content {
            WalletReviewHost(true, {}) {
                id.walt.walletdemo.compose.ui.screens.WalletFlowScreen(controller, state, {})
            }
        }
        onNodeWithTag("wallet.external.close").assertIsDisplayed().assertIsEnabled()
        onAllNodesWithTag("wallet.offerInput").assertCountEquals(0)
        if (unavailable) onNodeWithTag("wallet.external.unavailable").assertIsDisplayed()
        else onNodeWithTag(WalletUiTestTags.OfferAcceptButton).assertIsDisplayed().assertIsEnabled()
        capture(if (unavailable) "external.callback.unavailable" else "external.receiving.review")
    }

    fun pin(state: String) = with(test) {
        layoutDirection = if (state == "rtl") "rtl" else "ltr"
        val gate = kotlinx.coroutines.CompletableDeferred<id.walt.walletdemo.compose.logic.DemoBiometricResult>()
        val biometrics = object : id.walt.walletdemo.compose.logic.DemoBiometricAuthenticator {
            override fun availability() = if (state in setOf("confirmation", "biometric_prompt", "compact_dark_large_text")) DemoBiometricAvailability.Available else DemoBiometricAvailability.Unavailable
            override suspend fun authenticate(reason: String) = gate.await()
        }
        val memory = id.walt.walletdemo.compose.logic.InMemoryDemoPinStore()
        val store = if (state == "unlock") object : id.walt.walletdemo.compose.logic.DemoPinStore by memory {
            override fun hasPin() = true
        } else memory
        val controller = id.walt.walletdemo.compose.logic.WalletDemoController(WalletUiTestWallet(), store, biometrics)
        if (state == "unlock") controller.updatePin("12")
        else if (state != "setup") {
            controller.updatePin("1234")
            controller.submitPin()
            if (state == "mismatch") controller.updatePinConfirmation("4321")
            if (state == "compact_dark_large_text") controller.updatePinConfirmation("123")
            if (state == "biometric_prompt") controller.updatePinConfirmation("1234")
        }
        if (state == "biometric_prompt") {
            waitUntil { controller.state.value.auth is id.walt.walletdemo.compose.logic.WalletAuthState.BiometricSetup }
            val auth = controller.state.value.auth as id.walt.walletdemo.compose.logic.WalletAuthState.BiometricSetup
            content { id.walt.walletdemo.compose.ui.screens.BiometricSetupScreen(
                auth,
                busy = true, availability = DemoBiometricAvailability.Available, onRetry = {}, onContinue = {}) }
            onNodeWithTag(WalletUiTestTags.BiometricSetupRetry).assertIsNotEnabled()
            onNodeWithTag(WalletUiTestTags.BiometricSetupContinue).assertIsNotEnabled()
            capture("onboarding.pin.$state")
            gate.complete(id.walt.walletdemo.compose.logic.DemoBiometricResult.Failed)
            return@with
        }
        val access = controller.state.value.access
        content {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalLayoutDirection provides
                    if (state == "rtl") androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr,
                LocalWalletVisualPreferences provides WalletVisualPreferences(reduceMotion = state == "rtl"),
            ) { id.walt.walletdemo.compose.ui.screens.PinScreen(controller, access) }
        }
        onAllNodesWithTag("wallet.pinBiometricToggle").assertCountEquals(0)
        val confirming = state !in setOf("setup", "unlock")
        onNodeWithTag(if (confirming) WalletUiTestTags.PinConfirmationInput else WalletUiTestTags.PinInput).assertIsDisplayed()
        onAllNodesWithTag(if (confirming) WalletUiTestTags.PinInput else WalletUiTestTags.PinConfirmationInput).assertCountEquals(0)
        when (state) {
            "unlock" -> onNodeWithTag(WalletUiTestTags.PinClearButton).assertIsEnabled()
            "mismatch" -> onNodeWithText("PIN confirmation does not match").assertIsDisplayed()
        }
        onAllNodesWithTag(WalletUiTestTags.PinSubmitButton).assertCountEquals(0)
        onNodeWithTag(WalletUiTestTags.PinClearButton).assertIsDisplayed()
        capture("onboarding.pin.$state")
        gate.complete(id.walt.walletdemo.compose.logic.DemoBiometricResult.Failed)
    }

    fun walletAccess(state: String) = with(test) {
        val controller = id.walt.walletdemo.compose.logic.WalletDemoController(WalletUiTestWallet(), id.walt.walletdemo.compose.logic.InMemoryDemoPinStore())
        val entry = id.walt.walletdemo.compose.logic.WalletAuthState.Login(
            error = if (state == "rejected") "Wrong PIN" else null,
            biometricPromptConsumed = true,
            biometricOutcome = when (state) {
                "biometric_fallback" -> id.walt.walletdemo.compose.logic.DemoBiometricResult.Failed
                "biometric_lockout" -> id.walt.walletdemo.compose.logic.DemoBiometricResult.LockedOut
                else -> null
            })
        val unlocking = state in setOf("rejected", "biometric_fallback", "biometric_lockout", "biometric_unavailable")
        val access = id.walt.walletdemo.compose.logic.WalletAccessState(
            auth = if (unlocking) entry else id.walt.walletdemo.compose.logic.WalletAuthState.Unlocked,
            biometricAvailability = when (state) {
                "biometric_fallback" -> DemoBiometricAvailability.Available
                "biometric_lockout" -> DemoBiometricAvailability.LockedOut
                else -> DemoBiometricAvailability.Unavailable
            }, biometricKind = if (state == "biometric_unavailable") id.walt.walletdemo.compose.logic.DemoBiometricKind.FaceId else id.walt.walletdemo.compose.logic.DemoBiometricKind.Generic,
            biometricEnabled = state.startsWith("biometric"),
            pinChange = when (state) {
                "current_pin" -> id.walt.walletdemo.compose.logic.WalletPinChange.Current()
                "new_pin" -> id.walt.walletdemo.compose.logic.WalletPinChange.NewPin()
                "confirmation", "save_failure" -> id.walt.walletdemo.compose.logic.WalletPinChange.NewPin(
                    id.walt.walletdemo.compose.logic.WalletAuthState.Setup(pin = "5678", confirmation = if (state == "save_failure") "5678" else "",
                        step = id.walt.walletdemo.compose.logic.PinSetupStep.Confirm))
                else -> null
            },
            operation = if (state == "save_failure") id.walt.walletdemo.compose.logic.WalletAccessOperation.RetryPin("PIN could not be saved. Try again.")
                else id.walt.walletdemo.compose.logic.WalletAccessOperation.Idle,
            settingsNotice = if (state == "pin_changed") id.walt.walletdemo.compose.logic.WalletAccessNotice("PIN changed",
                id.walt.walletdemo.compose.logic.WalletAccessNotice.Kind.Success) else null)
        content {
            if (unlocking) id.walt.walletdemo.compose.ui.screens.PinScreen(controller, access)
            else Column {
                if (access.pinChange == null) WalletScreenHeader("Wallet access")
                id.walt.walletdemo.compose.ui.screens.WalletAccessSettingsScreen(controller, access)
            }
        }
        when (state) {
            "default", "pin_changed" -> onNodeWithTag(WalletUiTestTags.SettingsChangePin).assertIsDisplayed()
            "confirmation", "save_failure" -> onNodeWithTag(WalletUiTestTags.PinConfirmationInput).assertIsDisplayed()
            else -> onNodeWithTag(WalletUiTestTags.PinInput).assertIsDisplayed()
        }
        if (state == "save_failure") onNodeWithTag(WalletUiTestTags.PinSubmitButton).assertIsDisplayed().assertIsEnabled()
        if (state == "biometric_fallback") onNodeWithTag(WalletUiTestTags.PinBiometricButton).assertIsDisplayed()
        if (state == "biometric_unavailable") onNodeWithTag("wallet.biometricOpenSettings").assertIsDisplayed()
        capture(if (unlocking) "access.unlock.$state" else "settings.access.$state")
    }

    fun keySetup(page: String) = with(test) {
        val unavailable = page == "approval_unavailable"
        val setup = WalletVisualFixtures.keySetup
        val approval = setup.options.first().approval
        content {
            id.walt.walletdemo.compose.ui.screens.IdentitySetupScreen(if (unavailable) setup.copy(options = setup.options.filter { it.approval.id != approval.id }, preferredApproval = approval) else setup, null,
                onChoose = {}, onResume = {}, onCancel = {}, onRefresh = {},
                biometricAvailability = if (unavailable) DemoBiometricAvailability.Unavailable else DemoBiometricAvailability.Available,
                biometricKind = id.walt.walletdemo.compose.logic.DemoBiometricKind.FaceId)
        }
        if (page != "summary" && !unavailable) {
            onNodeWithTag("wallet.keySetupEdit.${page.replaceFirstChar { it.uppercase() }}").performClick()
            onNodeWithText("Done").assertIsDisplayed()
        } else {
            onNodeWithText("Create signing key").assertIsDisplayed()
            onNodeWithTag("wallet.keySetupEdit.Recovery").assertIsDisplayed()
            onNodeWithTag("wallet.keySetupEdit.Storage").assertIsDisplayed()
            onNodeWithTag("wallet.keySetupEdit.Approval").assertIsDisplayed()
        }
        if (unavailable) {
            onNodeWithTag(WalletUiTestTags.KeySetupContinue).assertIsNotEnabled()
            onNodeWithTag("wallet.biometricOpenSettings").assertIsDisplayed()
        }
        capture("onboarding.key.$page")
    }

    fun biometricSetup(unavailable: Boolean = false) = with(test) {
        content {
            id.walt.walletdemo.compose.ui.screens.BiometricSetupScreen(
                id.walt.walletdemo.compose.logic.WalletAuthState.BiometricSetup(
                    if (unavailable) id.walt.walletdemo.compose.logic.DemoBiometricResult.Unavailable
                    else id.walt.walletdemo.compose.logic.DemoBiometricResult.Cancelled),
                busy = false, availability = if (unavailable) DemoBiometricAvailability.Unavailable else DemoBiometricAvailability.Available, onRetry = {}, onContinue = {})
        }
        onNodeWithTag(WalletUiTestTags.BiometricSetupContinue).assertIsDisplayed().assertIsEnabled()
        if (unavailable) onAllNodesWithTag(WalletUiTestTags.BiometricSetupRetry).assertCountEquals(0)
        else onNodeWithTag(WalletUiTestTags.BiometricSetupRetry).assertIsDisplayed().assertIsEnabled()
        capture("onboarding.biometric.${if (unavailable) "unavailable" else "cancelled"}")
    }

    fun account(state: String) = with(test) {
        content {
            id.walt.walletdemo.compose.ui.screens.AccountAuthScreen(
                isBusy = state == "busy", error = if (state == "expired") "Your session has expired. Sign in again to continue." else null,
                onLogin = { _, _ -> }, onRegister = { _, _ -> })
        }
        onNodeWithTag(WalletUiTestTags.AccountEmailInput).assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.AccountPasswordInput).assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.AccountLoginButton).assertIsNotEnabled()
        onNodeWithTag(WalletUiTestTags.AccountRegisterButton).assertIsNotEnabled()
        if (state == "expired") onNodeWithTag(WalletUiTestTags.AccountAuthError).assertIsDisplayed()
        capture("account.$state")
    }

    fun readerTrustImport() = with(test) {
        content { ReaderTrustImportReview(WalletVisualFixtures.readerTrustImport, {}, {}) }
        onNodeWithTag(WalletUiTestTags.SettingsReaderTrustImportConfirm).assertIsDisplayed().assertIsEnabled()
        onNodeWithTag(WalletUiTestTags.SettingsReaderTrustImportCancel).assertIsDisplayed()
        onNodeWithText("Example Reader CA", substring = false).assertIsDisplayed()
        onNodeWithText("Jan 1, 2031", substring = true).performScrollToContent(this).assertIsDisplayed()
        onNodeWithText("Valid until", substring = false).assertIsDisplayed()
        capture("settings.reader.import_review")
    }

    fun settingsRoot(destination: String = "root", reviewEnabled: Boolean = true) = with(test) {
        val trust = id.walt.walletdemo.compose.logic.DemoReaderTrustSettingsController(
            id.walt.walletdemo.compose.logic.InMemoryDemoReaderTrustSettingsStore(
                id.walt.wallet2.mobile.ProximityReaderTrustSettings(if (destination == "reader_required")
                    id.walt.wallet2.mobile.ProximityReaderPolicy.RequireTrusted else id.walt.wallet2.mobile.ProximityReaderPolicy.AllowAnonymousOrUntrusted)),
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
            dispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
            workerDispatcher = kotlinx.coroutines.Dispatchers.Unconfined)
        content {
            SettingsScreen(
                state = WalletDemoUiState(showDcApiPresentationPreview = reviewEnabled),
                onShowDcApiPresentationPreviewChange = {}, onProximityTransportProfileChange = {},
                onBack = {}, onIdentityAction = {}, onRefreshIdentityDetails = {}, onLock = {}, onResetWallet = {},
                onRequestSigningProtectionChange = {}, onConfirmSigningProtectionChange = {},
                onCancelSigningProtectionChange = {}, onProximityApprovalModeChange = {},
                readerTrustSettingsContent = { DemoReaderTrustSettings(trust) },
                walletAccessContent = { Text("Wallet access") },
            )
        }
        onNodeWithTag(WalletUiTestTags.SettingsSigningKey).assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.SettingsDigitalCredentialsApi).assertIsDisplayed()
        when (destination) {
            "credential_status", "credential_status_help" -> {
                onNodeWithTag("settings-credential-status").performClick()
                if (destination == "credential_status_help") onNodeWithTag("settings-credential-status-how").performClick()
            }
            "dc_api" -> {
                onNodeWithTag(WalletUiTestTags.SettingsDigitalCredentialsApi).performClick()
                val toggle = onNodeWithTag(WalletUiTestTags.SettingsShowDcApiPreview)
                if (reviewEnabled) toggle.assertIsOn() else toggle.assertIsOff()
            }
            "nearby", "connection", "reader", "reader_required" -> {
                onNodeWithTag(WalletUiTestTags.SettingsProximityPresentation).performClick()
                if (destination == "connection") onNodeWithTag(WalletUiTestTags.SettingsConnectionMethod).performClick()
                if (destination.startsWith("reader")) onNodeWithTag(WalletUiTestTags.SettingsReaderAuthentication).performClick()
            }
            "reset_confirmation" -> {
                onNodeWithTag(WalletUiTestTags.SettingsReset).performScrollTo().performClick()
                onNodeWithTag(WalletUiTestTags.SettingsResetConfirm).assertIsDisplayed().assertIsEnabled()
            }
            "technical" -> onNodeWithTag(WalletUiTestTags.SettingsTechnicalDetails).performClick()
        }
        val stateId = when (destination) {
            "dc_api" -> if (reviewEnabled) "enabled" else "disabled"
            "technical" -> "unavailable"
            else -> "default"
        }
        if (destination == "reset_confirmation") {
            capture("settings.reset.confirmation")
            onNodeWithText("Cancel").performClick()
            onAllNodesWithTag(WalletUiTestTags.SettingsResetConfirm).assertCountEquals(0)
        } else capture("settings.$destination.$stateId")
    }

    fun walletHome(empty: Boolean = false) = with(test) {
        val ready = WalletVisualFixtures.partialResult.session as WalletSessionState.Ready
        val state = WalletVisualFixtures.partialResult.copy(
            selectedTab = WalletDemoTab.Credentials, operation = WalletOperationState.Idle,
            session = ready.copy(credentials = if (empty) emptyList() else ready.credentials),
        )
        content {
            Column(Modifier.fillMaxSize()) {
                WalletHeader(state, onSettings = {}, onScan = {}, onShareNearby = {})
                CredentialsTab(state.session, modifier = Modifier.weight(1f))
                WalletFooter(feedback = { StatusCard(state, {}, {}) })
            }
        }
        if (empty) onNodeWithTag(WalletUiTestTags.CredentialsEmpty).assertIsDisplayed()
        else {
            waitUntil { onAllNodesWithTag(WalletUiTestTags.credentialCard(ready.credentials.single().id)).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag(WalletUiTestTags.credentialCard(ready.credentials.single().id)).assertIsDisplayed()
        }
        onNodeWithTag(WalletUiTestTags.ScanButton).assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.ProximityStartButton).assertIsDisplayed()
        capture(if (empty) "wallet.home.empty" else "wallet.home.credential")
    }

    fun scanner(state: String, fidoHandoffSupported: Boolean = false) = with(test) {
        val input = when (state) { "link" -> "https://example.test/request"; "fido" -> "FIDO:/0123456789"; else -> "" }
        content { WalletScanScreen(onBack = {}, onOpen = { _, _ -> }, initialInput = input) }
        if (state == "link") {
            onNodeWithTag(WalletUiTestTags.ScanContinue).assertIsDisplayed().assertIsEnabled()
            onNodeWithText("Receive credentials").assertDoesNotExist()
            onNodeWithText("Share credentials").assertDoesNotExist()
        } else if (state == "empty") {
            onAllNodesWithTag(WalletUiTestTags.ScanInput).assertCountEquals(0)
            onAllNodesWithTag(WalletUiTestTags.ScanContinue).assertCountEquals(0)
            onNodeWithTag("wallet.scanMode").assertIsDisplayed().assertIsEnabled()
        } else if (fidoHandoffSupported) onNodeWithTag(WalletUiTestTags.ScanContinue).assertIsDisplayed().assertIsEnabled()
        else onNodeWithTag(WalletUiTestTags.ScanContinue).assertIsDisplayed().assertIsNotEnabled()
        capture("wallet.scan.$state")
    }

    fun singleOffer() = with(test) {
        val credential = WalletVisualFixtures.offer.offeredCredentials.first()
        val state = WalletDemoUiState(
            offerPreview = WalletVisualFixtures.offer.copy(offeredCredentials = listOf(credential), batchSize = null),
            issuanceCopyCounts = mapOf(credential.configurationId to 1))
        content {
            ReceiveTab(state, state.requestDrafts, onTxCodeChange = {},
                onCopiesChange = { _, _ -> }, onPreviewOffer = {}, onAcceptOffer = {},
                onDeclineOffer = {}, onResumeDeferred = {}, onDone = {}, onRefresh = {})
        }
        onNodeWithTag("issuance-select-${credential.configurationId}").assertDoesNotExist()
        onNodeWithTag("issuance-copies-${credential.configurationId}").assertDoesNotExist()
        onNodeWithTag("issuance-details-${credential.configurationId}").assertIsDisplayed().assertHasClickAction()
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            .assertContentDescriptionEquals("Credential information")
        onNodeWithTag("issuance-identity-${credential.configurationId}", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag(WalletUiTestTags.OfferAcceptButton).assertIsDisplayed().assertIsEnabled()
        capture("batch.offer.single_full_art")
    }

    fun credentialDetails() = with(test) {
        val details = WalletVisualFixtures.credentialDetails
        content {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                CredentialDetailsContent(details)
            }
        }
        onNodeWithTag(WalletUiTestTags.credentialDetails(details.summary.id)).assertExists()
        onNodeWithText("Ada").performScrollTo().assertIsDisplayed()
        onNodeWithText("Vienna").performScrollTo().assertIsDisplayed()
        onNodeWithText("Ada").assertIsDisplayed()
        capture("credential.details.identity")
    }

    fun localizedCredentialDetails() = with(test) {
        content {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                CredentialDetailsContent(WalletVisualFixtures.localizedCredentialDetails)
            }
        }
        onNodeWithText("Familienname").assertIsDisplayed()
        onNodeWithText("Vorname").assertIsDisplayed()
        onNodeWithText("Name im Namensraum").assertIsDisplayed()
        onNodeWithText("Straße").assertIsDisplayed()
        capture("credential.details.localized_metadata")
    }

    fun batchOffer(noneSelected: Boolean = false, compact: Boolean = false) = with(test) {
        val copies = WalletVisualFixtures.copies.mapValues { (_, count) -> if (noneSelected) 0 else count }
        val state = WalletDemoUiState(offerPreview = WalletVisualFixtures.offer, issuanceCopyCounts = copies)
        content {
            ReceiveTab(
                state = state, requestDrafts = state.requestDrafts,
                onTxCodeChange = {}, onCopiesChange = { _, _ -> },
                onPreviewOffer = {}, onAcceptOffer = {}, onDeclineOffer = {}, onResumeDeferred = {}, onDone = {}, onRefresh = {},
            )
        }
        onNodeWithTag(WalletUiTestTags.OfferIssuerSection).assertIsDisplayed()
        val action = onNodeWithTag(WalletUiTestTags.OfferAcceptButton).assertIsDisplayed()
        val primaryBounds = action.getUnclippedBoundsInRoot()
        val secondaryBounds = onNodeWithTag(WalletUiTestTags.OfferDeclineButton).getUnclippedBoundsInRoot()
        val rootBounds = onRoot().getUnclippedBoundsInRoot()
        val availableWidth = rootBounds.right - rootBounds.left - 40.dp
        val requiredWidth = primaryBounds.right - primaryBounds.left + secondaryBounds.right - secondaryBounds.left + 8.dp
        if (requiredWidth <= availableWidth) {
            assertEquals(secondaryBounds.top, primaryBounds.top,
                "Actions that fit must share one row, including at large text sizes")
        } else {
            kotlin.test.assertTrue(primaryBounds.top >= secondaryBounds.bottom,
                "Actions that need more space must remain separately reachable")
        }
        if (noneSelected) action.assertIsNotEnabled() else action.assertIsEnabled()
        if (compact) {
            onNodeWithTag("issuance-select-resident-card").assertIsDisplayed()
            capture("batch.offer.compact_dark_large_text")
            onNodeWithText("2 of 2 credential types selected").performScrollTo().assertIsDisplayed()
            onNodeWithTag("issuance-more-library-card").assertIsDisplayed()
            action.assertIsDisplayed().assertIsEnabled()
            capture("batch.offer.compact_dark_large_text.last_target")
            return@with
        }
        val scenario = if (noneSelected) "none_selected" else "two_targets_three_copies"
        onNodeWithTag("issuance-select-library-card").assertIsDisplayed()
        if (!noneSelected) onNodeWithText("Copies: 1").assertIsDisplayed()
        capture("batch.offer.$scenario")
    }

    fun offerDefinitions() = with(test) {
        val offer = WalletVisualFixtures.offer
        content {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)) {
                OfferedCredentialDetails(offer.offeredCredentials.first(), offer.issuer.display!!.name!!, offer.issuer.credentialIssuer)
            }
        }
        onNodeWithText("Values have not been received yet.").assertIsDisplayed()
        onNodeWithText("Given name").assertIsDisplayed()
        onNodeWithText("Family name").assertIsDisplayed()
        onNodeWithText("Contact email").assertIsDisplayed()
        onAllNodesWithText("Always included").assertCountEquals(1)
        onAllNodesWithText("Optional").assertCountEquals(1)
        onNodeWithText("Ada").assertDoesNotExist()
        capture("batch.offer.definitions")
    }

    fun credentialImages() = with(test) {
        val images = WalletVisualImages()
        val details = WalletVisualFixtures.credentialWithImages
        images.install()
        try {
            content {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                    CredentialDetailsContent(details)
                }
            }
            listOf("portrait" to "portrait", "signature_usual_mark" to "signature").forEach { (path, image) ->
                waitUntil { onAllNodesWithTag(WalletUiTestTags.claimImage(path)).fetchSemanticsNodes().isNotEmpty() }
                onNodeWithTag(WalletUiTestTags.claimImage(path)).performScrollTo().assertIsDisplayed()
                waitUntil { images.isReady(image) }
                // Both thumbnails are visible together; neither may be captured while still loading.
                waitUntil {
                    listOf("portrait", "signature_usual_mark").all { visiblePath ->
                        onAllNodes(hasTestTag(WalletUiTestTags.claimImage(visiblePath)) and isEnabled())
                            .fetchSemanticsNodes().isNotEmpty()
                    }
                }
                waitForIdle()
            }
            onNodeWithTag(WalletUiTestTags.claimImage("portrait")).assertIsDisplayed()
            onNodeWithTag(WalletUiTestTags.claimImage("signature_usual_mark")).assertIsDisplayed()
            capture("credential.media.loaded")
        } finally {
            images.close()
        }
    }

    fun partialBatchResult(status: WalletDemoContinuationStatus = WalletDemoContinuationStatus.AwaitingIssuer,
                           failure: Boolean = false, missingDetails: Boolean = false) = with(test) {
        val base = WalletVisualFixtures.partialResult
        val state = base.copy(
            lastReceivedCredentialIds = base.lastReceivedCredentialIds + if (missingDetails) listOf("missing-credential") else emptyList(),
            deferredCredentials = if (failure || missingDetails) emptyList() else base.deferredCredentials.map { it.copy(status = status) },
            issuanceReceipt = base.issuanceReceipt?.let { if (missingDetails) it.copy(pendingIds = emptySet()) else if (failure) it.copy(pendingIds = emptySet(),
                problem = WalletDemoIssuanceProblem("The issuer could not finish this request.", failedTargetCount = 1, notAttemptedTargetCount = 2)) else it },
            operation = if (missingDetails) WalletOperationState.Succeeded("Received 2 credential(s)", WalletDemoTab.Receive)
                else if (failure) WalletOperationState.Failed("The issuer could not finish this request.", WalletDemoTab.Receive) else base.operation,
        )
        content {
            Column(Modifier.fillMaxSize()) {
                WalletHeader(state, onSettings = null, onClose = {}, title = "Receiving result")
                ReceiveTab(state, state.requestDrafts, onTxCodeChange = {},
                    onCopiesChange = { _, _ -> }, onPreviewOffer = {}, onAcceptOffer = {}, onDeclineOffer = {},
                    onResumeDeferred = {}, onDone = {}, onRefresh = {}, modifier = Modifier.weight(1f),
                    feedback = { StatusCard(state, {}, {}) })
            }
        }
        if (missingDetails) {
            onNodeWithTag("issuance-missing-details").assertIsDisplayed()
            onNodeWithText("Saved · 2").assertIsDisplayed()
            onNodeWithTag("issuance-refresh").assertIsDisplayed().assertIsEnabled()
        } else if (failure) {
            onNodeWithText("Failed targets: 1").performScrollTo().assertIsDisplayed()
            onNodeWithText("Not attempted: 2").assertIsDisplayed()
        } else if (status.canResume) {
            onNodeWithText(if (status == WalletDemoContinuationStatus.AwaitingLocalSave) "Finish saving" else "Check with issuer")
                .performScrollTo().assertIsDisplayed()
        } else {
            onNodeWithTag("issuance-resume-visual-deferred-library").assertDoesNotExist()
            onNodeWithTag("issuance-refresh").assertIsDisplayed()
        }
        onNodeWithTag("wallet.offerInput").assertDoesNotExist()
        onNodeWithTag("issuance-saved-${WalletVisualFixtures.credentialSummary.id}").assertExists()
        val id = if (missingDetails) "missing_details" else if (failure) "partial_failure" else when (status) {
            WalletDemoContinuationStatus.AwaitingLocalSave -> "local_save_pending"
            WalletDemoContinuationStatus.RemoteOutcomeUncertain -> "remote_uncertain"
            WalletDemoContinuationStatus.StorageOutcomeUncertain -> "storage_uncertain"
            else -> "saved_and_deferred"
        }
        capture("batch.result.$id")
    }

    fun nearbyState(kind: String) = with(test) {
        val fixtures = WalletVisualProximityFixtures
        val state = fixtures.state(kind)
        content { nearbyVisualHost {
            if (kind == "receipt") ReviewScaffold(actions = {
                ProximityOutcomeActions(state, { it }, {}, {}, {}, {})
            }) {
                ProximityTerminalContent("Presentation complete", "The approved credential data was sent to the reader.") {
                    ProximitySharingReceiptContent(fixtures.review, fixtures.submission,
                        id.walt.wallet2.mobile.ProximityApprovalTiming.BeforeConnection, fixtures.completedAt, fixtures.details)
                }
            } else WalletDemoProximityScreen(state, fixtures.details,
                WalletDemoProximityHostActionExecutor { ProximityHostActionResult.Completed },
                onSelectCredential = { _, _ -> }, onToggleElement = { _, _ -> }, onContinueAfterResponseChange = {},
                onApprove = { _, _ -> }, onDecline = {}, onRetry = {}, onRemediate = { _, _ -> },
                onCancel = {}, onDismiss = {}, onRestart = {}, headerOwnsClose = true)
        } }
        when (kind) {
            "permission" -> onNode(hasText("Allow Bluetooth access") and hasClickAction()).assertIsDisplayed()
            "expired" -> { onNodeWithTag(WalletUiTestTags.ProximityRetry).assertIsDisplayed(); onNodeWithTag(WalletUiTestTags.ProximityDone).assertIsDisplayed() }
            "receipt" -> { onNodeWithTag(WalletUiTestTags.ProximityDone).assertIsDisplayed(); onNodeWithText("City service desk").assertExists() }
            "review" -> onNodeWithTag(WalletUiTestTags.ProximityReview).assertExists()
        }
        capture("nearby.$kind")
        if (kind == "review") {
            onNodeWithTag(WalletUiTestTags.proximityElement(0, "org.iso.18013.5.1", "given_name")).performScrollTo().assertIsDisplayed()
            onNodeWithText("Reader intends to retain this data").performScrollToContent(this).assertIsDisplayed()
            onNodeWithText("Alex").assertIsDisplayed()
            capture("nearby.review.disclosures")
        }
    }

    fun nearbyReady() = with(test) {
        val method = mutableStateOf(id.walt.wallet2.mobile.ProximityEngagementMethod.Qr)
        content { CompositionLocalProvider(LocalWalletVisualPreferences provides WalletVisualPreferences(reduceMotion = true)) {
            nearbyVisualHost {
            WalletDemoProximityScreen(
                state = WalletDemoProximityUiState(active = true, preferredEngagement = method.value,
                    sessionState = ProximityState.EngagementReady(
                        listOf(ProximityEngagement.Qr(WalletVisualFixtures.nearbyQrPayload), ProximityEngagement.Nfc))),
                credentialDetailsById = emptyMap(),
                hostActions = WalletDemoProximityHostActionExecutor { ProximityHostActionResult.Completed },
                onSelectCredential = { _, _ -> }, onToggleElement = { _, _ -> }, onContinueAfterResponseChange = {},
                onApprove = { _, _ -> }, onDecline = {}, onRetry = {}, onRemediate = { _, _ -> },
                onCancel = {}, onDismiss = {}, onRestart = {}, onConnectionOptions = {}, headerOwnsClose = true,
            )
        } } }
        onNodeWithTag(WalletUiTestTags.ProximityQr).assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.ProximityCancel).assertIsDisplayed()
        capture("nearby.ready.qr")
        runOnIdle { method.value = id.walt.wallet2.mobile.ProximityEngagementMethod.Nfc }
        onNodeWithText("Hold near the reader").assertIsDisplayed()
        onNodeWithText("Show QR code instead").assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.ProximityQr).assertDoesNotExist()
        capture("nearby.ready.nfc")
    }

    @Composable
    private fun nearbyVisualHost(content: @Composable () -> Unit) {
        Column(Modifier.fillMaxSize()) {
            WalletScreenHeader("Share nearby") {
                id.walt.walletdemo.compose.ui.components.WalletHeaderButton({}, modifier = Modifier.testTag(WalletUiTestTags.ProximityCancel)) {
                    WalletIcon(WalletSymbol.Decline, "Close nearby sharing")
                }
            }
            Box(Modifier.weight(1f)) { content() }
        }
    }

    fun providerSharingReview(compact: Boolean = false) = with(test) {
        content {
            WalletDemoSharingReviewScreen(review = WalletVisualFixtures.providerReview, title = "Share documents",
                onSubmit = {}, onCancel = {}, onBackAtRoot = {})
        }
        onNodeWithTag(WalletUiTestTags.PresentationSubmitButton).assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.PresentationVerifierSection).performScrollTo().assertIsDisplayed()
        capture(if (compact) "sharing.provider.compact_dark_large_text" else "sharing.provider.review")
    }

    fun sharingInformation(alternative: Boolean = false, multiple: Boolean = false, dates: Boolean = false) = with(test) {
        val options = if (dates) WalletVisualFixtures.dateCredentials else WalletVisualFixtures.informationCredentials.take(if (multiple) 3 else 2)
        val review = id.walt.walletdemo.compose.logic.WalletDemoSharingReview(
            id.walt.walletdemo.compose.logic.WalletDemoSharingRequest(
                id.walt.walletdemo.compose.logic.WalletDemoSharingRequester(
                    fallbackName = "https://verifier.example", verifiedOrigin = "https://verifier.example")), options,
            options.map { it.queryId }.distinct().map {
                id.walt.walletdemo.compose.logic.WalletDemoPresentationCredentialRequirement(listOf(listOf(it)))
            })
        content {
            WalletDemoSharingReviewScreen(review = review, title = "Share credentials", compact = false,
                onSubmit = {}, onCancel = {}, onBackAtRoot = {})
        }
        if (alternative) onNodeWithTag(WalletUiTestTags.presentationCredentialToggle(options[1].selection.id))
            .performScrollTo().performClick()
        options.mapNotNull { it.issuer }.distinct().forEach { issuer ->
            onAllNodesWithText(issuer).assertCountEquals(1)
        }
        val heading = if (dates) "PID (SD-JWT VC)" else if (alternative) "City ID" else "National ID"
        onAllNodes(hasText(heading) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertCountEquals(1)
        if (dates) onNodeWithText("Oct 30, 2026", substring = true).assertIsDisplayed()
        onNodeWithText(if (dates) "Jan 28, 2027" else if (multiple) "MEM-1234" else if (alternative) "Lovelace" else "Ada", substring = dates)
            .performScrollToContent(this).assertIsDisplayed()
        if (alternative) onNodeWithText("Always included by this credential").assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.PresentationSubmitButton).assertIsDisplayed().assertIsEnabled()
        capture("sharing.information." + if (dates) "dates" else if (multiple) "multiple" else if (alternative) "alternative" else "selected")
    }

    fun providerOfferReview() = with(test) {
        content {
            WalletDemoOfferCreateScreen(WalletDemoOfferCreateUiState.Review(WalletVisualFixtures.offer),
                onAccept = { _, _ -> }, onDecline = {}, onDismiss = {}, onCancelAuthorization = {},
                )
        }
        onNodeWithTag(WalletUiTestTags.OfferAcceptButton).assertIsEnabled().assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.OfferCredentialsSection).performScrollTo().assertIsDisplayed()
        capture("receiving.provider.review")
    }

    fun providerReceivingState(kind: String) = with(test) {
        val base = WalletVisualFixtures.partialResult
        val state = when (kind) {
            "preparing" -> WalletDemoOfferCreateUiState.Loading
            "authorization" -> WalletDemoOfferCreateUiState.WaitingForAuthorization()
            "failure" -> WalletDemoOfferCreateUiState.Failure("The credential offer could not be verified.")
            "partial_result" -> WalletDemoOfferCreateUiState.Receipt(
                receipt = requireNotNull(base.issuanceReceipt).copy(problem = WalletDemoIssuanceProblem(
                    "The issuer could not finish this request.", failedTargetCount = 1, notAttemptedTargetCount = 2)),
                saved = (base.session as WalletSessionState.Ready).credentials, pending = base.deferredCredentials,
                receivedIds = base.lastReceivedCredentialIds.toSet())
            else -> error("Unknown receiving state: $kind")
        }
        content {
            WalletDemoOfferCreateScreen(state, onAccept = { _, _ -> }, onDecline = {}, onDismiss = {},
                onCancelAuthorization = {})
        }
        when (state) {
            is WalletDemoOfferCreateUiState.Receipt -> {
                onNodeWithTag("wallet.provider.done").assertIsDisplayed().assertIsEnabled()
                onNodeWithTag("issuance-saved-${WalletVisualFixtures.credentialSummary.id}").assertIsDisplayed()
                onNodeWithText("Check with issuer").assertIsDisplayed()
                onNodeWithText("Not attempted: 2").performScrollTo().assertIsDisplayed()
                onNodeWithTag("wallet.provider.done").assertIsDisplayed()
            }
            else -> onNodeWithContentDescription("Close request").assertIsDisplayed()
        }
        capture("receiving.provider.$kind")
    }

    fun providerSharingStatus(failure: Boolean = false) = with(test) {
        content {
            WalletProviderStatusScreen(title = if (failure) "Unable to share" else "Preparing request…",
                message = if (failure) "The request could not be verified." else null, onClose = {}, onDismiss = {})
        }
        onNodeWithContentDescription("Close request").assertIsDisplayed()
        capture(if (failure) "sharing.provider.failure" else "sharing.provider.preparing")
    }

    fun paymentState(blocked: Boolean) = with(test) {
        content {
            WalletDemoSharingReviewScreen(review = WalletVisualFixtures.paymentReview, title = "Payment",
                onSubmit = {}, onCancel = {}, compact = false, preparePaymentConsent = {
                    if (blocked) error("Required issuer payment labels are missing.")
                    else kotlinx.coroutines.awaitCancellation()
                })
        }
        onNodeWithTag(if (blocked) "payment-consent-blocked" else "payment-consent-loading").performScrollTo().assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.PresentationSubmitButton).assertIsNotEnabled().assertIsDisplayed()
        capture(if (blocked) "payment.blocked" else "payment.loading")
    }

    fun localizedPayment() = with(test) {
        content {
            WalletDemoSharingReviewScreen(review = WalletVisualFixtures.paymentReview, title = "Payment",
                onSubmit = {}, onCancel = {}, compact = false,
                preparePaymentConsent = { WalletVisualFixtures.localizedPayment })
        }
        onNodeWithText("EUR").performScrollToContent(this).assertIsDisplayed()
        onNodeWithText("11.56").assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.PresentationSubmitButton).assertIsDisplayed().assertIsEnabled()
        onNodeWithTag("payment-unsigned-warning").assertDoesNotExist()
        capture("payment.localized.compact_large_text")
    }

    fun paymentTerms(recurring: Boolean, compact: Boolean = false, details: Boolean = false) = with(test) {
        val consent = WalletVisualFixtures.paymentTerms(recurring)
        var cancelled = false
        var submitted = false
        content {
            WalletDemoSharingReviewScreen(review = WalletVisualFixtures.paymentReview, title = "Payment",
                onSubmit = { submitted = true }, onCancel = { cancelled = true }, compact = false,
                preparePaymentConsent = { consent })
        }
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag("payment-payee-logo").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("Payee logo").assertDoesNotExist()
        onNodeWithText("Currency").assertDoesNotExist()
        onNodeWithTag(WalletUiTestTags.PresentationSubmitButton).assertIsDisplayed().assertIsEnabled()
        onNodeWithText(if (recurring) "Monthly" else "Execution date and time").assertExists()
        if (!recurring) {
            onNodeWithText("2099-01-01T12:00:00Z").assertDoesNotExist()
            onNodeWithText("(UTC)", substring = true).assertExists()
        }
        onNodeWithText("Requested credentials").assertExists()
        onNodeWithText("https://verifier.example").assertExists()
        consent.fields.filter { it.path != listOf("payee", "logo") && it.path != listOf("currency") }.forEach { field ->
            when (field.placement) {
                WalletDemoPaymentFieldPlacement.Prominent, WalletDemoPaymentFieldPlacement.Main ->
                    onNodeWithText(field.label).assertExists("Main payment field must not require expansion: ${field.label}")
                WalletDemoPaymentFieldPlacement.Details, WalletDemoPaymentFieldPlacement.Omitted ->
                    onNodeWithText(field.label).assertDoesNotExist()
            }
        }
        if (recurring) onNodeWithText("Payment terms").assertExists()
        if (compact) {
            onNodeWithText("Payment terms").performScrollToContent(this, alignTop = true).assertIsDisplayed()
        }
        if (compact) onNodeWithText("Monthly").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { layout ->
            val results = mutableListOf<TextLayoutResult>()
            kotlin.test.assertTrue(layout(results))
            assertEquals(1.5f, results.single().layoutInput.density.fontScale)
        }
        val id = if (recurring) "payment.recurring" else "payment.scheduled"
        if (!details) capture(if (compact) "$id.compact_large_text" else "$id.main")
        if (details || compact) {
            onNodeWithTag("payment-payee-details-toggle").performScrollToContent(this).performClick()
            onNodeWithText("https://shop.example ↗").performScrollToContent(this).assertIsDisplayed()
            if (recurring) onNodeWithText("0.1234567890123456789").performScrollToContent(this).assertIsDisplayed()
            onNodeWithTag("payment-details-toggle").performScrollToContent(this).performClick()
            onNodeWithText("Example Payments Ltd").performScrollToContent(this).assertIsDisplayed()
            if (details) {
                onNodeWithTag("payment-details-toggle").performScrollToContent(this, alignTop = true).assertIsDisplayed()
                capture("$id.details")
            }
            consent.fields.forEach { field ->
                when (field.path) {
                    listOf("payee", "logo") -> onNodeWithTag("payment-payee-logo").performScrollToContent(this).assertIsDisplayed()
                    listOf("currency") -> onNodeWithText("EUR").performScrollToContent(this).assertIsDisplayed()
                    else -> onNodeWithText(field.label).assertExists("Payment field is missing: ${field.label}")
                        .performScrollToContent(this).assertIsDisplayed()
                }
            }
        }
        onNodeWithText("Cancel payment").assertIsDisplayed().performClick()
        kotlin.test.assertTrue(cancelled)
        kotlin.test.assertFalse(submitted)
    }

    fun paymentReview() = with(test) {
        val consent = WalletVisualFixtures.payment
        content {
            WalletDemoSharingReviewScreen(
                review = WalletVisualFixtures.paymentReview,
                title = "Payment", compact = false, onSubmit = {}, onCancel = {}, onBackAtRoot = {},
                preparePaymentConsent = { consent },
            )
        }
        onNodeWithText("Confirm payment").performScrollTo().assertIsDisplayed()
        onNodeWithText("11.56").performScrollTo().assertIsDisplayed()
        onNodeWithText("Pay €11.56").assertIsDisplayed()
        onNodeWithText("Technical reference").assertDoesNotExist()
        capture("payment.mixed_credentials.main")
        onNodeWithTag("payment-details-toggle").performScrollTo().performClick()
        onNodeWithText("example-transaction-001").performScrollTo().assertIsDisplayed()
        capture("payment.mixed_credentials.details")
        onNodeWithText("Example account").performScrollToContent(this).assertIsDisplayed()
        onNodeWithText("Pay €11.56").assertIsEnabled().assertIsDisplayed()
        capture("payment.mixed_credentials.requested_data")
        onNodeWithText("Pay €11.56").performClick()
        onNodeWithTag("payment-unsigned-confirm").assertIsDisplayed()
        capture("payment.mixed_credentials.unsigned_confirmation")
    }
}
