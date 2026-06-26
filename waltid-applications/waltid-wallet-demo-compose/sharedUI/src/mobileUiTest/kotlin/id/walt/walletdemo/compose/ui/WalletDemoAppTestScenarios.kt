package id.walt.walletdemo.compose.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
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
import id.walt.walletdemo.compose.logic.WalletDemoClient
import id.walt.walletdemo.compose.logic.WalletDemoController
import id.walt.walletdemo.compose.logic.WalletDemoCredential
import id.walt.walletdemo.compose.logic.WalletDemoOperationResult
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class WalletDemoAppTestScenarios {

    fun pinSetupBootstrapsWalletAndShowsCredentials() = runComposeUiTest {
        val client = FakeWalletDemoClient(credentials = listOf(sampleCredential))
        val controller = WalletDemoController(client)

        setContent { WalletDemoApp(controller) }

        unlockWithPin()

        waitUntil(timeoutMillis = 5_000) { controller.state.value.isReady }
        onNodeWithTag("wallet.status").assertTextContains("Wallet ready")
        onNodeWithText("Example Credential").performScrollTo().assertIsDisplayed()
        assertEquals(1, client.bootstrapCalls)
    }

    fun receiveFlowUpdatesStatusAndCredentialList() = runComposeUiTest {
        val client = FakeWalletDemoClient(receivedCredentialIds = listOf("cred-1", "cred-2"))
        val controller = WalletDemoController(client)

        setContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.isReady }

        client.credentials = listOf(sampleCredential)
        onNodeWithTag("wallet.offerInput").performScrollTo().performTextInput("openid-credential-offer://example")
        onNodeWithTag("wallet.receiveButton").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)

        waitUntil(timeoutMillis = 5_000) { controller.state.value.status.startsWith("Received") }
        onNodeWithTag("wallet.status").assertTextContains("Received 2 credential(s)")
        onNodeWithText("Example Credential").performScrollTo().assertIsDisplayed()
        assertEquals("openid-credential-offer://example", client.receivedOfferUrl)
    }

    fun presentFlowUpdatesStatus() = runComposeUiTest {
        val client = FakeWalletDemoClient(
            credentials = listOf(sampleCredential),
            presentationResult = WalletDemoOperationResult(success = true, message = "Presentation sent"),
        )
        val controller = WalletDemoController(client)

        setContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.isReady }

        onNodeWithTag("wallet.presentationInput").performScrollTo().performTextInput("openid4vp://example")
        onNodeWithTag("wallet.presentButton").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)

        waitUntil(timeoutMillis = 5_000) { controller.state.value.status == "Presentation sent" }
        onNodeWithTag("wallet.status").assertTextContains("Presentation sent")
        assertEquals("openid4vp://example", client.presentedRequestUrl)
    }

    fun deepLinkReceiveAndPresentFlowUpdatesStatus() = runComposeUiTest {
        val offerUrl = "openid-credential-offer://example"
        val requestUrl = "openid4vp://example"
        val client = FakeWalletDemoClient(
            credentialsAfterReceive = listOf(sampleCredential),
            presentationResult = WalletDemoOperationResult(success = true, message = "Presentation sent"),
        )
        val controller = WalletDemoController(client)

        setContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.isReady }

        controller.handleDeepLink(offerUrl)
        waitForIdle()
        onNodeWithTag("wallet.offerInput").performScrollTo().assertTextContains(offerUrl)

        onNodeWithTag("wallet.receiveButton").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { controller.state.value.status.startsWith("Received") }
        onNodeWithTag("wallet.status").assertTextContains("Received 1 credential(s)")
        onNodeWithText("Example Credential").performScrollTo().assertIsDisplayed()

        controller.handleDeepLink(requestUrl)
        waitForIdle()
        onNodeWithTag("wallet.presentationInput").performScrollTo().assertTextContains(requestUrl)

        onNodeWithTag("wallet.presentButton").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { controller.state.value.status == "Presentation sent" }
        onNodeWithTag("wallet.status").assertTextContains("Presentation sent")
        assertEquals(offerUrl, client.receivedOfferUrl)
        assertEquals(requestUrl, client.presentedRequestUrl)
    }

    fun credentialsPersistAcrossControllerRecreation() = runComposeUiTest {
        val client = FakeWalletDemoClient(credentialsAfterReceive = listOf(sampleCredential))
        val firstController = WalletDemoController(client)
        var activeController by mutableStateOf(firstController)

        setContent { WalletDemoApp(activeController) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { firstController.state.value.isReady }

        firstController.handleDeepLink("openid-credential-offer://example")
        onNodeWithTag("wallet.receiveButton").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { firstController.state.value.status.startsWith("Received") }
        onNodeWithText("Example Credential").performScrollTo().assertIsDisplayed()

        val recreatedController = WalletDemoController(client)
        activeController = recreatedController
        waitForIdle()
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { recreatedController.state.value.isReady }

        onNodeWithText("Example Credential").performScrollTo().assertIsDisplayed()
        assertEquals(2, client.bootstrapCalls)
    }

    private fun androidx.compose.ui.test.ComposeUiTest.unlockWithPin() {
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
            label = "Example Credential",
            addedAt = "2026-06-17",
        )
    }
}

private class FakeWalletDemoClient(
    var credentials: List<WalletDemoCredential> = emptyList(),
    private val receivedCredentialIds: List<String> = listOf("cred-1"),
    private val credentialsAfterReceive: List<WalletDemoCredential>? = null,
    private val presentationResult: WalletDemoOperationResult = WalletDemoOperationResult(success = true, message = "Presentation sent"),
) : WalletDemoClient {
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
