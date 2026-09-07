package id.walt.wallet2.mobile

import id.walt.mdoc.proximity.MdocConsentDecision
import id.walt.mdoc.proximity.MdocConsentHandler
import id.walt.mdoc.proximity.MdocConsentPrompt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Orders session observations and holder decisions; external work runs outside this owner lock. */
internal class MobileWalletProximitySessionOwner(
    initial: MobileWalletProximityState.CheckingPrerequisites,
    private val prerequisiteRetry: Channel<Unit>,
) : MdocConsentHandler {
    private data class Pending(
        val prompt: MdocConsentPrompt,
        val reviewId: MobileWalletProximityReviewId,
        val decision: CompletableDeferred<MdocConsentDecision>,
    )

    private val mutex = Mutex()
    private var current: MobileWalletProximityState = initial.snapshot()
    private val mutableState = MutableStateFlow(current.snapshot())
    val state: StateFlow<MobileWalletProximityState> = mutableState.asStateFlow()
    private var processor: MobileWalletProximityRequestProcessor? = null
    private var pending: Pending? = null

    suspend fun attach(processor: MobileWalletProximityRequestProcessor) = mutex.withLock {
        check(this.processor == null)
        if (current.isTerminal) {
            processor.cancel()
            throw CancellationException("The proximity session is already terminal")
        }
        this.processor = processor
    }

    suspend fun publish(next: MobileWalletProximityState) = mutex.withLock { publishLocked(next) }

    private suspend fun publishLocked(next: MobileWalletProximityState): Boolean {
        if (current.isTerminal || next.position() < current.position()) return false
        if (next.isTerminal) {
            pending?.decision?.cancel()
            pending = null
            processor?.cancel()
        }
        current = next.snapshot()
        mutableState.value = current.snapshot()
        return true
    }

    override suspend fun decide(prompt: MdocConsentPrompt): MdocConsentDecision {
        val review = requireNotNull(processor).review(prompt)
        val waiting = Pending(prompt, review.reviewId, CompletableDeferred())
        mutex.withLock {
            if (current.isTerminal) throw CancellationException("The proximity session is terminal")
            check(pending == null) { "A previous proximity review is still awaiting a decision" }
            pending = waiting
            check(publishLocked(MobileWalletProximityState.ReviewRequired(review))) {
                "An obsolete proximity review cannot replace a newer session phase"
            }
        }
        return try {
            waiting.decision.await()
        } finally {
            mutex.withLock { if (pending === waiting) pending = null }
        }
    }

    suspend fun dispatch(action: MobileWalletProximityAction): MobileWalletProximityActionResult {
        // This must precede even lock acquisition: the caller can mutate a submitted collection while suspended.
        val owned = try {
            if (action is MobileWalletProximityAction.Approve) action.copy(submission = action.submission.snapshot()) else action
        } catch (_: IllegalArgumentException) {
            return rejectedAction("invalid_submission", "The proximity submission is invalid")
        }
        return mutex.withLock {
            when (owned) {
                MobileWalletProximityAction.Cancel -> {
                    if (MobileWalletProximityActionType.Cancel !in current.legalActions) {
                        return@withLock rejectedAction("cancel_not_allowed", "Cancellation is not allowed in the current state")
                    }
                    publishLocked(MobileWalletProximityState.Cancelled)
                }
                MobileWalletProximityAction.RetryPrerequisites -> {
                    if (current !is MobileWalletProximityState.CheckingPrerequisites) {
                        return@withLock rejectedAction("prerequisite_retry_not_allowed", "Prerequisites are not awaiting remediation")
                    }
                    if (prerequisiteRetry.trySend(Unit).isFailure) {
                        return@withLock rejectedAction("session_closed", "The proximity session is closed")
                    }
                }
                is MobileWalletProximityAction.ReportRemediation -> {
                    val checking = current as? MobileWalletProximityState.CheckingPrerequisites
                        ?: return@withLock rejectedAction("remediation_result_not_allowed", "No prerequisite remediation is pending")
                    if (owned.action !in checking.capabilities.remediationActions) {
                        return@withLock rejectedAction("unexpected_remediation_result", "The reported remediation was not requested")
                    }
                    if (owned.result == MobileWalletProximityHostActionResult.Completed && prerequisiteRetry.trySend(Unit).isFailure) {
                        return@withLock rejectedAction("session_closed", "The proximity session is closed")
                    }
                }
                is MobileWalletProximityAction.Approve, is MobileWalletProximityAction.Decline -> {
                    val waiting = pending
                        ?: return@withLock rejectedAction("stale_action", "No proximity review is awaiting this action")
                    val id = when (owned) {
                        is MobileWalletProximityAction.Approve -> owned.reviewId
                        is MobileWalletProximityAction.Decline -> owned.reviewId
                    }
                    if (id != waiting.reviewId || current !is MobileWalletProximityState.ReviewRequired) {
                        return@withLock rejectedAction("stale_action", "The action belongs to another proximity review")
                    }
                    val processor = requireNotNull(processor)
                    val decision = when (owned) {
                        is MobileWalletProximityAction.Approve -> {
                            processor.accept(waiting.prompt, id, owned.submission)?.let {
                                return@withLock MobileWalletProximityActionResult.Rejected(it)
                            }
                            val authorization = processor.holderAuthorization(id)
                            check(publishLocked(MobileWalletProximityState.AuthorizingHolderKey(authorization)))
                            MdocConsentDecision.Approve(waiting.prompt.bindingToken)
                        }
                        is MobileWalletProximityAction.Decline -> {
                            processor.cancel()
                            check(publishLocked(MobileWalletProximityState.Terminating(waiting.prompt.exchange)))
                            MdocConsentDecision.Deny(waiting.prompt.bindingToken)
                        }
                    }
                    pending = null
                    check(waiting.decision.complete(decision)) { "The accepted proximity decision was already consumed" }
                }
            }
            MobileWalletProximityActionResult.Accepted
        }
    }

    suspend fun cancel() = mutex.withLock {
        publishLocked(MobileWalletProximityState.Cancelled)
        pending?.decision?.cancel()
        pending = null
        processor?.cancel()
    }
}

