package id.walt.walletdemo.compose.logic

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Request-owned selection and consent. Hosts may be recreated without preparing another request. */
class WalletDemoSharingReviewController(
    val review: WalletDemoSharingReview,
    private val scope: CoroutineScope,
    private val prepare: (suspend (WalletDemoSharingSelection) -> WalletDemoPaymentConsent?)? = null,
) {
    data class State(val selection: WalletDemoSharingSelection, val payment: WalletDemoPaymentReview)

    private val mutableState = MutableStateFlow(State(
        WalletDemoSharingSelection(credentials = review.defaultCredentialSelection()),
        if (prepare == null) WalletDemoPaymentReview.NotRequired else WalletDemoPaymentReview.Loading,
    ))
    val state = mutableState.asStateFlow()
    private var preparation: Job? = null
    private var generation = 0
    private var closed = false

    init { refreshConsent() }

    fun toggleCredential(selection: WalletDemoPresentationCredentialSelection) {
        if (closed) return
        val option = review.credentialOptions.firstOrNull { it.selection == selection } ?: return
        updateSelection(state.value.selection.toggleCredential(selection, option))
    }

    fun toggleDisclosure(selection: WalletDemoPresentationDisclosureSelection) {
        if (closed) return
        val option = review.credentialOptions.firstOrNull {
            it.queryId == selection.queryId && it.credentialId == selection.credentialId
        } ?: return
        if (option.selection !in state.value.selection.credentials) return
        if (option.disclosures.none { it.path == selection.path && it.selectable }) return
        updateSelection(state.value.selection.toggleDisclosure(selection))
    }

    /** UI callers must supply the rendered state: a stale click must never approve newer choices. */
    fun selectionForSubmission(reviewedState: State = state.value): WalletDemoSharingSelection? {
        val current = state.value
        if (closed || current != reviewedState || !review.hasCompleteCredentialSelection(current.selection.credentials) || !current.payment.canConfirm) return null
        return current.selection.copy(paymentConsentRevision = current.payment.consent?.revision)
    }

    fun close() {
        closed = true
        generation++
        preparation?.cancel()
    }

    private fun updateSelection(selection: WalletDemoSharingSelection) {
        if (selection == state.value.selection) return
        mutableState.value = State(selection, if (prepare == null) WalletDemoPaymentReview.NotRequired else WalletDemoPaymentReview.Loading)
        refreshConsent()
    }

    private fun refreshConsent() {
        val revision = ++generation
        preparation?.cancel()
        val resolve = prepare ?: return
        val selection = state.value.selection
        if (!review.hasCompleteCredentialSelection(selection.credentials)) return
        preparation = scope.launch {
            val payment = try {
                resolve(selection)?.let { WalletDemoPaymentReview.Ready(it) } ?: WalletDemoPaymentReview.NotRequired
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: Exception) {
                WalletDemoPaymentReview.Blocked(cause.message ?: "Payment instructions are unavailable.")
            }
            currentCoroutineContext().ensureActive()
            if (!closed && generation == revision) mutableState.value = State(selection, payment)
        }
    }
}
