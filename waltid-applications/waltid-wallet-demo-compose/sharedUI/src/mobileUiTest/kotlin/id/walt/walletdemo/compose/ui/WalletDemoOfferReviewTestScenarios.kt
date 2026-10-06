package id.walt.walletdemo.compose.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.v2.runComposeUiTest
import id.walt.walletdemo.compose.logic.*
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class WalletDemoOfferReviewTestScenarios {
    fun receivedCredentialDetailsStayInTheProviderHost() = runComposeUiTest {
        val result = WalletVisualFixtures.partialResult
        val state = WalletDemoOfferCreateUiState.Receipt(
            requireNotNull(result.issuanceReceipt), result.receivedCredentials(), result.deferredCredentials,
        )
        var done = 0
        val resumed = mutableListOf<String>()
        setContent {
            WalletDemoOfferCreateScreen(state, onAccept = { _, _ -> }, onDecline = {},
                onDismiss = {}, onCancelAuthorization = {}, onDone = { done++ }, onResumeDeferred = { resumed += it })
        }
        onAllNodesWithTag("wallet.screen.header").assertCountEquals(1)
        onNodeWithTag("issuance-saved-${state.saved.single().id}").performScrollTo().performClick()
        onAllNodesWithTag("wallet.screen.header").assertCountEquals(1)
        onAllNodesWithTag("wallet.provider.done").assertCountEquals(0)
        onNodeWithText("Ada").performScrollTo().assertIsDisplayed()
        onNodeWithTag("credential-technical-details").performScrollTo().performClick()
        onAllNodesWithTag("wallet.screen.header").assertCountEquals(1)
        onNodeWithTag("wallet-detail-back").performClick()
        onNodeWithTag("wallet-detail-back").performClick()
        onNodeWithTag("issuance-pending-${state.pending.single().id}").performScrollTo().assertIsDisplayed()
        assertEquals(emptyList(), resumed)
        onNodeWithTag("wallet.provider.done").performClick()
        assertEquals(1, done)
    }

    fun selectedCopiesAndTransactionCodeSurviveHostChangesAndSubmitOnce() = runComposeUiTest {
        val preview = WalletDemoOfferPreview(
            WalletDemoIssuerMetadata("https://issuer.example", null),
            listOf("pid", "mdl").map {
                WalletDemoOfferedCredentialMetadata(it, "mso_mdoc", null, null, null, emptyList())
            },
            WalletDemoTransactionCodeRequirement(WalletDemoTransactionCodeInputMode.Numeric, 4, null),
            batchSize = 3,
        )
        val state = mutableStateOf(WalletDemoOfferCreateUiState.Review(preview))
        val visible = mutableStateOf(true)
        val draft = WalletDemoOfferDraft()
        val presentation = mutableStateOf(WalletReviewPresentation.FullScreen)
        val accepted = mutableListOf<Pair<String?, Map<String, Int>>>()
        var dismissed = 0
        setContent {
            if (visible.value) WalletDemoOfferCreateScreen(state.value, draft = draft,
                onAccept = { code, counts ->
                    accepted += code to counts
                    state.value = state.value.copy(submitting = true)
                }, onDecline = {}, onDismiss = { dismissed++ }, onCancelAuthorization = {},
                presentation = presentation.value)
        }
        onNodeWithTag(WalletUiTestTags.OfferAcceptButton).assertIsNotEnabled()
        onNodeWithTag("issuance-select-mdl").performScrollTo().performClick()
        onNodeWithTag("issuance-select-pid").performScrollTo().performClick()
        onNodeWithTag(WalletUiTestTags.TxCodeInput).performScrollTo().performTextInput("1234")
        onNodeWithTag(WalletUiTestTags.OfferAcceptButton).assertIsNotEnabled() // No credentials selected.
        onNodeWithTag("issuance-select-pid").performScrollTo().performClick()
        repeat(2) { onNodeWithTag("issuance-more-pid").performScrollTo().performClick() }
        onNodeWithTag("issuance-more-pid").assertIsNotEnabled()
        runOnIdle { visible.value = false }
        waitForIdle()
        runOnIdle { visible.value = true }
        onNodeWithTag(WalletUiTestTags.TxCodeInput).performScrollTo().assertIsDisplayed()
        assertEquals("1234", draft.transactionCode)
        runOnIdle { presentation.value = WalletReviewPresentation.Sheet }
        onNodeWithTag("issuance-copies-pid").performScrollTo().assertTextEquals("Copies: 3")
        onNodeWithTag("issuance-select-mdl").performScrollTo().assertIsOff()
        onNodeWithTag(WalletUiTestTags.OfferAcceptButton).performClick()
        assertEquals("1234", accepted.single().first)
        assertEquals(mapOf("pid" to 3, "mdl" to 0), accepted.single().second)
        onNodeWithTag(WalletUiTestTags.OfferAcceptButton).assertIsNotEnabled()
        onNodeWithTag(WalletUiTestTags.OfferDeclineButton).assertIsNotEnabled()
        onNodeWithTag("wallet.review.sheet").performTouchInput { swipeDown() }
        onNodeWithTag(WalletUiTestTags.OfferReview).assertIsDisplayed()
        assertEquals(0, dismissed)
        assertEquals(1, accepted.size)
    }
}
