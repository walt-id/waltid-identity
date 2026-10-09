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
import id.walt.walletdemo.compose.logic.WalletDemoProximityUiState
import id.walt.walletdemo.compose.logic.WalletDemoProximityHostActionExecutor
import id.walt.wallet2.mobile.ProximityEngagement
import id.walt.wallet2.mobile.ProximityHostActionResult
import id.walt.wallet2.mobile.ProximityState
import id.walt.walletdemo.compose.ui.components.CredentialDetailsContent
import id.walt.walletdemo.compose.ui.screens.ReceiveTab
import id.walt.walletdemo.compose.ui.screens.SettingsScreen
import id.walt.walletdemo.compose.ui.screens.WalletHeader

/** Content and readiness are shared; the platform adapter owns the renderer and baseline path. */
@OptIn(ExperimentalTestApi::class)
internal class WalletVisualScenarios(
    private val test: ComposeUiTest,
    private val captureImage: (String) -> Unit,
    private val platformTheme: @Composable (@Composable () -> Unit) -> Unit = { it() },
) {
    private fun capture(id: String) {
        // Capture settled state independently of host speed; shipping interactions remain animated.
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

    fun batchOffer(noneSelected: Boolean = false) = with(test) {
        val copies = WalletVisualFixtures.copies.mapValues { (_, count) -> if (noneSelected) 0 else count }
        val state = WalletDemoUiState(offerPreview = WalletVisualFixtures.offer, issuanceCopyCounts = copies)
        content {
            ReceiveTab(
                state = state, requestDrafts = state.requestDrafts,
                onOfferUrlChange = {}, onTxCodeChange = {}, onCopiesChange = { _, _ -> },
                onPreviewOffer = {}, onAcceptOffer = {}, onDeclineOffer = {}, onResumeDeferred = {},
            )
        }
        onNodeWithTag(WalletUiTestTags.OfferIssuerSection).assertIsDisplayed()
        val action = onNodeWithTag(WalletUiTestTags.OfferAcceptButton).assertIsDisplayed()
        if (noneSelected) action.assertIsNotEnabled() else action.assertIsEnabled()
        val scenario = if (noneSelected) "none_selected" else "two_targets_three_copies"
        capture("batch.offer.$scenario")
        onNodeWithText("Receive Library membership").performScrollTo().assertIsDisplayed()
        if (!noneSelected) onNodeWithText("Copies: 1").performScrollTo().assertIsDisplayed()
        action.assertIsDisplayed()
        capture("batch.offer.$scenario.second_target")
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
                waitForIdle()
                capture("credential.media.$image")
            }
        } finally {
            images.close()
        }
    }

    fun partialBatchResult() = with(test) {
        val state = WalletVisualFixtures.partialResult
        content {
            Column(Modifier.fillMaxSize()) {
                WalletHeader(state, onSettings = {}, onDismissStatus = {}, onToggleStatusExpanded = {})
                ReceiveTab(state, state.requestDrafts, onOfferUrlChange = {}, onTxCodeChange = {},
                    onCopiesChange = { _, _ -> }, onPreviewOffer = {}, onAcceptOffer = {}, onDeclineOffer = {},
                    onResumeDeferred = {}, modifier = Modifier.weight(1f))
            }
        }
        onNodeWithText("Saved credentials: 1. Pending targets: 1.").assertIsDisplayed()
        onNodeWithText("Check library-card").assertIsDisplayed()
        capture("batch.result.saved_and_deferred")
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

    fun paymentReview() = with(test) {
        val consent = WalletVisualFixtures.payment
        content {
            WalletDemoSharingReviewScreen(
                review = WalletVisualFixtures.paymentReview,
                title = "Payment", compact = false, onSubmit = {}, onCancel = {},
                preparePaymentConsent = { consent },
            )
        }
        onNodeWithText("Confirm payment").performScrollTo().assertIsDisplayed()
        onNodeWithText("11.56 EUR").performScrollTo().assertIsDisplayed()
        onNodeWithText("Pay €11.56").assertIsDisplayed()
        onNodeWithText("bound-but-hidden").assertDoesNotExist()
        capture("payment.mixed_credentials.main")
        onNodeWithTag("payment-details-toggle").performScrollTo().performClick()
        onNodeWithText("example-transaction-001").performScrollTo().assertIsDisplayed()
        capture("payment.mixed_credentials.details")
        onNodeWithText("Payment authorisation").performScrollTo().assertIsDisplayed()
        onNodeWithText("Pay €11.56").assertIsEnabled().assertIsDisplayed()
        capture("payment.mixed_credentials.requested_data")
    }
}
