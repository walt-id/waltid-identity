package id.walt.walletdemo.compose.logic

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WalletDemoControllerTest {

    @Test
    fun setupPinRejectsInvalidLengthAndNonDigits() = runTest {
        val controller = controllerWith(FakeDemoWallet(), this)

        controller.updatePin("12a4")
        controller.updatePinConfirmation("12a4")
        controller.submitPin()

        val auth = controller.state.value.auth as WalletAuthState.Setup
        assertEquals("PIN must contain 4 to 8 digits", auth.error)
        assertTrue(controller.state.value.session is WalletSessionState.NotBootstrapped)
    }

    @Test
    fun setupPinRejectsMismatchedConfirmation() = runTest {
        val controller = controllerWith(FakeDemoWallet(), this)

        controller.updatePin("1234")
        controller.updatePinConfirmation("4321")
        controller.submitPin()

        val auth = controller.state.value.auth as WalletAuthState.Setup
        assertEquals("PIN confirmation does not match", auth.error)
        assertTrue(controller.state.value.session is WalletSessionState.NotBootstrapped)
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
        assertTrue(state.auth is WalletAuthState.Unlocked)
        val session = state.session as WalletSessionState.Ready
        assertEquals("Wallet ready", state.statusText)
        assertEquals("did:key:test", session.did)
        assertEquals(listOf(sampleCredential), session.credentials)
        assertEquals(1, wallet.bootstrapCalls)
    }

    @Test
    fun wrongLoginPinKeepsWalletLocked() = runTest {
        val controller = unlockedControllerWith(FakeDemoWallet(), this)

        controller.lock()
        controller.updatePin("9999")
        controller.submitPin()

        val auth = controller.state.value.auth as WalletAuthState.Login
        assertEquals("Wrong PIN", auth.error)
        assertTrue(controller.state.value.session is WalletSessionState.Ready)
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

        assertTrue(controller.state.value.auth is WalletAuthState.Unlocked)
        assertTrue(controller.state.value.session is WalletSessionState.Ready)
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
        assertEquals(WalletOperationState.Succeeded("Received 2 credential(s)"), controller.state.value.operation)
        assertEquals("Received 2 credential(s)", controller.state.value.statusText)
        val session = controller.state.value.session as WalletSessionState.Ready
        assertEquals(listOf(sampleCredential), session.credentials)
    }

    @Test
    fun receiveIgnoresBlankOfferUrl() = runTest {
        val wallet = FakeDemoWallet()
        val controller = unlockedControllerWith(wallet, this)

        controller.updateOfferUrl("   ")
        controller.receive()
        runCurrent()

        assertEquals(null, wallet.receivedOfferUrl)
        assertEquals("Wallet ready", controller.state.value.statusText)
    }

    @Test
    fun receiveWithoutTxCodePassesNull() = runTest {
        val wallet = FakeDemoWallet()
        val controller = unlockedControllerWith(wallet, this)

        controller.updateOfferUrl("openid-credential-offer://example")
        controller.receive()
        runCurrent()

        assertEquals("openid-credential-offer://example", wallet.receivedOfferUrl)
        assertEquals(null, wallet.receivedTxCode)
    }

    @Test
    fun receiveWithTxCodeForwardsCode() = runTest {
        val wallet = FakeDemoWallet()
        val controller = unlockedControllerWith(wallet, this)

        controller.updateOfferUrl("openid-credential-offer://example")
        controller.updateTxCode("1234")
        controller.receive()
        runCurrent()

        assertEquals("openid-credential-offer://example", wallet.receivedOfferUrl)
        assertEquals("1234", wallet.receivedTxCode)
    }

    @Test
    fun deepLinkWhileReadyImmediatelyReceives() = runTest {
        val wallet = FakeDemoWallet(offerTxCodeRequired = false)
        val controller = unlockedControllerWith(wallet, this)

        controller.handleDeepLink("openid-credential-offer://example")
        runCurrent()

        assertEquals("openid-credential-offer://example", wallet.receivedOfferUrl)
        assertTrue(controller.state.value.requestDrafts.offerFromDeepLink.not())
    }

    @Test
    fun deepLinkBeforeBootstrapReceivesAfterUnlock() = runTest {
        val wallet = FakeDemoWallet(offerTxCodeRequired = false)
        val controller = controllerWith(wallet, this)

        controller.handleDeepLink("openid-credential-offer://example")
        assertEquals("openid-credential-offer://example", controller.state.value.requestDrafts.offerUrl)
        assertTrue(controller.state.value.requestDrafts.offerFromDeepLink)
        assertEquals(null, wallet.receivedOfferUrl)

        controller.updatePin("1234")
        controller.updatePinConfirmation("1234")
        controller.submitPin()
        runCurrent()

        assertEquals("openid-credential-offer://example", wallet.receivedOfferUrl)
    }

    @Test
    fun resolveAndReceiveWithoutTxCodeReceivesDirectly() = runTest {
        val wallet = FakeDemoWallet(offerTxCodeRequired = false)
        val controller = unlockedControllerWith(wallet, this)

        controller.resolveAndReceive("openid-credential-offer://example")
        runCurrent()

        assertEquals("openid-credential-offer://example", wallet.resolvedOfferUrl)
        assertEquals("openid-credential-offer://example", wallet.receivedOfferUrl)
        assertEquals(null, wallet.receivedTxCode)
        assertFalse(controller.state.value.requestDrafts.txCodeRequired)
    }

    @Test
    fun resolveAndReceiveWithTxCodePromptsThenReceives() = runTest {
        val wallet = FakeDemoWallet(offerTxCodeRequired = true)
        val controller = unlockedControllerWith(wallet, this)

        controller.resolveAndReceive("openid-credential-offer://example")
        runCurrent()

        assertEquals("openid-credential-offer://example", wallet.resolvedOfferUrl)
        assertEquals(null, wallet.receivedOfferUrl)
        assertTrue(controller.state.value.requestDrafts.txCodeRequired)
        assertEquals(WalletOperationState.Idle, controller.state.value.operation)

        controller.updateTxCode("9876")
        controller.receive()
        runCurrent()

        assertEquals("openid-credential-offer://example", wallet.receivedOfferUrl)
        assertEquals("9876", wallet.receivedTxCode)
        assertFalse(controller.state.value.requestDrafts.txCodeRequired)
    }

    @Test
    fun manualEntryResolveWithoutTxCodeReceivesDirectly() = runTest {
        val wallet = FakeDemoWallet(offerTxCodeRequired = false)
        val controller = unlockedControllerWith(wallet, this)

        controller.updateOfferUrl("openid-credential-offer://manual")
        controller.resolveAndReceive(controller.state.value.requestDrafts.offerUrl)
        runCurrent()

        assertEquals("openid-credential-offer://manual", wallet.receivedOfferUrl)
        assertEquals(null, wallet.receivedTxCode)
        assertFalse(controller.state.value.requestDrafts.txCodeRequired)
    }

    @Test
    fun manualEntryResolveWithTxCodePromptsThenReceives() = runTest {
        val wallet = FakeDemoWallet(offerTxCodeRequired = true)
        val controller = unlockedControllerWith(wallet, this)

        controller.updateOfferUrl("openid-credential-offer://manual")
        controller.resolveAndReceive(controller.state.value.requestDrafts.offerUrl)
        runCurrent()

        assertTrue(controller.state.value.requestDrafts.txCodeRequired)
        assertEquals(null, wallet.receivedOfferUrl)

        controller.updateTxCode("5555")
        controller.receive()
        runCurrent()

        assertEquals("openid-credential-offer://manual", wallet.receivedOfferUrl)
        assertEquals("5555", wallet.receivedTxCode)
    }

    @Test
    fun updateOfferUrlClearsTxCodeRequired() = runTest {
        val wallet = FakeDemoWallet(offerTxCodeRequired = true)
        val controller = unlockedControllerWith(wallet, this)

        controller.updateOfferUrl("openid-credential-offer://first")
        controller.resolveAndReceive(controller.state.value.requestDrafts.offerUrl)
        runCurrent()
        assertTrue(controller.state.value.requestDrafts.txCodeRequired)

        controller.updateOfferUrl("openid-credential-offer://second")
        assertFalse(controller.state.value.requestDrafts.txCodeRequired)
    }

    @Test
    fun receiveWithBlankTxCodePassesNull() = runTest {
        val wallet = FakeDemoWallet()
        val controller = unlockedControllerWith(wallet, this)

        controller.updateOfferUrl("openid-credential-offer://example")
        controller.updateTxCode("   ")
        controller.receive()
        runCurrent()

        assertEquals(null, wallet.receivedTxCode)
    }

    @Test
    fun presentUpdatesStatusOnSuccess() = runTest {
        val wallet = FakeDemoWallet(presentationResult = WalletDemoOperationResult.Success("Presentation sent"))
        val controller = unlockedControllerWith(wallet, this)

        controller.updatePresentationRequestUrl("openid4vp://example")
        controller.present()
        runCurrent()

        assertEquals("openid4vp://example", wallet.presentedRequestUrl)
        assertEquals(WalletOperationState.Succeeded("Presentation sent"), controller.state.value.operation)
        assertEquals("Presentation sent", controller.state.value.statusText)
    }

    @Test
    fun presentIgnoresBlankRequestUrl() = runTest {
        val wallet = FakeDemoWallet()
        val controller = unlockedControllerWith(wallet, this)

        controller.updatePresentationRequestUrl("   ")
        controller.present()
        runCurrent()

        assertEquals(null, wallet.presentedRequestUrl)
        assertEquals("Wallet ready", controller.state.value.statusText)
    }

    @Test
    fun selectCredentialTracksSavedCredentialDetails() = runTest {
        val controller = unlockedControllerWith(FakeDemoWallet(credentials = listOf(sampleCredential)), this)

        controller.selectCredential("cred-1")

        assertEquals("cred-1", controller.state.value.selectedCredentialId)

        controller.clearSelectedCredential()

        assertEquals(null, controller.state.value.selectedCredentialId)
    }

    @Test
    fun receiveClearsSelectedCredentialWhenItNoLongerExists() = runTest {
        val wallet = FakeDemoWallet(credentials = listOf(sampleCredential))
        val controller = unlockedControllerWith(wallet, this)

        controller.selectCredential("cred-1")
        wallet.credentials = emptyList()
        controller.updateOfferUrl("openid-credential-offer://example")
        controller.receive()
        runCurrent()

        assertEquals(null, controller.state.value.selectedCredentialId)
    }

    @Test
    fun handleDeepLinkRoutesCredentialOffersAndPresentationRequests() = runTest {
        val controller = controllerWith(FakeDemoWallet(), this)
        val offerUrl = "openid-credential-offer://example"
        val presentationUrl = "openid4vp://example"

        controller.handleDeepLink(offerUrl)
        controller.handleDeepLink(presentationUrl)
        controller.handleDeepLink("https://example.com/ignored")

        assertEquals(offerUrl, controller.state.value.requestDrafts.offerUrl)
        assertEquals(presentationUrl, controller.state.value.requestDrafts.presentationRequestUrl)
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
    private val presentationResult: WalletDemoOperationResult = WalletDemoOperationResult.Success("Presentation sent"),
    private val offerTxCodeRequired: Boolean = false,
) : DemoWallet {
    var bootstrapCalls = 0
    var receivedOfferUrl: String? = null
    var receivedTxCode: String? = null
    var presentedRequestUrl: String? = null
    var resolvedOfferUrl: String? = null

    override suspend fun bootstrap(): WalletDemoBootstrapResult {
        bootstrapCalls += 1
        return WalletDemoBootstrapResult(keyId = "key-1", did = "did:key:test")
    }

    override suspend fun listCredentials(): List<WalletDemoCredential> = credentials

  /*  override suspend fun credentialDetails(id: String): WalletDemoCredentialDetails? =
        credentials.firstOrNull { it.id == id }?.let { WalletDemoCredentialDetails(id = it.id, credentialDataJson = "{}") }
*/
    override suspend fun resolveOffer(offerUrl: String): DemoOfferResolution {
        resolvedOfferUrl = offerUrl
        return DemoOfferResolution(txCodeRequired = offerTxCodeRequired)
    }

    override suspend fun receive(offerUrl: String, txCode: String?): List<String> {
        receivedOfferUrl = offerUrl
        receivedTxCode = txCode
        return receivedCredentialIds
    }

    override suspend fun present(requestUrl: String, did: String?): WalletDemoOperationResult {
        presentedRequestUrl = requestUrl
        return presentationResult
    }
}
