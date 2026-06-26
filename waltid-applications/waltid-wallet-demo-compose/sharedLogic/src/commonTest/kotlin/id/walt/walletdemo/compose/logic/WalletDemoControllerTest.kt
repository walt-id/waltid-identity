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
        val controller = controllerWith(FakeDemoWallet(), this)

        controller.updatePin("12a4")
        controller.updatePinConfirmation("12a4")
        controller.submitPin()

        assertFalse(controller.state.value.isUnlocked)
        assertEquals("PIN must contain 4 to 8 digits", controller.state.value.pinError)
    }

    @Test
    fun setupPinRejectsMismatchedConfirmation() = runTest {
        val controller = controllerWith(FakeDemoWallet(), this)

        controller.updatePin("1234")
        controller.updatePinConfirmation("4321")
        controller.submitPin()

        assertFalse(controller.state.value.isUnlocked)
        assertEquals("PIN confirmation does not match", controller.state.value.pinError)
    }

    @Test
    fun setupPinUnlocksAndBootstrapsWallet() = runTest {
        val wallet = FakeDemoWallet(credentials = listOf(sampleCredential))
        val controller = controllerWith(wallet, this)

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
        assertEquals(1, wallet.bootstrapCalls)
    }

    @Test
    fun wrongLoginPinKeepsWalletLocked() = runTest {
        val controller = unlockedControllerWith(FakeDemoWallet(), this)

        controller.lock()
        controller.updatePin("9999")
        controller.submitPin()

        assertFalse(controller.state.value.isUnlocked)
        assertEquals("Wrong PIN", controller.state.value.pinError)
    }

    @Test
    fun correctLoginPinUnlocksWithoutRepeatingBootstrapWhenReady() = runTest {
        val wallet = FakeDemoWallet()
        val controller = unlockedControllerWith(wallet, this)
        assertEquals(1, wallet.bootstrapCalls)

        controller.lock()
        controller.updatePin("1234")
        controller.submitPin()
        runCurrent()

        assertTrue(controller.state.value.isUnlocked)
        assertTrue(controller.state.value.isReady)
        assertEquals(1, wallet.bootstrapCalls)
    }

    @Test
    fun receiveRefreshesCredentialsAndStatus() = runTest {
        val wallet = FakeDemoWallet(receivedCredentialIds = listOf("cred-1", "cred-2"))
        val controller = unlockedControllerWith(wallet, this)

        wallet.credentials = listOf(sampleCredential)
        controller.updateOfferUrl("openid-credential-offer://example")
        controller.receive()
        runCurrent()

        assertEquals("openid-credential-offer://example", wallet.receivedOfferUrl)
        assertEquals("Received 2 credential(s)", controller.state.value.status)
        assertEquals(listOf(sampleCredential), controller.state.value.credentials)
    }

    @Test
    fun receiveIgnoresBlankOfferUrl() = runTest {
        val wallet = FakeDemoWallet()
        val controller = unlockedControllerWith(wallet, this)

        controller.updateOfferUrl("   ")
        controller.receive()
        runCurrent()

        assertEquals(null, wallet.receivedOfferUrl)
        assertEquals("Wallet ready", controller.state.value.status)
    }

    @Test
    fun presentUpdatesStatusOnSuccess() = runTest {
        val wallet = FakeDemoWallet(presentationResult = WalletDemoOperationResult(success = true, message = "Presentation sent"))
        val controller = unlockedControllerWith(wallet, this)

        controller.updatePresentationRequestUrl("openid4vp://example")
        controller.present()
        runCurrent()

        assertEquals("openid4vp://example", wallet.presentedRequestUrl)
        assertEquals("Presentation sent", controller.state.value.status)
    }

    @Test
    fun presentIgnoresBlankRequestUrl() = runTest {
        val wallet = FakeDemoWallet()
        val controller = unlockedControllerWith(wallet, this)

        controller.updatePresentationRequestUrl("   ")
        controller.present()
        runCurrent()

        assertEquals(null, wallet.presentedRequestUrl)
        assertEquals("Wallet ready", controller.state.value.status)
    }

    @Test
    fun handleDeepLinkRoutesCredentialOffersAndPresentationRequests() = runTest {
        val controller = controllerWith(FakeDemoWallet(), this)
        val offerUrl = "openid-credential-offer://example"
        val presentationUrl = "openid4vp://example"

        controller.handleDeepLink(offerUrl)
        controller.handleDeepLink(presentationUrl)
        controller.handleDeepLink("https://example.com/ignored")

        assertEquals(offerUrl, controller.state.value.offerUrl)
        assertEquals(presentationUrl, controller.state.value.presentationRequestUrl)
    }

    private fun controllerWith(wallet: DemoWallet, scope: TestScope): WalletDemoController =
        WalletDemoController(
            wallet = wallet,
            scope = scope.backgroundScope,
            dispatcher = StandardTestDispatcher(scope.testScheduler),
        )

    private fun unlockedControllerWith(wallet: DemoWallet, scope: TestScope): WalletDemoController {
        val controller = controllerWith(wallet, scope)
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

private class FakeDemoWallet(
    var credentials: List<WalletDemoCredential> = emptyList(),
    private val receivedCredentialIds: List<String> = listOf("cred-1"),
    private val presentationResult: WalletDemoOperationResult = WalletDemoOperationResult(success = true, message = "Presentation sent"),
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
        return receivedCredentialIds
    }

    override suspend fun present(requestUrl: String, did: String?): WalletDemoOperationResult {
        presentedRequestUrl = requestUrl
        return presentationResult
    }
}