private val MobileWalletProximityState.isTerminal: Boolean
    get() = this is MobileWalletProximityState.Completed || this is MobileWalletProximityState.Failed ||
        this is MobileWalletProximityState.Cancelled

/** Exchange and phase order rejects delayed observations even when they acquire the lock later. */
private fun MobileWalletProximityState.position(): Long = when (this) {
    is MobileWalletProximityState.CheckingPrerequisites -> 0
    is MobileWalletProximityState.Preparing -> 1
    is MobileWalletProximityState.EngagementReady -> 2
    is MobileWalletProximityState.Connecting -> 3
    is MobileWalletProximityState.AwaitingRequest -> exchange.toLong() * 10
    is MobileWalletProximityState.ReviewRequired -> review.exchange.toLong() * 10 + 1
    is MobileWalletProximityState.AuthorizingHolderKey -> authorization.exchange.toLong() * 10 + 2
    is MobileWalletProximityState.SendingResponse -> exchange.toLong() * 10 + 3
    is MobileWalletProximityState.AwaitingNextRequest -> completedExchanges.toLong() * 10 + 4
    is MobileWalletProximityState.Terminating -> exchange.toLong() * 10 + 5
    is MobileWalletProximityState.Completed -> exchanges.toLong() * 10 + 6
    is MobileWalletProximityState.Cancelled, is MobileWalletProximityState.Failed -> Long.MAX_VALUE
}

private fun MobileWalletProximityTransportCapability.snapshot() = copy(
    runtime = when (val observation = runtime) {
        is MobileWalletProximityRuntimeObservation.Unavailable -> observation.copy(remediationActions = observation.remediationActions.toList())
        else -> observation
    },
)

private fun MobileWalletProximityCapabilities.snapshot() = copy(
    qrEngagement = qrEngagement.snapshot(), nfcEngagement = nfcEngagement.snapshot(),
    bluetoothLowEnergy = bluetoothLowEnergy.snapshot(), nfcRetrieval = nfcRetrieval.snapshot(),
    wifiAwareRetrieval = wifiAwareRetrieval.snapshot(),
)

private fun MobileWalletProximityState.snapshot(): MobileWalletProximityState = when (this) {
    is MobileWalletProximityState.CheckingPrerequisites -> copy(capabilities = capabilities.snapshot())
    is MobileWalletProximityState.EngagementReady -> copy(engagements = engagements.toList())
    is MobileWalletProximityState.Connecting -> copy(engagements = engagements.toList())
    is MobileWalletProximityState.ReviewRequired -> copy(review = review.snapshot())
    is MobileWalletProximityState.AuthorizingHolderKey -> copy(authorization = authorization.copy(requests = authorization.requests.toList()))
    else -> this
}
