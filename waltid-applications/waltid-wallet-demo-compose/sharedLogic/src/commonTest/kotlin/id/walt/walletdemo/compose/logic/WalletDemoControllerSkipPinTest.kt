package id.walt.walletdemo.compose.logic

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlin.time.Instant
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher

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
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        runCurrent()

        assertTrue(controller.state.value.auth is WalletAuthState.Unlocked)
        assertEquals(false, controller.state.value.pinLockEnabled)
        assertEquals(1, wallet.bootstrapCalls)
        assertTrue(controller.state.value.session is WalletSessionState.Ready)
    }

    @Test
    fun statusObservationStopsInBackgroundAndPreferenceOffStillAllowsManualChecks() = runTest {
        val wallet = RecordingDemoWallet()
        val settings = InMemoryDemoSharingSettingsStore().apply { setAutomaticCredentialStatusChecks(false) }
        val controller = WalletDemoController(wallet, InMemoryDemoPinStore(), sharingSettings = settings,
            skipPin = true, scope = backgroundScope,
            dispatcher = StandardTestDispatcher(testScheduler))
        runCurrent()
        assertEquals(1, wallet.statusCollectors)
        assertTrue(wallet.statusRefreshModes.isEmpty(), "Reading/observing status must not refresh it")
        controller.handleApplicationBackgrounded(); runCurrent()
        assertEquals(0, wallet.statusCollectors)
        controller.checkCredentialStatuses(); runCurrent()
        assertTrue(wallet.statusRefreshModes.isEmpty(), "Hidden hosts must not admit status checks")
        controller.handleApplicationForegrounded(); runCurrent()
        assertEquals(1, wallet.statusCollectors)
        assertTrue(wallet.statusRefreshModes.isEmpty())
        controller.checkCredentialStatuses(); runCurrent()
        assertEquals(listOf(true), wallet.statusRefreshModes)
    }

    @Test
    fun automaticChecksFollowSharedDueTimesAndStopAfterLeavingTheForeground() = runTest {
        val start = Instant.parse("2026-11-12T10:00:00Z")
        val wallet = RecordingDemoWallet().apply {
            statuses.value = WalletDemoCredentialStatuses(nextRefreshAt = (start + 60.seconds).toString())
        }
        val controller = WalletDemoController(wallet, InMemoryDemoPinStore(), skipPin = true,
            scope = backgroundScope, dispatcher = StandardTestDispatcher(testScheduler),
            statusNow = { start + testScheduler.currentTime.milliseconds })
        runCurrent()
        assertEquals(listOf(false), wallet.statusRefreshModes)
        advanceTimeBy(59_999); runCurrent()
        assertEquals(1, wallet.statusRefreshModes.size)
        advanceTimeBy(1); runCurrent()
        assertEquals(2, wallet.statusRefreshModes.size)
        // A due time that remains in the past cannot cause a tight retry loop.
        advanceTimeBy(29_999); runCurrent()
        assertEquals(2, wallet.statusRefreshModes.size)
        advanceTimeBy(1); runCurrent()
        assertEquals(3, wallet.statusRefreshModes.size)
        controller.handleApplicationBackgrounded(); runCurrent()
        advanceTimeBy(120_000); runCurrent()
        assertEquals(3, wallet.statusRefreshModes.size)
        controller.setAutomaticCredentialStatusChecks(false)
        controller.handleApplicationForegrounded(); runCurrent()
        assertEquals(3, wallet.statusRefreshModes.size)
        controller.checkCredentialStatuses(); runCurrent()
        assertEquals(true, wallet.statusRefreshModes.last())
    }

    @Test
    fun newlyAdverseNoticeWaitsForConsentToEndAndNamesTheCredentialOnce() = runTest {
        val wallet = RecordingDemoWallet().apply {
            credentials = listOf(WalletDemoCredential("identity", "dc+sd-jwt", "Issuer", label = "Identity card"))
        }
        val settings = InMemoryDemoSharingSettingsStore().apply { setAutomaticCredentialStatusChecks(false) }
        val controller = WalletDemoController(wallet, InMemoryDemoPinStore(), sharingSettings = settings,
            skipPin = true, scope = backgroundScope,
            dispatcher = StandardTestDispatcher(testScheduler))
        val clear = WalletDemoCredentialStatus("NOT_REVOKED", "NONE", "FRESH", "CHECK", "clear")
        wallet.statuses.value = WalletDemoCredentialStatuses(mapOf("identity" to clear))
        runCurrent()
        controller.setExternalCredentialStatusReviewActive(true)
        wallet.statuses.value = WalletDemoCredentialStatuses(mapOf("identity" to
            clear.copy(kind = "REVOKED", issue = "REVOKED", advisoryRevision = "revoked")))
        runCurrent()
        assertEquals(null, controller.state.value.credentialStatusNotice)
        controller.setExternalCredentialStatusReviewActive(false); runCurrent()
        assertTrue(controller.state.value.credentialStatusNotice!!.contains("Identity card"))
        controller.dismissCredentialStatusNotice()
        wallet.statuses.value = wallet.statuses.value.copy(credentials = wallet.statuses.value.credentials.mapValues {
            it.value.copy(lastCheckedAt = "2026-11-12T12:00:00Z")
        })
        runCurrent()
        assertEquals(null, controller.state.value.credentialStatusNotice, "Check-time-only updates must not reannounce")
        val revoked = wallet.statuses.value.credentials.getValue("identity")
        wallet.statuses.value = WalletDemoCredentialStatuses(mapOf("identity" to revoked.copy(
            freshness = "STALE", advisoryRevision = "revoked-stale")))
        runCurrent()
        assertEquals(null, controller.state.value.credentialStatusNotice, "Evidence aging is not a new issuer status")
        wallet.statuses.value = WalletDemoCredentialStatuses(mapOf("identity" to clear)); runCurrent()
        wallet.statuses.value = WalletDemoCredentialStatuses(mapOf("identity" to revoked)); runCurrent()
        assertTrue(controller.state.value.credentialStatusNotice!!.contains("Identity card"),
            "A new adverse transition after recovery must be announced again")
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
        val dispatcher = StandardTestDispatcher(testScheduler)
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
    var credentials: List<WalletDemoCredential> = emptyList()
    val statuses = MutableStateFlow(WalletDemoCredentialStatuses())
    var statusCollectors = 0
    val statusRefreshModes = mutableListOf<Boolean>()
    override fun credentialStatusSnapshots() = flow {
        statusCollectors += 1
        try { emitAll(statuses) } finally { statusCollectors -= 1 }
    }
    override suspend fun credentialStatusSnapshot() = statuses.value
    override suspend fun refreshCredentialStatuses(ids: Set<String>?, force: Boolean): WalletDemoStatusRefreshSummary {
        statusRefreshModes += force
        return WalletDemoStatusRefreshSummary(0, 0, 0, 0)
    }

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
    override suspend fun beginAuthorizationIssuance(sessionId: String, credentials: List<WalletDemoCredentialSelection>) = error("unused")
    override suspend fun continuePreAuthorizedIssuance(sessionId: String, transactionCode: String?, credentials: List<WalletDemoCredentialSelection>) =
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

    override suspend fun listDeferredIssuance(): List<WalletDemoDeferredCredential> = emptyList()

    override suspend fun resumeDeferredIssuance(deferredCredentialId: String) = error("unused")
    override suspend fun previewPresentation(requestUrl: String) = error("unused")
    override suspend fun submitPresentation(
        previewHandle: WalletDemoPresentationPreviewHandle,
        selectedCredentialOptions: List<WalletDemoPresentationCredentialSelection>,
        selectedDisclosureOptions: List<WalletDemoPresentationDisclosureSelection>,
        did: String?,
        paymentConsentRevision: String?,
    ) = error("unused")
    override suspend fun rejectPresentation(previewHandle: WalletDemoPresentationPreviewHandle) =
        error("unused")
    override suspend fun discardPresentationPreview(previewHandle: WalletDemoPresentationPreviewHandle) = Unit
    override suspend fun deleteCredential(credentialId: String) = false
    override suspend fun deleteWallet() = Unit
}
