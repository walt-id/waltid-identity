package id.walt.walletdemo.compose.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import id.walt.walletdemo.compose.logic.WalletDemoBootstrapResult
import id.walt.walletdemo.compose.logic.DemoWallet
import id.walt.walletdemo.compose.logic.WalletDemoController
import id.walt.walletdemo.compose.logic.WalletDemoCredential
import id.walt.walletdemo.compose.logic.WalletDemoOperationResult
import id.walt.walletdemo.compose.logic.WalletDemoSampleCredentialData
import id.walt.walletdemo.compose.logic.WalletSessionState
import id.walt.walletdemo.compose.logic.statusText
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class WalletDemoAppTestScenarios {

    fun pinSetupBootstrapsWalletAndShowsCredentials() = runComposeUiTest {
        val wallet = FakeDemoWallet(credentials = listOf(sampleCredential))
        val controller = WalletDemoController(wallet)

        setContent { WalletDemoApp(controller) }

        unlockWithPin()

        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }
        onNodeWithTag("wallet.status").assertTextContains("Wallet ready")
        onNodeWithText("Example Credential").performScrollTo().assertIsDisplayed()
        assertEquals(1, wallet.bootstrapCalls)
    }

    fun savedCredentialOpensNeutralDetails() = runComposeUiTest {
        val wallet = FakeDemoWallet(credentials = listOf(sampleCredential))
        val controller = WalletDemoController(wallet)

        setContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        onNodeWithTag(WalletUiTestTags.credentialCard("cred-1")).performScrollTo().performClick()

        onNodeWithTag(WalletUiTestTags.CredentialDetailsScreen).assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.credentialDetails("cred-1")).assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.claimGroup("Personal details")).performScrollTo().assertIsDisplayed()
        onNodeWithText("Given name").performScrollTo().assertIsDisplayed()
        onNodeWithText("Ada").performScrollTo().assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.claimImage("portrait")).performScrollTo().assertIsDisplayed()

        onNodeWithTag(WalletUiTestTags.DetailsBack).performClick()

        onNodeWithText("Example Credential").performScrollTo().assertIsDisplayed()
    }

    fun receiveFlowUpdatesStatusAndCredentialList() = runComposeUiTest {
        val wallet = FakeDemoWallet(receivedCredentialIds = listOf("cred-1", "cred-2"))
        val controller = WalletDemoController(wallet)

        setContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        wallet.credentials = listOf(sampleCredential)
        onNodeWithTag("wallet.offerInput").performScrollTo().performTextInput("openid-credential-offer://example")
        onNodeWithTag("wallet.receiveButton").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)

        waitUntil(timeoutMillis = 5_000) { controller.state.value.statusText.startsWith("Received") }
        onNodeWithTag("wallet.status").assertTextContains("Received 2 credential(s)")
        onNodeWithText("Example Credential").performScrollTo().assertIsDisplayed()
        assertEquals("openid-credential-offer://example", wallet.receivedOfferUrl)
    }

    fun presentFlowUpdatesStatus() = runComposeUiTest {
        val wallet = FakeDemoWallet(
            credentials = listOf(sampleCredential),
            presentationResult = WalletDemoOperationResult.Success("Presentation sent"),
        )
        val controller = WalletDemoController(wallet)

        setContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        onNodeWithTag("wallet.presentationInput").performScrollTo().performTextInput("openid4vp://example")
        onNodeWithTag("wallet.presentButton").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)

        waitUntil(timeoutMillis = 5_000) { controller.state.value.statusText == "Presentation sent" }
        onNodeWithTag("wallet.status").assertTextContains("Presentation sent")
        assertEquals("openid4vp://example", wallet.presentedRequestUrl)
    }

    fun deepLinkReceiveAndPresentFlowUpdatesStatus() = runComposeUiTest {
        val offerUrl = "openid-credential-offer://example"
        val requestUrl = "openid4vp://example"
        val wallet = FakeDemoWallet(
            credentialsAfterReceive = listOf(sampleCredential),
            presentationResult = WalletDemoOperationResult.Success("Presentation sent"),
        )
        val controller = WalletDemoController(wallet)

        setContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        controller.handleDeepLink(offerUrl)
        waitForIdle()
        onNodeWithTag("wallet.offerInput").performScrollTo().assertTextContains(offerUrl)

        onNodeWithTag("wallet.receiveButton").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { controller.state.value.statusText.startsWith("Received") }
        onNodeWithTag("wallet.status").assertTextContains("Received 1 credential(s)")
        onNodeWithText("Example Credential").performScrollTo().assertIsDisplayed()

        controller.handleDeepLink(requestUrl)
        waitForIdle()
        onNodeWithTag("wallet.presentationInput").performScrollTo().assertTextContains(requestUrl)

        onNodeWithTag("wallet.presentButton").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { controller.state.value.statusText == "Presentation sent" }
        onNodeWithTag("wallet.status").assertTextContains("Presentation sent")
        assertEquals(offerUrl, wallet.receivedOfferUrl)
        assertEquals(requestUrl, wallet.presentedRequestUrl)
    }

    fun credentialsPersistAcrossControllerRecreation() = runComposeUiTest {
        val wallet = FakeDemoWallet(credentialsAfterReceive = listOf(sampleCredential))
        val firstController = WalletDemoController(wallet)
        var activeController by mutableStateOf(firstController)

        setContent { WalletDemoApp(activeController) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { firstController.state.value.session is WalletSessionState.Ready }

        firstController.handleDeepLink("openid-credential-offer://example")
        onNodeWithTag("wallet.receiveButton").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { firstController.state.value.statusText.startsWith("Received") }
        onNodeWithText("Example Credential").performScrollTo().assertIsDisplayed()

        val recreatedController = WalletDemoController(wallet)
        activeController = recreatedController
        waitForIdle()
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { recreatedController.state.value.session is WalletSessionState.Ready }

        onNodeWithText("Example Credential").performScrollTo().assertIsDisplayed()
        assertEquals(2, wallet.bootstrapCalls)
    }

    private fun ComposeUiTest.unlockWithPin() {
        onNodeWithTag("wallet.pinInput").performClick().performTextInput("1234")
        onNodeWithTag("wallet.pinConfirmationInput").performClick().performTextInput("1234")
        waitForIdle()
        onNodeWithTag("wallet.pinSubmitButton").performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
    }

    private companion object {
        val sampleCredential = WalletDemoCredential(
            id = "cred-1",
            format = "jwt_vc_json",
            issuer = "Example Issuer",
            subject = "did:key:holder",
            label = "Example Credential",
            addedAt = "2026-06-17",
            credentialDataJson = WalletDemoSampleCredentialData.credentialDataJsonWithPortrait,
        )
    }
}

private class FakeDemoWallet(
    var credentials: List<WalletDemoCredential> = emptyList(),
    private val receivedCredentialIds: List<String> = listOf("cred-1"),
    private val credentialsAfterReceive: List<WalletDemoCredential>? = null,
    private val presentationResult: WalletDemoOperationResult = WalletDemoOperationResult.Success("Presentation sent"),
) : DemoWallet {
    var bootstrapCalls = 0
    var receivedOfferUrl: String? = null
    var presentedRequestUrl: String? = null

    override suspend fun bootstrap(): WalletDemoBootstrapResult {
        bootstrapCalls += 1
        return WalletDemoBootstrapResult(keyId = "key-1", did = "did:key:test")
    }

    override suspend fun listCredentials(): List<WalletDemoCredential> = credentials

    override suspend fun receive(offerUrl: String): List<String> {
        receivedOfferUrl = offerUrl
        credentialsAfterReceive?.let { credentials = it }
        return receivedCredentialIds
    }

    override suspend fun present(requestUrl: String, did: String?): WalletDemoOperationResult {
        presentedRequestUrl = requestUrl
        return presentationResult
    }
}
