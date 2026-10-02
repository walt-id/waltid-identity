package id.walt.walletdemo.compose.android

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.credentials.ExperimentalDigitalCredentialApi
import id.walt.wallet2.mobile.AndroidDigitalCredentialCreateProvider
import id.walt.wallet2.mobile.AndroidDigitalCredentialCreateResponse
import id.walt.walletdemo.compose.logic.*
import id.walt.walletdemo.compose.ui.WalletDemoOfferCreateUiState
import id.walt.walletdemo.compose.ui.WalletDemoOfferDraft
import id.walt.walletdemo.compose.ui.prefetchOfferCardArt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** One issuance session, including its browser handoff and receipt, survives host recreation. */
@OptIn(ExperimentalDigitalCredentialApi::class)
internal class DigitalCredentialCreateModel(
    context: Context,
    client: DigitalCredentialCreateClient? = null,
) : DigitalCredentialActivityModel() {
    private val context = context.applicationContext
    private val client = client ?: MobileDigitalCredentialCreateClient(context, ::interactionActivity)
    private var session: WalletDemoIssuanceSession? = null
    private var holder: WalletDemoHolderBinding? = null
    private var protocol: String? = null
    private var completion: Intent? = null
    // Cancellation must not discard SDK recovery handles after a remote operation has begun.
    private var preserveContinuations = false
    private var receivedIds = emptySet<String>()
    val draft = WalletDemoOfferDraft()
    var state by mutableStateOf<WalletDemoOfferCreateUiState>(WalletDemoOfferCreateUiState.Loading)
        private set

    override suspend fun prepare(intent: Intent) {
        val prepared = client.prepare(intent)
        protocol = prepared.protocol
        holder = prepared.holder
        val started = prepared.session
        session = started
        currentCoroutineContext().ensureActive()
        if (result != null || released) return
        prefetchOfferCardArt(context, started.preview)
        currentCoroutineContext().ensureActive()
        if (result == null && !released) state = WalletDemoOfferCreateUiState.Review(started.preview)
    }

    fun accept(txCode: String?, copies: Map<String, Int>) {
        val review = state as? WalletDemoOfferCreateUiState.Review ?: return
        val started = session ?: return
        if (review.submitting || result != null) return
        state = review.copy(submitting = true, errorMessage = null)
        perform {
            val selections = started.preview.credentialSelections(copies, requireNotNull(holder))
            when (started.grant) {
                WalletDemoIssuanceGrant.PreAuthorizedCode -> {
                    preserveContinuations = true
                    completeOutcome(client.accept(started.id, txCode, selections))
                }
                WalletDemoIssuanceGrant.AuthorizationCode -> {
                    val authorization = client.authorize(started.id, selections)
                    currentCoroutineContext().ensureActive()
                    DigitalCredentialCreateAuthHandoff.register(context, started.id, authorization.state, authorization.redirectUri) { uri ->
                        scope.launch { awaitCurrentOperation(); continueAuthorization(uri) }
                    }
                    state = WalletDemoOfferCreateUiState.WaitingForAuthorization()
                    DigitalCredentialCreateAuthHandoff.openExternalBrowser(context, authorization.url)
                }
            }
        }
    }

    private fun continueAuthorization(callback: String) {
        val waiting = state as? WalletDemoOfferCreateUiState.WaitingForAuthorization ?: return
        val id = session?.id ?: return
        if (waiting.completing || result != null || released) return
        state = waiting.copy(completing = true)
        preserveContinuations = true
        perform {
            try { completeOutcome(client.continueAuthorization(id, callback)) }
            finally { DigitalCredentialCreateAuthHandoff.clear(context, id) }
        }
    }

    fun cancel() {
        if (busy()) return
        if (state is WalletDemoOfferCreateUiState.Receipt) { done(); return }
        val data = Intent()
        if (state is WalletDemoOfferCreateUiState.Failure) AndroidDigitalCredentialCreateProvider.setFailure(data)
        else AndroidDigitalCredentialCreateProvider.setCancellation(data)
        finish(data)
    }

    /** Back returns to the provider picker; explicit Cancel ends the caller's operation. */
    fun back() {
        if (busy()) return
        if (state is WalletDemoOfferCreateUiState.Receipt) done() else finish()
    }

    fun done() {
        if ((state as? WalletDemoOfferCreateUiState.Receipt)?.busy != false) return
        completion?.let(::finish)
    }

    fun resume(id: String) {
        val previous = state as? WalletDemoOfferCreateUiState.Receipt ?: return
        if (previous.busy || previous.pending.none { it.id == id && it.status.canResume }) return
        receiptOperation(previous) { completeOutcome(client.resume(id), previous, id) }
    }

    /** Refresh only reads retained state; it never repeats an issuance request. */
    fun refresh() {
        val previous = state as? WalletDemoOfferCreateUiState.Receipt ?: return
        if (previous.busy) return
        receiptOperation(previous) {
            val pending = client.continuations()
                .filter { it.id in previous.receipt.pendingIds }
            val saved = client.credentials().filter { it.id in receivedIds }
            currentCoroutineContext().ensureActive()
            state = previous.copy(saved = saved, pending = pending, refreshError = null)
        }
    }

    private fun receiptOperation(previous: WalletDemoOfferCreateUiState.Receipt, action: suspend () -> Unit) {
        state = previous.copy(busy = true, refreshError = null)
        perform {
            try { action() }
            catch (cause: CancellationException) { throw cause }
            catch (cause: Exception) { state = previous.copy(refreshError = cause.message ?: "Could not refresh this result.") }
        }
    }

    private suspend fun completeOutcome(
        mapped: WalletDemoIssuanceOutcome,
        previous: WalletDemoOfferCreateUiState.Receipt? = null,
        resumingId: String? = null,
    ) {
        // Store changes are observable even when a later target fails.
        WalletDemoCredentialStoreNotifier.notifyChanged()
        var refreshError: String? = null
        val latest = try { client.continuations() }
        catch (cause: CancellationException) { throw cause }
        catch (cause: Exception) {
            refreshError = "Could not refresh pending credentials. Check the wallet before starting another request."
            emptyList()
        }
        val outcome = mapped.withContinuations(latest, resumingId)
        if (previous == null && session?.grant == WalletDemoIssuanceGrant.PreAuthorizedCode &&
            outcome is WalletDemoIssuanceOutcome.Failed && !outcome.offerConsumed &&
            outcome.storedCredentialIds.isEmpty() && outcome.deferredCredentials.isEmpty()) {
            preserveContinuations = false
            state = WalletDemoOfferCreateUiState.Review(requireNotNull(session).preview, errorMessage = outcome.message)
            return
        }
        val newIds: List<String>
        val newPending: List<WalletDemoDeferredCredential>
        val problem: WalletDemoIssuanceProblem?
        when (outcome) {
            is WalletDemoIssuanceOutcome.Stored -> { newIds = outcome.credentialIds; newPending = emptyList(); problem = null }
            is WalletDemoIssuanceOutcome.Deferred -> { newIds = outcome.storedCredentialIds; newPending = outcome.credentials; problem = null }
            is WalletDemoIssuanceOutcome.Failed -> { newIds = outcome.storedCredentialIds; newPending = outcome.deferredCredentials; problem = outcome.problem() }
            WalletDemoIssuanceOutcome.Cancelled -> {
                if (previous == null) {
                    finish(Intent().also { AndroidDigitalCredentialCreateProvider.setCancellation(it) })
                    return
                }
                newIds = emptyList(); newPending = emptyList(); problem = null
            }
        }
        receivedIds = receivedIds + newIds
        val ids = receivedIds
        val pending = previous?.pending.orEmpty().filterNot { it.id == resumingId } + newPending
        val saved = try { client.credentials().filter { it.id in ids } }
        catch (cause: CancellationException) { throw cause }
        catch (cause: Exception) {
            refreshError = "Could not load saved credential details. Check the wallet before starting another request."
            previous?.saved.orEmpty()
        }
        currentCoroutineContext().ensureActive()
        if (completion == null) completion = Intent().also { data ->
            if (outcome is WalletDemoIssuanceOutcome.Failed) AndroidDigitalCredentialCreateProvider.setFailure(data)
            else AndroidDigitalCredentialCreateProvider.setResponse(data, AndroidDigitalCredentialCreateResponse.acknowledgment(
                protocol ?: AndroidDigitalCredentialCreateResponse.acknowledgment().protocol,
            ))
        }
        state = WalletDemoOfferCreateUiState.Receipt(
            receipt = WalletDemoIssuanceReceipt(session?.preview?.issuer, pending.map { it.id }.toSet(), previous?.receipt?.problem ?: problem),
            saved = saved, pending = pending.distinctBy { it.id },
            refreshError = refreshError ?: if (saved.size < ids.size) "Some saved credential details could not be loaded. Check the wallet before starting another request." else null,
        )
    }

    private fun busy(): Boolean = when (val current = state) {
        is WalletDemoOfferCreateUiState.Review -> current.submitting
        is WalletDemoOfferCreateUiState.WaitingForAuthorization -> current.completing
        is WalletDemoOfferCreateUiState.Receipt -> current.busy
        else -> false
    }

    override fun showFailure(cause: Exception) {
        Log.e("WaltDigitalCredentials", "Digital credential issuance failed (${cause::class.simpleName})", cause)
        session?.id?.let { DigitalCredentialCreateAuthHandoff.clear(context, it) }
        state = WalletDemoOfferCreateUiState.Failure(cause.message ?: "The request could not be completed.")
    }

    override suspend fun releaseRequest() {
        session?.id?.let { id ->
            DigitalCredentialCreateAuthHandoff.clear(context, id)
            // Leave both completed and interrupted remote work for the SDK continuation path.
            if (!preserveContinuations) runCatching { client.cancel(id) }
        }
    }
}
