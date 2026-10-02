package id.walt.walletdemo.compose.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.WalletDemoUiState
import id.walt.walletdemo.compose.logic.WalletDemoContinuationStatus
import id.walt.walletdemo.compose.logic.WalletDemoIssuanceProblem
import id.walt.walletdemo.compose.logic.WalletDemoProximityUiState
import id.walt.walletdemo.compose.logic.WalletDemoProximityHostActionExecutor
import id.walt.wallet2.mobile.ProximityEngagement
import id.walt.wallet2.mobile.ProximityHostActionResult
import id.walt.wallet2.mobile.ProximityState
import id.walt.walletdemo.compose.ui.components.CredentialDetailsContent
import id.walt.walletdemo.compose.ui.components.OfferedCredentialDetails
import id.walt.walletdemo.compose.ui.screens.ReceiveTab
import id.walt.walletdemo.compose.ui.screens.SettingsScreen
import id.walt.walletdemo.compose.ui.screens.WalletHeader
import id.walt.walletdemo.compose.ui.screens.WalletScanScreen
import id.walt.walletdemo.compose.ui.screens.CredentialsTab
import id.walt.walletdemo.compose.logic.WalletSessionState
import id.walt.walletdemo.compose.logic.WalletDemoTab
import id.walt.walletdemo.compose.logic.WalletOperationState

