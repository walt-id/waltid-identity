package id.walt.walletdemo.compose.logic

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WalletDemoControllerSkipPinTest {
    @Test
    fun skipPinStartsUnlockedAndBootstraps() = runTest {
        val wallet = RecordingDemoWallet()
        val controller = WalletDemoController(
            wallet = wallet,
            pinStore = InMemoryDemoPinStore(),
            skipPin = true,
            scope = backgroundScope,
            dispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler),
        )
        runCurrent()

        assertTrue(controller.state.value.auth is WalletAuthState.Unlocked)
        assertEquals(false, controller.state.value.pinLockEnabled)
        assertEquals(1, wallet.bootstrapCalls)
        assertTrue(controller.state.value.session is WalletSessionState.Ready)
    }

    @Test
    fun authorizationCallbackWaitsForBootstrapThenContinues() = runTest {
        val wallet = RecordingDemoWallet()
        wallet.pendingSession = WalletDemoIssuanceSession(
            id = "pending-auth",
            grant = WalletDemoIssuanceGrant.AuthorizationCode,
            preview = WalletDemoOfferPreview(
                issuer = WalletDemoIssuerMetadata("https://issuer.example", display = null),
                offeredCredentials = emptyList(),
                transactionCode = null,
                requiresIssuerAuthentication = true,
            ),
        )
        val dispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
        val controller = WalletDemoController(
            wallet = wallet,
            pinStore = InMemoryDemoPinStore(),
            skipPin = true,
            scope = backgroundScope,
            dispatcher = dispatcher,
        )
        controller.handleDeepLink("http://localhost:8080/?code=auth-code")
        runCurrent()

        assertEquals("http://localhost:8080/?code=auth-code", wallet.authorizationCallback)
        assertEquals(listOf("cred-1"), (controller.state.value.session as WalletSessionState.Ready).credentials.map { it.id })
    }
}

private class RecordingDemoWallet : DemoWallet {
    var bootstrapCalls = 0
    var pendingSession: WalletDemoIssuanceSession? = null
    var authorizationCallback: String? = null
    private var credentials: List<WalletDemoCredential> = emptyList()

    override suspend fun bootstrap(signingProtection: WalletDemoSigningProtection): WalletDemoBootstrapResult {
        bootstrapCalls += 1
        return WalletDemoBootstrapResult(
            keyId = "key",
            did = "did:jwk:test",
            publicJwk = "{}",
            signingProtection = WalletDemoSigningProtection.None,
        )
    }

    override suspend fun signingProtectionAvailability(
        signingProtection: WalletDemoSigningProtection,
    ) = WalletDemoSigningProtectionAvailability.Available

    override fun pendingAuthorizationIssuance(): WalletDemoIssuanceSession? = pendingSession

    override suspend fun listCredentials(): List<WalletDemoCredential> = credentials
    override suspend fun startIssuance(offerUrl: String, redirectUri: String, did: String?) =
        error("unused")
    override suspend fun beginAuthorizationIssuance(sessionId: String) = error("unused")
    override suspend fun continuePreAuthorizedIssuance(sessionId: String, transactionCode: String?) =
        error("unused")
    override suspend fun continueAuthorizationIssuance(sessionId: String, callbackUri: String): WalletDemoIssuanceOutcome {
        authorizationCallback = callbackUri
        credentials = listOf(
            WalletDemoCredential(
                id = "cred-1",
                format = "jwt_vc_json",
                issuer = "https://issuer.example",
                subject = "did:jwk:test",
                label = "Test credential",
            ),
        )
        return WalletDemoIssuanceOutcome.Stored(listOf("cred-1"))
    }
    override suspend fun cancelIssuance(sessionId: String) = WalletDemoIssuanceOutcome.Cancelled
    override suspend fun resumeDeferredIssuance(deferredCredentialId: String) = error("unused")
    override suspend fun present(requestUrl: String, did: String?) = error("unused")
    override suspend fun previewPresentation(requestUrl: String) = error("unused")
    override suspend fun submitPresentation(
        previewHandle: WalletDemoPresentationPreviewHandle,
        selectedCredentialOptions: List<WalletDemoPresentationCredentialSelection>,
        selectedDisclosureOptions: List<WalletDemoPresentationDisclosureSelection>,
        did: String?,
    ) = error("unused")
    override suspend fun rejectPresentation(previewHandle: WalletDemoPresentationPreviewHandle) =
        error("unused")
    override suspend fun discardPresentationPreview(previewHandle: WalletDemoPresentationPreviewHandle) = Unit
    override suspend fun deleteCredential(credentialId: String) = false
    override suspend fun deleteWallet() = Unit
}
