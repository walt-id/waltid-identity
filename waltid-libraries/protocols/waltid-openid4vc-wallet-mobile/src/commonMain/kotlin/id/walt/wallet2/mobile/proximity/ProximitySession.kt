package id.walt.wallet2.mobile.proximity

import kotlinx.coroutines.flow.StateFlow

/** User or host action accepted by a proximity session. */
public sealed interface ProximityAction {
    /** Approves the current immutable review with the exact [submission]. */
    public data class Approve(
        /** Identity of the review the holder approved. */
        public val reviewId: ProximityReviewId,
        /** Holder-approved credential and disclosure choices. */
        public val submission: ProximitySubmission,
    ) :
        ProximityAction

    /** Declines the current review and terminates the session without disclosure. */
    public data class Decline(
        /** Identity of the review the holder declined. */
        public val reviewId: ProximityReviewId,
    ) : ProximityAction

    /** Cancels the active session and releases its resources. */
    public data object Cancel : ProximityAction

    /** Rechecks side-effect-free prerequisites after host remediation. */
    public data object RetryPrerequisites : ProximityAction

    /** Reports the privacy-safe outcome of a requested host remediation. */
    public data class ReportRemediation(
        /** Remediation action whose outcome is being reported. */
        public val action: ProximityRemediationAction,
        /** Privacy-safe outcome reported by the host. */
        public val result: ProximityHostActionResult,
    ) : ProximityAction
}

/** Privacy-safe outcome of a system surface performed by the host application. */
public enum class ProximityHostActionResult {
    Completed,
    Cancelled,
    Failed,
}

/** Prepared engagement presented by the host UI. */
public sealed interface ProximityEngagement {
    /** ISO mdoc device-engagement URI for QR rendering. */
    public data class Qr(
        /** Complete `mdoc:` URI that the host must encode without transformation. */
        public val payload: String,
    ) : ProximityEngagement {
        init { require(payload.startsWith("mdoc:")) }
    }

    /** NFC engagement prepared by the platform transport. */
    public data object Nfc : ProximityEngagement
}

/**
 * One protected-key operation required by a frozen approved document response.
 *
 * @property requestIndex Reviewed document request requiring authorization.
 * @property credentialId Credential whose protected holder key will be used.
 * @property deviceAuthentication Frozen signature or MAC operation.
 */
public data class ProximityHolderAuthorizationRequest(
    public val requestIndex: Int,
    public val credentialId: String,
    public val deviceAuthentication: ProximityDeviceAuthenticationMethod,
) {
    init {
        require(requestIndex >= 0 && credentialId.isNotBlank())
    }
}

/**
 * Exact holder-key authorization context for a frozen approved submission.
 *
 * @property exchange Request exchange whose response is frozen.
 * @property requests Protected-key operations required by the approved response.
 */
public data class ProximityHolderAuthorization(
    /** Identity of the consumed review whose accepted choices require key authorization. */
    public val reviewId: ProximityReviewId,
    public val exchange: Int,
    public val requests: List<ProximityHolderAuthorizationRequest>,
) {
    init {
        require(exchange > 0 && requests.isNotEmpty())
        require(requests.distinctBy { it.requestIndex }.size == requests.size)
    }
}

/** Deterministic result of dispatching an action. */
public sealed interface ProximityActionResult {
    /** The action was legal for the current state and was accepted exactly once. */
    public data object Accepted : ProximityActionResult

    /** The action was illegal, stale, or invalid and had no side effects. */
    public data class Rejected(
        /** Stable reason the action was rejected. */
        public val error: ProximityError,
    ) :
        ProximityActionResult
}

/** Public Wallet SDK session state; each variant carries only data valid for that phase. */
public sealed interface ProximityState {
    /** Prerequisites are being evaluated or require host remediation. */
    public data class CheckingPrerequisites(
        /** Latest side-effect-free prerequisite snapshot. */
        public val capabilities: ProximityCapabilities,
    ) : ProximityState

    /** Session material and selected transports are being prepared. */
    public data class Preparing(
        /** Profile frozen for this single-use session. */
        public val profile: ProximityProfile,
    ) : ProximityState

    /** Engagement data is ready for presentation to the reader. */
    public data class EngagementReady(
        /** Prepared engagement methods the host may present. */
        public val engagements: List<ProximityEngagement>,
    ) : ProximityState {
        init { require(engagements.isNotEmpty() && engagements.distinctBy { it::class }.size == engagements.size) }
    }

    /** The reader has consumed engagement data and transport connection is in progress. */
    public data class Connecting(
        /** Engagement methods that initiated the connection attempt. */
        public val engagements: List<ProximityEngagement>,
    ) : ProximityState {
        init { require(engagements.isNotEmpty() && engagements.distinctBy { it::class }.size == engagements.size) }
    }

    /** Transport is connected and awaiting the next device request. */
    public data class AwaitingRequest(
        /** One-based exchange number expected next. */
        public val exchange: Int,
    ) : ProximityState {
        init { require(exchange > 0) }
    }

    /** An immutable request snapshot requires explicit holder consent. */
    public data class ReviewRequired(
        /** Exact review snapshot to render and approve or decline. */
        public val review: ProximityReview,
        /** Explains why an earlier prepared approval could not be used. */
        public val reason: ProximityReviewReason = ProximityReviewReason.RequestReceived,
    ) :
        ProximityState

