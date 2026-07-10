package id.walt.walletdemo.compose.logic

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        controller.selectTab(WalletDemoTab.Receive)
        controller.updateOfferUrl("openid-credential-offer://example")
        controller.receive()
        runCurrent()

        assertEquals("openid-credential-offer://example", wallet.receivedOfferUrl)
        assertEquals(
            WalletOperationState.Succeeded("Received 1 credential(s)", WalletDemoTab.Receive),
            controller.state.value.operation,
        )
        assertEquals("Received 1 credential(s)", controller.state.value.statusText)
        assertEquals(listOf("cred-1"), controller.state.value.lastReceivedCredentialIds)
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
    fun presentUpdatesStatusOnSuccess() = runTest {
        val wallet = FakeDemoWallet(presentationResult = WalletDemoOperationResult.Success("Presentation sent"))
        val controller = unlockedControllerWith(wallet, this)

        controller.selectTab(WalletDemoTab.Present)
        controller.updatePresentationRequestUrl("openid4vp://example")
        controller.present()
        runCurrent()

        assertEquals("openid4vp://example", wallet.presentedRequestUrl)
        assertEquals(
            WalletOperationState.Succeeded("Presentation sent", WalletDemoTab.Present),
            controller.state.value.operation,
        )
        assertEquals("Presentation sent", controller.state.value.statusText)
    }

    @Test
    fun presentationPreviewApproveAndRejectUseStepwiseWalletApi() = runTest {
        val preview = WalletDemoPresentationPreview(
            verifierName = "Example Verifier",
            clientId = "https://verifier.example",
            credentialOptions = listOf(
                WalletDemoPresentationCredentialOption(
                    queryId = "pid",
                    credentialId = "cred-1",
                    label = "Example Credential",
                    issuer = "Example Issuer",
                    format = "jwt_vc_json",
                    disclosures = listOf(
                        WalletDemoPresentationDisclosure(
                            label = "given_name",
                            valueJson = "\"Ada\"",
                            displayValue = "Ada",
                            selectivelyDisclosable = true,
                        )
                    ),
                )
            ),
        )
        val wallet = FakeDemoWallet(presentationPreview = preview)
        val controller = unlockedControllerWith(wallet, this)

        controller.selectTab(WalletDemoTab.Present)
        controller.updatePresentationRequestUrl("openid4vp://example")
        controller.previewPresentation()
        runCurrent()

        assertEquals(preview, controller.state.value.presentationPreview)
        assertEquals(WalletOperationState.Idle, controller.state.value.operation)
        assertEquals("Review presentation request", controller.state.value.statusText)
        assertEquals(setOf("cred-1"), controller.state.value.selectedPresentationCredentialIds)

        controller.submitPresentation()
        runCurrent()

        assertEquals(listOf("cred-1"), wallet.submittedCredentialIds)
        assertEquals(
            WalletOperationState.Succeeded("Presentation sent", WalletDemoTab.Present),
            controller.state.value.operation,
        )

        controller.previewPresentation()
        runCurrent()
        controller.cancelPresentationReview()

        assertEquals(
            WalletOperationState.Succeeded("Presentation review cancelled", WalletDemoTab.Present),
            controller.state.value.operation,
        )
        assertEquals(null, controller.state.value.presentationPreview)
        assertEquals(emptySet(), controller.state.value.selectedPresentationCredentialIds)
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
    fun handleDeepLinkRoutesCredentialOffersAndPresentationRequests() = runTest {
        val controller = controllerWith(FakeDemoWallet(), this)
        val offerUrl = "openid-credential-offer://example"
        val presentationUrl = "openid4vp://example"

        controller.handleDeepLink(offerUrl)
        assertEquals(WalletDemoTab.Receive, controller.state.value.selectedTab)
        controller.handleDeepLink(presentationUrl)
        assertEquals(WalletDemoTab.Present, controller.state.value.selectedTab)
        controller.handleDeepLink("https://example.com/ignored")

        assertEquals(offerUrl, controller.state.value.requestDrafts.offerUrl)
        assertEquals(presentationUrl, controller.state.value.requestDrafts.presentationRequestUrl)
    }

    @Test
    fun handleDeepLinkResetsCompletedReceiveAndPresentationState() = runTest {
        val offerUrl = "openid-credential-offer://example"
        val presentationUrl = "openid4vp://example"
        val wallet = FakeDemoWallet(
            credentials = listOf(sampleCredential),
            presentationPreview = WalletDemoPresentationPreview(
                verifierName = "Example Verifier",
                clientId = "https://verifier.example",
                credentialOptions = listOf(
                    WalletDemoPresentationCredentialOption(
                        queryId = "pid",
                        credentialId = "cred-1",
                        label = "Example Credential",
                        issuer = "Example Issuer",
                        format = "jwt_vc_json",
                        disclosures = emptyList(),
                    )
                ),
            )
        )
        val controller = unlockedControllerWith(wallet, this)

        controller.updateOfferUrl(offerUrl)
        controller.receive()
        runCurrent()
        assertTrue(controller.state.value.receiveCompleted)

        controller.updatePresentationRequestUrl(presentationUrl)
        controller.previewPresentation()
        runCurrent()
        controller.submitPresentation()
        runCurrent()
        assertTrue(controller.state.value.presentationCompleted)

        controller.handleDeepLink(offerUrl)

        assertEquals(WalletDemoTab.Receive, controller.state.value.selectedTab)
        assertEquals(offerUrl, controller.state.value.requestDrafts.offerUrl)
        assertEquals(1, controller.state.value.receiveNavigationResetKey)
        assertEquals(0, controller.state.value.presentationNavigationResetKey)
        assertEquals(emptyList(), controller.state.value.lastReceivedCredentialIds)
        assertFalse(controller.state.value.receiveCompleted)
        assertEquals(WalletOperationState.Idle, controller.state.value.operation)
        assertTrue(controller.state.value.receiveUrlEntryEnabled)
        assertTrue(controller.state.value.receiveActionEnabled)
        assertEquals("Wallet ready", controller.state.value.statusText)

        controller.handleDeepLink(presentationUrl)

        assertEquals(WalletDemoTab.Present, controller.state.value.selectedTab)
        assertEquals(presentationUrl, controller.state.value.requestDrafts.presentationRequestUrl)
        assertEquals(1, controller.state.value.receiveNavigationResetKey)
        assertEquals(1, controller.state.value.presentationNavigationResetKey)
        assertEquals(null, controller.state.value.presentationPreview)
        assertEquals(emptySet(), controller.state.value.selectedPresentationCredentialIds)
        assertFalse(controller.state.value.presentationCompleted)
        assertEquals(WalletOperationState.Idle, controller.state.value.operation)
        assertTrue(controller.state.value.presentationUrlEntryEnabled)
        assertTrue(controller.state.value.presentationPreviewActionEnabled)
        assertEquals("Wallet ready", controller.state.value.statusText)

        controller.handleDeepLink(presentationUrl)

        assertEquals(2, controller.state.value.presentationNavigationResetKey)
    }

    @Test
    fun receiveCompletionTracksReceivedCredentialsAndCanStartNewFlow() = runTest {
        val wallet = FakeDemoWallet(receivedCredentialIds = listOf("cred-1"))
        val controller = unlockedControllerWith(wallet, this)

        assertTrue(controller.state.value.receiveUrlEntryEnabled)
        assertFalse(controller.state.value.receiveActionEnabled)

        controller.selectTab(WalletDemoTab.Receive)
        controller.updateOfferUrl("openid-credential-offer://example")
        assertTrue(controller.state.value.receiveActionEnabled)

        wallet.credentials = listOf(sampleCredential)
        controller.receive()
        runCurrent()

        assertTrue(controller.state.value.receiveCompleted)
        assertFalse(controller.state.value.receiveUrlEntryEnabled)
        assertFalse(controller.state.value.receiveActionEnabled)
        assertEquals(listOf("cred-1"), controller.state.value.lastReceivedCredentialIds)
        assertEquals(WalletDemoTab.Receive, controller.state.value.selectedTab)

        val resetKeyBeforeNewFlow = controller.state.value.receiveNavigationResetKey
        controller.startNewReceiveFlow()

        assertEquals("", controller.state.value.requestDrafts.offerUrl)
        assertEquals(emptyList(), controller.state.value.lastReceivedCredentialIds)
        assertTrue(!controller.state.value.receiveCompleted)
        assertTrue(controller.state.value.receiveUrlEntryEnabled)
        assertFalse(controller.state.value.receiveActionEnabled)
        assertEquals(resetKeyBeforeNewFlow + 1, controller.state.value.receiveNavigationResetKey)
        assertEquals(WalletOperationState.Idle, controller.state.value.operation)
        assertEquals("Wallet ready", controller.state.value.statusText)
    }

    @Test
    fun receiveCompletionDerivesNewCredentialsWhenWalletReturnsNoIds() = runTest {
        val existingCredential = sampleCredential.copy(id = "old-cred", label = "Existing Credential")
        val newCredential = sampleCredential.copy(id = "new-cred", label = "New Credential")
        val wallet = FakeDemoWallet(credentials = listOf(existingCredential), receivedCredentialIds = emptyList())
        val controller = unlockedControllerWith(wallet, this)

        wallet.credentials = listOf(existingCredential, newCredential)
        controller.selectTab(WalletDemoTab.Receive)
        controller.updateOfferUrl("openid-credential-offer://example")
        controller.receive()
        runCurrent()

        assertTrue(controller.state.value.receiveCompleted)
        assertEquals(listOf("new-cred"), controller.state.value.lastReceivedCredentialIds)
        assertEquals("Received 1 credential(s)", controller.state.value.statusText)
        assertEquals(listOf(newCredential), controller.state.value.receivedCredentials())
    }

    @Test
    fun receiveDoesNotCompleteWhenNoDisplayableCredentialIsAvailable() = runTest {
        val wallet = FakeDemoWallet(credentials = emptyList(), receivedCredentialIds = listOf("missing-cred"))
        val controller = unlockedControllerWith(wallet, this)

        controller.selectTab(WalletDemoTab.Receive)
        controller.updateOfferUrl("openid-credential-offer://example")
        controller.receive()
        runCurrent()

        assertFalse(controller.state.value.receiveCompleted)
        assertEquals(emptyList(), controller.state.value.lastReceivedCredentialIds)
        assertTrue(controller.state.value.receiveUrlEntryEnabled)
        assertEquals(
            WalletOperationState.Failed(
                "Receive failed: received credentials are not available locally",
                WalletDemoTab.Receive,
            ),
            controller.state.value.operation,
        )
        assertEquals(
            "Receive failed: received credentials are not available locally",
            controller.state.value.statusText,
        )
    }

    @Test
    fun presentationCompletionCanStartNewFlow() = runTest {
        val preview = WalletDemoPresentationPreview(
            verifierName = "Example Verifier",
            clientId = "https://verifier.example",
            credentialOptions = listOf(
                WalletDemoPresentationCredentialOption(
                    queryId = "pid",
                    credentialId = "cred-1",
                    label = "Example Credential",
                    issuer = "Example Issuer",
                    format = "jwt_vc_json",
                    disclosures = emptyList(),
                )
            ),
        )
        val wallet = FakeDemoWallet(presentationPreview = preview)
        val controller = unlockedControllerWith(wallet, this)

        controller.selectTab(WalletDemoTab.Present)
        controller.updatePresentationRequestUrl("openid4vp://example")
        assertTrue(controller.state.value.presentationUrlEntryEnabled)
        assertFalse(controller.state.value.presentationPreviewActionEnabled)

        controller.previewPresentation()
        runCurrent()
        assertFalse(controller.state.value.presentationUrlEntryEnabled)
        assertFalse(controller.state.value.presentationPreviewActionEnabled)

        controller.submitPresentation()
        runCurrent()

        assertTrue(controller.state.value.presentationCompleted)
        assertEquals(preview, controller.state.value.presentationPreview)
        assertEquals(emptySet(), controller.state.value.selectedPresentationCredentialIds)
        assertFalse(controller.state.value.presentationUrlEntryEnabled)
        assertFalse(controller.state.value.presentationPreviewActionEnabled)
        assertEquals(WalletDemoTab.Present, controller.state.value.selectedTab)

        val resetKeyBeforeNewFlow = controller.state.value.presentationNavigationResetKey
        controller.startNewPresentationFlow()

        assertEquals("", controller.state.value.requestDrafts.presentationRequestUrl)
        assertEquals(null, controller.state.value.presentationPreview)
        assertEquals(emptySet(), controller.state.value.selectedPresentationCredentialIds)
        assertTrue(!controller.state.value.presentationCompleted)
        assertTrue(controller.state.value.presentationUrlEntryEnabled)
        assertFalse(controller.state.value.presentationPreviewActionEnabled)
        assertEquals(resetKeyBeforeNewFlow + 1, controller.state.value.presentationNavigationResetKey)
        assertEquals(WalletOperationState.Idle, controller.state.value.operation)
        assertEquals("Wallet ready", controller.state.value.statusText)
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
    private val presentationResult: WalletDemoOperationResult = WalletDemoOperationResult.Success(WalletDisplayText.PresentationSent),
    private val presentationPreview: WalletDemoPresentationPreview = WalletDemoPresentationPreview(
        verifierName = null,
        clientId = null,
        credentialOptions = emptyList(),
    ),
) : DemoWallet {
    var bootstrapCalls = 0
    var receivedOfferUrl: String? = null
    var presentedRequestUrl: String? = null
    var previewedRequestUrl: String? = null
    var submittedRequestUrl: String? = null
    var submittedCredentialIds: List<String>? = null

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

    override suspend fun previewPresentation(requestUrl: String): WalletDemoPresentationPreview {
        previewedRequestUrl = requestUrl
        return presentationPreview
    }

    override suspend fun submitPresentation(
        requestUrl: String,
        selectedCredentialIds: List<String>,
        did: String?,
    ): WalletDemoOperationResult {
        submittedRequestUrl = requestUrl
        submittedCredentialIds = selectedCredentialIds
        return WalletDemoOperationResult.Success(WalletDisplayText.PresentationSent)
    }
}
