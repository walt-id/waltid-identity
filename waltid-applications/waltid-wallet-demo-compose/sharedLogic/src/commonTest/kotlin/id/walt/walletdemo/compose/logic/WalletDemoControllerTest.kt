package id.walt.walletdemo.compose.logic

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WalletDemoControllerTest {

    @Test
    fun setupPinRejectsInvalidLengthAndNonDigits() = runTest {
        val controller = controllerWith(FakeWalletDemoClient(), this)

        controller.updatePin("12a4")
        controller.updatePinConfirmation("12a4")
        controller.submitPin()

        assertFalse(controller.state.value.isUnlocked)
        assertEquals("PIN must contain 4 to 8 digits", controller.state.value.pinError)
    }

    @Test
    fun setupPinRejectsMismatchedConfirmation() = runTest {
        val controller = controllerWith(FakeWalletDemoClient(), this)

        controller.updatePin("1234")
        controller.updatePinConfirmation("4321")
        controller.submitPin()

        assertFalse(controller.state.value.isUnlocked)
        assertEquals("PIN confirmation does not match", controller.state.value.pinError)
    }

    @Test
    fun setupPinUnlocksAndBootstrapsWallet() = runTest {
        val client = FakeWalletDemoClient(credentials = listOf(sampleCredential))
        val controller = controllerWith(client, this)

        controller.updatePin("1234")
        controller.updatePinConfirmation("1234")
        controller.submitPin()
        runCurrent()

        val state = controller.state.value
        assertTrue(state.isUnlocked)
        assertTrue(state.isReady)
        assertEquals("Wallet ready", state.status)
        assertEquals("did:key:test", state.did)
        assertEquals(listOf(sampleCredential), state.credentials)
        assertEquals(1, client.bootstrapCalls)
    }

    @Test
    fun wrongLoginPinKeepsWalletLocked() = runTest {
        val controller = unlockedControllerWith(FakeWalletDemoClient(), this)

        controller.lock()
        controller.updatePin("9999")
        controller.submitPin()

        assertFalse(controller.state.value.isUnlocked)
        assertEquals("Wrong PIN", controller.state.value.pinError)
    }

    @Test
    fun correctLoginPinUnlocksWithoutRepeatingBootstrapWhenReady() = runTest {
        val client = FakeWalletDemoClient()
        val controller = unlockedControllerWith(client, this)
        assertEquals(1, client.bootstrapCalls)

        controller.lock()
        controller.updatePin("1234")
        controller.submitPin()
        runCurrent()

        assertTrue(controller.state.value.isUnlocked)
        assertTrue(controller.state.value.isReady)
        assertEquals(1, client.bootstrapCalls)
    }

    @Test
    fun receiveRefreshesCredentialsAndStatus() = runTest {
        val client = FakeWalletDemoClient(receivedCredentialIds = listOf("cred-1", "cred-2"))
        val controller = unlockedControllerWith(client, this)

        client.credentials = listOf(sampleCredential)
        controller.updateOfferUrl("openid-credential-offer://example")
        controller.receive()
        runCurrent()

        assertEquals("openid-credential-offer://example", client.receivedOfferUrl)
        assertEquals("Received 2 credential(s)", controller.state.value.status)
        assertEquals(listOf(sampleCredential), controller.state.value.credentials)
    }

    @Test
    fun presentUpdatesStatusOnSuccess() = runTest {
        val client = FakeWalletDemoClient(presentationResult = WalletDemoOperationResult(success = true, message = "Presentation sent"))
        val controller = unlockedControllerWith(client, this)

        controller.updatePresentationRequestUrl("openid4vp://example")
        controller.present()
        runCurrent()

        assertEquals("openid4vp://example", client.presentedRequestUrl)
        assertEquals("Presentation sent", controller.state.value.status)
    }

    private fun controllerWith(client: WalletDemoClient, scope: TestScope): WalletDemoController =
        WalletDemoController(
            client = client,
            scope = scope.backgroundScope,
            dispatcher = StandardTestDispatcher(scope.testScheduler),
        )

    private fun unlockedControllerWith(client: WalletDemoClient, scope: TestScope): WalletDemoController {
        val controller = controllerWith(client, scope)
        controller.updatePin("1234")
        controller.updatePinConfirmation("1234")
        controller.submitPin()
        scope.runCurrent()
        return controller
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
        return receivedCredentialIds
    }

    override suspend fun present(requestUrl: String, did: String?): WalletDemoOperationResult {
        presentedRequestUrl = requestUrl
        return presentationResult
    }
}