    /** Connection ended without disclosure. Review this verified request before explicitly preparing a new connection. */
    public data class PreparationRequired(
        /** Verified request to review and explicitly approve before reconnecting. */
        public val plan: ProximitySharingPlan,
        /** Whether this is the initial request or a change from the earlier prepared approval. */
        public val reason: ProximityReviewReason = ProximityReviewReason.RequestReceived,
    ) : ProximityState

    /** The approved response is awaiting protected holder-key authorization. */
    public data class AuthorizingHolderKey(
        /** Exact protected-key operations frozen by holder consent. */
        public val authorization: ProximityHolderAuthorization,
    ) : ProximityState

    /** The approved and authorized response is being sent. */
    public data class SendingResponse(
        /** One-based exchange number being answered. */
        public val exchange: Int,
    ) : ProximityState {
        init { require(exchange > 0) }
    }

    /** One response completed and the session remains open for another request. */
    public data class AwaitingNextRequest(
        /** Number of exchanges completed successfully. */
        public val completedExchanges: Int,
    ) : ProximityState {
        init { require(completedExchanges > 0) }
    }

    /** Session termination is being sent after the final exchange. */
    public data class Terminating(
        /** One-based exchange number after which termination occurs. */
        public val exchange: Int,
    ) : ProximityState {
        init { require(exchange > 0) }
    }

    /**
     * Matching could not satisfy the final request; its original requested information is retained.
     * No credential data was shared for this request. Earlier exchanges may already have shared data.
     */
    public data class NoData(
        /** One-based final request exchange number. */
        public val exchange: Int,
        /** Required original requested-document context for this unmatched request. */
        public val unsatisfiedRequest: ProximityUnsatisfiedRequest,
    ) : ProximityState {
        init { require(exchange > 0) }
    }

    /** Session reached a normal terminal state after disclosure or holder decline. */
    public data class Completed(
        /** Number of requests handled before termination. */
        public val exchanges: Int,
        /** Whether the holder declined the final reviewed request. */
        public val declined: Boolean,
        /** Last locally completed response; does not assert that the reader verified or accepted it. */
        public val receipt: ProximitySharingReceipt? = null,
    ) :
        ProximityState {
        init { require(exchanges > 0) }
    }

    /** Session was cancelled locally and all owned resources were released. */
    public data object Cancelled : ProximityState

    /** Session terminated because of a stable Wallet SDK failure. */
    public data class Failed(
        /** Display-safe terminal failure. */
        public val error: ProximityError,
    ) : ProximityState
}

/** Legal host actions derived exclusively from the current session state. */
public val ProximityState.legalActions: Set<ProximityActionType>
    get() = when (this) {
        is ProximityState.CheckingPrerequisites -> setOf(
            ProximityActionType.RetryPrerequisites,
            ProximityActionType.ReportRemediation,
            ProximityActionType.Cancel,
        )
        is ProximityState.ReviewRequired -> setOf(
            ProximityActionType.Approve,
            ProximityActionType.Decline,
            ProximityActionType.Cancel,
        )
        is ProximityState.Preparing,
        is ProximityState.EngagementReady,
        is ProximityState.Connecting,
        is ProximityState.AwaitingRequest,
        is ProximityState.AuthorizingHolderKey,
        is ProximityState.SendingResponse,
        is ProximityState.AwaitingNextRequest -> setOf(ProximityActionType.Cancel)
        is ProximityState.Terminating,
        is ProximityState.PreparationRequired,
        is ProximityState.Completed,
        is ProximityState.NoData,
        ProximityState.Cancelled,
        is ProximityState.Failed -> emptySet()
    }

/** Action kinds used for state-derived UI affordances without constructing an action payload. */
public enum class ProximityActionType {
    Approve,
    Decline,
    Cancel,
    RetryPrerequisites,
    ReportRemediation,
}

/** Engagement that actually won the reader connection. */
public enum class ProximityEngagementMethod { Qr, Nfc }

/** Bearer actually carrying the connected session. */
public enum class ProximityTransport { BluetoothLowEnergy, Nfc, WifiAware }

/**
 * Actual connected route, independent of the methods configured or advertised.
 * @property engagement Engagement that won the reader connection.
 * @property transport Bearer of the last received message, or the initial connection before any message.
 */
public data class ProximityConnectedRoute(
    public val engagement: ProximityEngagementMethod,
    public val transport: ProximityTransport,
)

/** Single-use, wallet-owned proximity presentation session. */
public interface ProximitySession {
    /** Winning route once connected; remains available through review and termination. */
    public val connectedRoute: ProximityConnectedRoute? get() = null

    /** Most recent request eligible for explicit preparation. Never authorizes disclosure by itself. */
    public val sharingPlan: ProximitySharingPlan? get() = null

    /** Hot state stream whose variants define the only legal phase data and actions. */
    public val state: StateFlow<ProximityState>

    /** Dispatches one state-bound action. Illegal or stale actions are rejected without side effects. */
    public suspend fun dispatch(action: ProximityAction): ProximityActionResult

    /** Opt-in host review of selected cached credential-status warnings; performs no status requests. */
    public suspend fun dispatchStatusReviewed(action: ProximityAction, reviewedStatus: Map<String, String>): ProximityActionResult =
        throw UnsupportedOperationException("Status-reviewed proximity dispatch is unavailable")

    /** Idempotently cancels an active session and releases all session-owned resources. */
    public suspend fun close()
}