/** Content and readiness are shared; the platform adapter owns the renderer and baseline path. */
@OptIn(ExperimentalTestApi::class)
internal class WalletVisualScenarios(
    private val test: ComposeUiTest,
    private val captureImage: (String) -> Unit,
    private val platformTheme: @Composable (@Composable () -> Unit) -> Unit = { it() },
) {
    private fun capture(id: String) {
        // Capture the settled Compose state after interactions and navigation.
        // Advancing virtual time keeps this independent of host rendering speed.
        test.mainClock.advanceTimeBy(1_000)
        test.waitForIdle()
        captureImage(id)
    }

    private fun content(body: @Composable () -> Unit) = test.setContent {
        platformTheme {
            WalletDemoTheme {
                Surface(modifier = Modifier.fillMaxSize(), content = body)
            }
        }
    }

    fun pin(state: String) = with(test) {
        val biometrics = object : id.walt.walletdemo.compose.logic.DemoBiometricAuthenticator {
            override fun isAvailable() = state == "biometrics_enabled"
            override suspend fun authenticate(reason: String) = id.walt.walletdemo.compose.logic.DemoBiometricResult.Succeeded
        }
        val controller = id.walt.walletdemo.compose.logic.WalletDemoController(
            WalletUiTestWallet(), id.walt.walletdemo.compose.logic.InMemoryDemoPinStore(), biometrics)
        if (state != "setup") {
            controller.updatePin("1234")
            controller.updatePinConfirmation(if (state == "mismatch") "4321" else "1234")
        }
        if (state == "mismatch") controller.submitPin()
        if (state == "biometrics_enabled") {
            controller.updateUseBiometrics(true)
            waitUntil { !controller.state.value.isAuthenticating }
        }
        val auth = controller.state.value.auth as id.walt.walletdemo.compose.logic.WalletAuthState.Setup
        content { id.walt.walletdemo.compose.ui.screens.PinScreen(controller, auth, false, biometrics.isAvailable()) }
        onNodeWithTag(WalletUiTestTags.PinInput).assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.PinConfirmationInput).assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.PinBiometricToggle).assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.PinSubmitButton).assertIsDisplayed()
        when (state) {
            "setup" -> onNodeWithTag(WalletUiTestTags.PinSubmitButton).assertIsNotEnabled()
            "mismatch" -> onNodeWithText("PIN confirmation does not match").assertIsDisplayed()
            "biometrics_enabled" -> onNodeWithTag(WalletUiTestTags.PinBiometricToggle).assertIsOn()
            else -> error("Unknown PIN fixture: $state")
        }
        capture("onboarding.pin.$state")
    }

    fun keySetup(page: String) = with(test) {
        content {
            id.walt.walletdemo.compose.ui.screens.IdentitySetupScreen(WalletVisualFixtures.keySetup, null,
                onChoose = {}, onResume = {}, onCancel = {}, onRefresh = {})
        }
        if (page != "summary") {
            onNodeWithTag("wallet.keySetupEdit.${page.replaceFirstChar { it.uppercase() }}").performClick()
            onNodeWithText("Done").assertIsDisplayed()
        } else {
            onNodeWithText("Create signing key").assertIsDisplayed()
            onNodeWithTag("wallet.keySetupEdit.Recovery").assertIsDisplayed()
            onNodeWithTag("wallet.keySetupEdit.Storage").assertIsDisplayed()
            onNodeWithTag("wallet.keySetupEdit.Approval").assertIsDisplayed()
        }
        capture("onboarding.key.$page")
    }

    fun settingsRoot() = with(test) {
        content {
            SettingsScreen(
                state = WalletDemoUiState(),
                onShowDcApiPresentationPreviewChange = {}, onProximityTransportProfileChange = {},
                onBack = {}, onIdentityAction = {}, onRefreshIdentityDetails = {}, onLock = {}, onResetWallet = {},
                onRequestSigningProtectionChange = {}, onConfirmSigningProtectionChange = {},
                onCancelSigningProtectionChange = {},
            )
        }
        onNodeWithTag(WalletUiTestTags.SettingsSigningKey).assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.SettingsDigitalCredentialsApi).assertIsDisplayed()
        capture("settings.root.default")
    }

    fun walletHome(empty: Boolean = false) = with(test) {
        val ready = WalletVisualFixtures.partialResult.session as WalletSessionState.Ready
        val state = WalletVisualFixtures.partialResult.copy(
            selectedTab = WalletDemoTab.Credentials, operation = WalletOperationState.Idle,
            session = ready.copy(credentials = if (empty) emptyList() else ready.credentials),
        )
        content {
            Column(Modifier.fillMaxSize()) {
                WalletHeader(state, onSettings = {}, onDismissStatus = {}, onToggleStatusExpanded = {}, onScan = {}, onShareNearby = {})
                CredentialsTab(state.session, modifier = Modifier.weight(1f))
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

    fun scanner(state: String) = with(test) {
        val input = when (state) { "link" -> "https://example.test/request"; "unsupported" -> "FIDO:/0123456789"; else -> "" }
        content { WalletScanScreen(onBack = {}, onOpen = { _, _ -> }, initialInput = input) }
        if (state == "link") {
            onNodeWithTag(WalletUiTestTags.ScanContinue).assertIsDisplayed().assertIsEnabled()
            onNodeWithText("Receive credentials").assertDoesNotExist()
            onNodeWithText("Share credentials").assertDoesNotExist()
        } else onNodeWithTag(WalletUiTestTags.ScanContinue).assertIsDisplayed().assertIsNotEnabled()
        capture("wallet.scan.$state")
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
        capture("credential.details.identity")
        onNodeWithText("Vienna").performScrollTo().assertIsDisplayed()
        capture("credential.details.nested")
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
                onOfferUrlChange = {}, onTxCodeChange = {}, onCopiesChange = { _, _ -> },
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
            kotlin.test.assertEquals(secondaryBounds.top, primaryBounds.top,
                "Actions that fit must share one row, including at large text sizes")
        } else {
            kotlin.test.assertTrue(primaryBounds.top >= secondaryBounds.bottom,
                "Actions that need more space must remain separately reachable")
        }
        if (noneSelected) action.assertIsNotEnabled() else action.assertIsEnabled()
        if (compact) {
            onNodeWithTag("issuance-select-resident-card").assertIsDisplayed()
            capture("batch.offer.compact_dark_large_text")
            onNodeWithText("Selected: 2 · Copies: 3").performScrollTo().assertIsDisplayed()
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
                capture("credential.media.$image")
            }
        } finally {
            images.close()
        }
    }

    fun partialBatchResult(status: WalletDemoContinuationStatus = WalletDemoContinuationStatus.AwaitingIssuer, failure: Boolean = false) = with(test) {
        val base = WalletVisualFixtures.partialResult
        val state = base.copy(
            deferredCredentials = if (failure) emptyList() else base.deferredCredentials.map { it.copy(status = status) },
            issuanceReceipt = base.issuanceReceipt?.let { if (failure) it.copy(pendingIds = emptySet(),
                problem = WalletDemoIssuanceProblem("The issuer could not finish this request.", failedTargetCount = 1, notAttemptedTargetCount = 2)) else it },
            operation = if (failure) WalletOperationState.Failed("The issuer could not finish this request.", WalletDemoTab.Receive) else base.operation,
        )
        content {
            Column(Modifier.fillMaxSize()) {
                WalletHeader(state, onSettings = {}, onDismissStatus = {}, onToggleStatusExpanded = {})
                ReceiveTab(state, state.requestDrafts, onOfferUrlChange = {}, onTxCodeChange = {},
                    onCopiesChange = { _, _ -> }, onPreviewOffer = {}, onAcceptOffer = {}, onDeclineOffer = {},
                    onResumeDeferred = {}, onDone = {}, onRefresh = {}, modifier = Modifier.weight(1f))
            }
        }
        if (failure) {
            onNodeWithText("Failed targets: 1").performScrollTo().assertIsDisplayed()
            onNodeWithText("Not attempted: 2").assertIsDisplayed()
        } else if (status.canResume) {
            onNodeWithText(if (status == WalletDemoContinuationStatus.AwaitingLocalSave) "Finish saving" else "Check with issuer")
                .performScrollTo().assertIsDisplayed()
        } else {
            onNodeWithTag("issuance-resume-visual-deferred-library").assertDoesNotExist()
            onNodeWithTag("issuance-refresh").assertIsDisplayed()
        }
        onNodeWithTag(WalletUiTestTags.OfferInput).assertDoesNotExist()
        onNodeWithTag("issuance-saved-${WalletVisualFixtures.credentialSummary.id}").assertExists()
        val id = if (failure) "partial_failure" else when (status) {
            WalletDemoContinuationStatus.AwaitingLocalSave -> "local_save_pending"
            WalletDemoContinuationStatus.RemoteOutcomeUncertain -> "remote_uncertain"
            WalletDemoContinuationStatus.StorageOutcomeUncertain -> "storage_uncertain"
            else -> "saved_and_deferred"
        }
        capture("batch.result.$id")
    }

    fun nearbyReady() = with(test) {
        content {
            WalletDemoProximityScreen(
                state = WalletDemoProximityUiState(active = true, sessionState = ProximityState.EngagementReady(
                    listOf(ProximityEngagement.Qr(WalletVisualFixtures.nearbyQrPayload)))),
                credentialDetailsById = emptyMap(),
                hostActions = WalletDemoProximityHostActionExecutor { ProximityHostActionResult.Completed },
                onSelectCredential = { _, _ -> }, onToggleElement = { _, _ -> }, onContinueAfterResponseChange = {},
                onApprove = {}, onDecline = {}, onRetry = {}, onRemediate = { _, _ -> },
                onCancel = {}, onDismiss = {}, onRestart = {},
            )
        }
        onNodeWithTag(WalletUiTestTags.ProximityQr).assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.ProximityCancel).assertIsDisplayed()
        capture("nearby.ready.qr")
    }

    fun providerSharingReview(compact: Boolean = false) = with(test) {
        content {
            WalletDemoSharingReviewScreen(review = WalletVisualFixtures.providerReview, title = "Share documents",
                onSubmit = {}, onCancel = {}, onBackAtRoot = {}, presentation = WalletReviewPresentation.Sheet)
        }
        onNodeWithTag(WalletUiTestTags.PresentationSubmitButton).assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.PresentationVerifierSection).performScrollTo().assertIsDisplayed()
        capture(if (compact) "sharing.provider.compact_dark_large_text" else "sharing.provider.review")
    }

    fun providerOfferReview() = with(test) {
        content {
            WalletDemoOfferCreateScreen(WalletDemoOfferCreateUiState.Review(WalletVisualFixtures.offer),
                onAccept = { _, _ -> }, onDecline = {}, onDismiss = {}, onCancelAuthorization = {},
                presentation = WalletReviewPresentation.Sheet)
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
                saved = (base.session as WalletSessionState.Ready).credentials, pending = base.deferredCredentials)
            else -> error("Unknown receiving state: $kind")
        }
        content {
            WalletDemoOfferCreateScreen(state, onAccept = { _, _ -> }, onDecline = {}, onDismiss = {},
                onCancelAuthorization = {}, presentation = WalletReviewPresentation.Sheet)
        }
        when (state) {
            is WalletDemoOfferCreateUiState.Receipt -> {
                onNodeWithTag("wallet.provider.done").assertIsDisplayed().assertIsEnabled()
                onNodeWithTag("issuance-saved-${WalletVisualFixtures.credentialSummary.id}").assertIsDisplayed()
                onNodeWithText("Check with issuer").assertIsDisplayed()
                onNodeWithText("Not attempted: 2").performScrollTo().assertIsDisplayed()
                onNodeWithTag("wallet.provider.done").assertIsDisplayed()
            }
            is WalletDemoOfferCreateUiState.Failure -> onNodeWithText("Close").assertIsDisplayed()
            else -> onNodeWithText("Cancel").assertIsDisplayed()
        }
        capture("receiving.provider.$kind")
    }

    fun providerSharingStatus(failure: Boolean = false) = with(test) {
        content {
            WalletProviderStatusScreen(title = if (failure) "Unable to share" else "Preparing request…",
                message = if (failure) "The request could not be verified." else null, onClose = {}, onDismiss = {})
        }
        onNodeWithText(if (failure) "Close" else "Cancel").assertIsDisplayed()
        capture(if (failure) "sharing.provider.failure" else "sharing.provider.preparing")
    }

    fun paymentReview(sheet: Boolean = false) = with(test) {
        val consent = WalletVisualFixtures.payment
        content {
            WalletDemoSharingReviewScreen(
                review = WalletVisualFixtures.paymentReview,
                title = "Payment", compact = false, onSubmit = {}, onCancel = {}, onBackAtRoot = {},
                presentation = if (sheet) WalletReviewPresentation.Sheet else WalletReviewPresentation.FullScreen,
                preparePaymentConsent = { consent },
            )
        }
        onNodeWithText("Confirm payment").performScrollTo().assertIsDisplayed()
        onNodeWithText("11.56 EUR").performScrollTo().assertIsDisplayed()
        onNodeWithText("Pay €11.56").assertIsDisplayed()
        onNodeWithText("bound-but-hidden").assertDoesNotExist()
        capture("${if (sheet) "payment.sheet" else "payment.mixed_credentials"}.main")
        onNodeWithTag("payment-details-toggle").performScrollTo().performClick()
        onNodeWithText("example-transaction-001").performScrollTo().assertIsDisplayed()
        capture("${if (sheet) "payment.sheet" else "payment.mixed_credentials"}.details")
        onNodeWithText("Payment authorisation").performScrollTo().assertIsDisplayed()
        onNodeWithText("Pay €11.56").assertIsEnabled().assertIsDisplayed()
        capture("${if (sheet) "payment.sheet" else "payment.mixed_credentials"}.requested_data")
    }
}
