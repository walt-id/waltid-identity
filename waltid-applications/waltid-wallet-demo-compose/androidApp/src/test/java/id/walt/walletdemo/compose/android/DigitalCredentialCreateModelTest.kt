package id.walt.walletdemo.compose.android

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModelStore
import androidx.credentials.provider.PendingIntentHandler
import androidx.credentials.exceptions.CreateCredentialUnknownException
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.fragment.app.FragmentActivity
import id.walt.wallet2.handlers.WalletIssuanceAuthorization
import id.walt.wallet2.handlers.WalletIssuancePkceState
import id.walt.walletdemo.compose.logic.*
import id.walt.walletdemo.compose.ui.WalletDemoOfferCreateUiState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class DigitalCredentialCreateModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val owner = object : ViewModelStoreOwner { override val viewModelStore = store }
    private val context get() = RuntimeEnvironment.getApplication()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() {
        store.clear()
        DigitalCredentialCreateAuthHandoff.clear(context, "provider-session")
        Dispatchers.resetMain()
    }
    private fun model(client: Client) = ViewModelProvider(owner, viewModelFactory {
        initializer { DigitalCredentialCreateModel(context, client) }
    })[DigitalCredentialCreateModel::class.java]

    @Test fun selectedBatchSubmitsOnceAndItsPartialReceiptSurvivesRecreationUntilDone() = runTest(dispatcher) {
        val client = Client()
        val pending = WalletDemoDeferredCredential("pending", "mdl", 5, status = WalletDemoContinuationStatus.AwaitingIssuer)
        client.pending = listOf(pending)
        client.outcome = WalletDemoIssuanceOutcome.Failed("Later target failed", listOf("saved"), listOf(pending),
            offerConsumed = true, failedTargetCount = 1, notAttemptedTargetCount = 2)
        client.acceptGate = CompletableDeferred()
        val first = model(client)
        first.start(Intent(), false); runCurrent()
        first.draft.transactionCode = "1234"
        first.draft.copies = mapOf("pid" to 3, "mdl" to 0)
        first.accept("1234", first.draft.copies)
        first.accept("1234", first.draft.copies)
        runCurrent()
        assertEquals(1, client.accepts)
        assertEquals("1234", client.code)
        assertEquals(listOf(WalletDemoCredentialSelection("pid", WalletDemoCredentialHolders.NewKeys(3))), client.selections)
        first.cancel(); first.back()
        assertNull(first.takeResult()) // Busy cannot dismiss a consumed request.
        val recreated = model(client)
        recreated.start(Intent(), true)
        assertSame(first, recreated)
        assertEquals("1234", recreated.draft.transactionCode)
        client.acceptGate!!.complete(Unit); runCurrent()
        val receipt = assertIs<WalletDemoOfferCreateUiState.Receipt>(recreated.state)
        assertEquals(listOf("saved"), receipt.saved.map { it.id })
        assertEquals(listOf("pending"), receipt.pending.map { it.id })
        assertEquals(2, receipt.receipt.problem?.notAttemptedTargetCount)
        assertNull(recreated.takeResult())
        recreated.done()
        val result = assertNotNull(recreated.takeResult())
        assertEquals(Activity.RESULT_OK, result.code)
        assertIs<CreateCredentialUnknownException>(PendingIntentHandler.retrieveCreateCredentialException(assertNotNull(result.data)))
        assertNull(first.takeResult())
        store.clear(); runCurrent()
        assertEquals(0, client.cancels) // Retained pending work must remain available in the wallet.
    }

    @Test fun browserReturnUsesTheSameBatchAndIgnoresUnrelatedOrDuplicateCallbacks() = runTest(dispatcher) {
        val client = Client(WalletDemoIssuanceGrant.AuthorizationCode)
        val model = model(client)
        val host = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        model.attach(host.get())
        model.start(Intent(), false); runCurrent()
        model.accept(null, mapOf("pid" to 0, "mdl" to 2)); runCurrent()
        assertIs<WalletDemoOfferCreateUiState.WaitingForAuthorization>(model.state)
        assertEquals(listOf(WalletDemoCredentialSelection("mdl", WalletDemoCredentialHolders.NewKeys(2))), client.selections)
        assertEquals(Intent.ACTION_VIEW, assertNotNull(shadowOf(host.get()).nextStartedActivity).action)
        assertEquals(DigitalCredentialCreateAuthHandoff.Delivery.Unmatched,
            DigitalCredentialCreateAuthHandoff.deliver(context, Uri.parse("openid://?code=abc&state=wrong")))
        assertNull(shadowOf(host.get()).nextStartedActivity)
        listOf(DigitalCredentialCreateAuthHandoff.Delivery.Live, DigitalCredentialCreateAuthHandoff.Delivery.Duplicate).forEach { expected ->
            assertEquals(expected, DigitalCredentialCreateAuthHandoff.deliver(context, Uri.parse("openid://?code=abc&state=expected")))
        }
        runCurrent()
        assertEquals(1, client.callbacks)
        assertEquals(0, client.accepts)
        assertIs<WalletDemoOfferCreateUiState.Receipt>(model.state)
        val resume = assertNotNull(shadowOf(host.get()).nextStartedActivity)
        assertEquals(DigitalCredentialCreateActivity::class.java.name, resume.component?.className)
        assertEquals(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP, resume.flags)
        assertNull(shadowOf(host.get()).nextStartedActivity)
        model.done(); assertNotNull(model.takeResult())
        store.clear(); runCurrent()
        host.pause().stop().destroy()
    }

    @Test fun refreshDoesNotRepeatIssuanceAndUncertainWorkCannotBeResumed() = runTest(dispatcher) {
        val client = Client()
        val pending = WalletDemoDeferredCredential("pending", "mdl", 5, status = WalletDemoContinuationStatus.AwaitingIssuer)
        client.pending = listOf(pending)
        client.outcome = WalletDemoIssuanceOutcome.Deferred(listOf("saved"), listOf(pending))
        val model = model(client)
        model.start(Intent(), false); runCurrent()
        model.accept(null, emptyMap()); runCurrent()
        model.refresh(); runCurrent()
        assertEquals(1, client.accepts); assertEquals(0, client.resumes)
        client.pending = listOf(pending.copy(status = WalletDemoContinuationStatus.RemoteOutcomeUncertain))
        model.refresh(); runCurrent()
        model.resume("pending"); runCurrent()
        assertEquals(0, client.resumes)
        client.pending = listOf(pending.copy(status = WalletDemoContinuationStatus.AwaitingLocalSave))
        model.refresh(); runCurrent()
        client.outcome = WalletDemoIssuanceOutcome.Stored(listOf("second"))
        client.saved += credential("second")
        client.pending = emptyList()
        model.resume("pending"); runCurrent()
        val receipt = assertIs<WalletDemoOfferCreateUiState.Receipt>(model.state)
        assertEquals(setOf("saved", "second"), receipt.saved.map { it.id }.toSet())
        assertTrue(receipt.pending.isEmpty())
        assertEquals(1, client.resumes)
        model.done(); store.clear(); runCurrent()
    }

    @Test fun recoverableCodeFailureRetainsTheDraftAndFailedDetailsRefreshDoesNotLoseTheSavedIds() = runTest(dispatcher) {
        val client = Client()
        client.outcome = WalletDemoIssuanceOutcome.Failed("Incorrect code")
        val model = model(client)
        model.start(Intent(), false); runCurrent()
        model.draft.transactionCode = "1234"
        model.accept("1234", emptyMap()); runCurrent()
        assertEquals("Incorrect code", assertIs<WalletDemoOfferCreateUiState.Review>(model.state).errorMessage)
        assertEquals("1234", model.draft.transactionCode)
        client.outcome = WalletDemoIssuanceOutcome.Stored(listOf("saved"))
        client.credentialsFail = true
        model.accept("5678", emptyMap()); runCurrent()
        assertNotNull(assertIs<WalletDemoOfferCreateUiState.Receipt>(model.state).refreshError)
        client.credentialsFail = false
        model.refresh(); runCurrent()
        assertEquals(listOf("saved"), assertIs<WalletDemoOfferCreateUiState.Receipt>(model.state).saved.map { it.id })
        assertEquals(2, client.accepts)
        model.done(); store.clear(); runCurrent()
    }

    @Test fun losingTheHostDuringRemoteWorkPreservesSdkRecoveryInsteadOfCancellingTheSession() = runTest(dispatcher) {
        val client = Client().also { it.acceptGate = CompletableDeferred() }
        val model = model(client)
        model.start(Intent(), false); runCurrent()
        model.accept(null, emptyMap()); runCurrent()
        store.clear(); runCurrent()
        assertEquals(1, client.accepts)
        assertEquals(0, client.cancels)
        assertNull(model.takeResult())
    }

    private class Client(grant: WalletDemoIssuanceGrant = WalletDemoIssuanceGrant.PreAuthorizedCode) : DigitalCredentialCreateClient {
        val session = WalletDemoIssuanceSession("provider-session", grant, WalletDemoOfferPreview(
            WalletDemoIssuerMetadata("https://issuer.example", null), listOf("pid", "mdl").map {
                WalletDemoOfferedCredentialMetadata(it, "mso_mdoc", null, null, null, emptyList())
            }, transactionCode = null, batchSize = 3))
        var outcome: WalletDemoIssuanceOutcome = WalletDemoIssuanceOutcome.Stored(listOf("saved"))
        var saved = listOf(credential("saved"))
        var pending = emptyList<WalletDemoDeferredCredential>()
        var credentialsFail = false
        var selections = emptyList<WalletDemoCredentialSelection>()
        var code: String? = null
        var accepts = 0; var callbacks = 0; var resumes = 0; var cancels = 0
        var acceptGate: CompletableDeferred<Unit>? = null
        override suspend fun prepare(intent: Intent) = DigitalCredentialCreateClient.Offer("openid4vci-v1", session, WalletDemoHolderBinding("key"))
        override suspend fun accept(id: String, code: String?, selections: List<WalletDemoCredentialSelection>): WalletDemoIssuanceOutcome {
            accepts++; this.code = code; this.selections = selections
            acceptGate?.await()
            return outcome
        }
        override suspend fun authorize(id: String, selections: List<WalletDemoCredentialSelection>): WalletIssuanceAuthorization {
            this.selections = selections
            return WalletIssuanceAuthorization("https://issuer.example/authorize", "expected", "openid://", WalletIssuancePkceState("challenge", "S256"), false)
        }
        override suspend fun continueAuthorization(id: String, callback: String): WalletDemoIssuanceOutcome { callbacks++; return outcome }
        override suspend fun cancel(id: String) { cancels++ }
        override suspend fun resume(id: String): WalletDemoIssuanceOutcome { resumes++; return outcome }
        override suspend fun credentials(): List<WalletDemoCredential> { check(!credentialsFail); return saved }
        override suspend fun continuations() = pending
    }

    private companion object {
        fun credential(id: String) = WalletDemoCredential(id, "mso_mdoc", "Issuer", label = "Driving licence", addedAt = "2026-01-01", credentialDataJson = "{}")
    }
}
