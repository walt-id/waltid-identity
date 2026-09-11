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
internal class ProximitySessionOwner(
    initial: ProximityState.CheckingPrerequisites,
    private val prerequisiteRetry: Channel<Unit>,
) : MdocConsentHandler {
    private data class Pending(
        val prompt: MdocConsentPrompt,
        val reviewId: ProximityReviewId,
        val decision: CompletableDeferred<MdocConsentDecision>,
    )

    private val mutex = Mutex()
    private var current: ProximityState = initial.snapshot()
    private val mutableState = MutableStateFlow(current.snapshot())
    val state: StateFlow<ProximityState> = mutableState.asStateFlow()
    private var processor: ProximityRequestProcessor? = null
    private var pending: Pending? = null

    suspend fun attach(processor: ProximityRequestProcessor) = mutex.withLock {
        check(this.processor == null)
        if (current.isTerminal) {
            processor.cancel()
            throw CancellationException("The proximity session is already terminal")
        }
        this.processor = processor
    }

    suspend fun publish(next: ProximityState) = mutex.withLock { publishLocked(next) }

    private suspend fun publishLocked(next: ProximityState): Boolean {
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
            check(publishLocked(ProximityState.ReviewRequired(review))) {
                "An obsolete proximity review cannot replace a newer session phase"
            }
        }
        return try {
            waiting.decision.await()
        } finally {
            mutex.withLock { if (pending === waiting) pending = null }
        }
    }

    suspend fun dispatch(action: ProximityAction): ProximityActionResult {
        // This must precede even lock acquisition: the caller can mutate a submitted collection while suspended.
        val owned = try {
            if (action is ProximityAction.Approve) action.copy(submission = action.submission.snapshot()) else action
        } catch (_: IllegalArgumentException) {
            return rejectedAction("invalid_submission", "The proximity submission is invalid")
        }
        return mutex.withLock {
            when (owned) {
                ProximityAction.Cancel -> {
                    if (ProximityActionType.Cancel !in current.legalActions) {
                        return@withLock rejectedAction("cancel_not_allowed", "Cancellation is not allowed in the current state")
                    }
                    publishLocked(ProximityState.Cancelled)
                }
                ProximityAction.RetryPrerequisites -> {
                    if (current !is ProximityState.CheckingPrerequisites) {
                        return@withLock rejectedAction("prerequisite_retry_not_allowed", "Prerequisites are not awaiting remediation")
                    }
                    if (prerequisiteRetry.trySend(Unit).isFailure) {
                        return@withLock rejectedAction("session_closed", "The proximity session is closed")
                    }
                }
                is ProximityAction.ReportRemediation -> {
                    val checking = current as? ProximityState.CheckingPrerequisites
                        ?: return@withLock rejectedAction("remediation_result_not_allowed", "No prerequisite remediation is pending")
                    if (owned.action !in checking.capabilities.remediationActions) {
                        return@withLock rejectedAction("unexpected_remediation_result", "The reported remediation was not requested")
                    }
                    if (owned.result == ProximityHostActionResult.Completed && prerequisiteRetry.trySend(Unit).isFailure) {
                        return@withLock rejectedAction("session_closed", "The proximity session is closed")
                    }
                }
                is ProximityAction.Approve, is ProximityAction.Decline -> {
                    val waiting = pending
                        ?: return@withLock rejectedAction("stale_action", "No proximity review is awaiting this action")
                    val id = when (owned) {
                        is ProximityAction.Approve -> owned.reviewId
                        is ProximityAction.Decline -> owned.reviewId
                    }
                    if (id != waiting.reviewId || current !is ProximityState.ReviewRequired) {
                        return@withLock rejectedAction("stale_action", "The action belongs to another proximity review")
                    }
                    val processor = requireNotNull(processor)
                    val decision = when (owned) {
                        is ProximityAction.Approve -> {
                            processor.accept(waiting.prompt, id, owned.submission)?.let {
                                return@withLock ProximityActionResult.Rejected(it)
                            }
                            val authorization = processor.holderAuthorization(id)
                            check(publishLocked(ProximityState.AuthorizingHolderKey(authorization)))
                            MdocConsentDecision.Approve(waiting.prompt.bindingToken)
                        }
                        is ProximityAction.Decline -> {
                            processor.cancel()
                            check(publishLocked(ProximityState.Terminating(waiting.prompt.exchange)))
                            MdocConsentDecision.Deny(waiting.prompt.bindingToken)
                        }
                    }
                    pending = null
                    check(waiting.decision.complete(decision)) { "The accepted proximity decision was already consumed" }
                }
            }
            ProximityActionResult.Accepted
        }
    }

    suspend fun cancel() = mutex.withLock {
        publishLocked(ProximityState.Cancelled)
        pending?.decision?.cancel()
        pending = null
        processor?.cancel()
    }
}

private val ProximityState.isTerminal: Boolean
    get() = this is ProximityState.Completed || this is ProximityState.NoData || this is ProximityState.Failed ||
        this is ProximityState.Cancelled

/** Exchange and phase order rejects delayed observations even when they acquire the lock later. */
private fun ProximityState.position(): Long = when (this) {
    is ProximityState.CheckingPrerequisites -> 0
    is ProximityState.Preparing -> 1
    is ProximityState.EngagementReady -> 2
    is ProximityState.Connecting -> 3
    is ProximityState.AwaitingRequest -> exchange.toLong() * 10
    is ProximityState.ReviewRequired -> review.exchange.toLong() * 10 + 1
    is ProximityState.AuthorizingHolderKey -> authorization.exchange.toLong() * 10 + 2
    is ProximityState.SendingResponse -> exchange.toLong() * 10 + 3
    is ProximityState.AwaitingNextRequest -> completedExchanges.toLong() * 10 + 4
    is ProximityState.Terminating -> exchange.toLong() * 10 + 5
    is ProximityState.Completed -> exchanges.toLong() * 10 + 6
    is ProximityState.NoData -> exchange.toLong() * 10 + 6
    is ProximityState.Cancelled, is ProximityState.Failed -> Long.MAX_VALUE
}

private fun ProximityTransportCapability.snapshot() = copy(
    runtime = when (val observation = runtime) {
        is ProximityRuntimeObservation.Unavailable -> observation.copy(remediationActions = observation.remediationActions.toList())
        else -> observation
    },
)

private fun ProximityCapabilities.snapshot() = copy(
    qrEngagement = qrEngagement.snapshot(), nfcEngagement = nfcEngagement.snapshot(),
    bluetoothLowEnergy = bluetoothLowEnergy.snapshot(), nfcRetrieval = nfcRetrieval.snapshot(),
    wifiAwareRetrieval = wifiAwareRetrieval.snapshot(),
)

private fun ProximityState.snapshot(): ProximityState = when (this) {
    is ProximityState.CheckingPrerequisites -> copy(capabilities = capabilities.snapshot())
    is ProximityState.EngagementReady -> copy(engagements = engagements.toList())
    is ProximityState.Connecting -> copy(engagements = engagements.toList())
    is ProximityState.ReviewRequired -> copy(review = review.snapshot())
    is ProximityState.AuthorizingHolderKey -> copy(authorization = authorization.copy(requests = authorization.requests.toList()))
    else -> this
}
