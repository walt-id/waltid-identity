package id.walt.wallet2.mobile

import id.walt.mdoc.proximity.ImmutableBytes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** How the holder approves data release. Transport selection remains independent. */
public sealed interface ProximityApproval {
    /** Show each request while connected, when the platform permits interaction. */
    public data object AskEachTime : ProximityApproval

    /** Authenticate a reader and collect its request without disclosure, then approve before reconnecting. */
    public data object PrepareBeforeSharing : ProximityApproval

    /** Use an explicit, wallet-bound approval once. Changed requests require another holder decision. */
    public data class Prepared(
        /** One-use approval created from the holder's reviewed sharing plan. */
        public val sharing: ProximityPreparedSharing,
    ) : ProximityApproval
}

/** Why a request needs a fresh review. */
public enum class ProximityReviewReason {
    RequestReceived,
    /** The reader or request differs from the exact scope approved before connecting. */
    PreparedSharingChanged,
}

/** Timing of the holder's deliberate approval for a locally completed response. */
public enum class ProximityApprovalTiming { DuringConnection, BeforeConnection }

/** Display-only receipt. Its data is not an authorization token and cannot be replayed. */
public class ProximitySharingReceipt internal constructor(
    review: ProximityReview,
    submission: ProximitySubmission,
    /** Whether the holder approved the response before or during the connection. */
    public val approvalTiming: ProximityApprovalTiming,
) {
    /** Local response-completion time; this is not a reader verification timestamp. */
    public val completedAt: Instant = Clock.System.now()
    private val ownedReview = review.snapshot()
    private val ownedSubmission = submission.snapshot()
    /** Reviewed reader request. Each access returns an independently owned snapshot. */
    public val review: ProximityReview get() = ownedReview.snapshot()
    /** Holder-approved credential and field choices. Each access returns an independently owned snapshot. */
    public val submission: ProximitySubmission get() = ownedSubmission.snapshot()
}

/** A wallet-verified request that can be approved before a new connection. This is not permission to share. */
public class ProximitySharingPlan internal constructor(
    internal val walletIdentity: Any,
    review: ProximityReview,
    internal val scope: ProximityApprovalScope,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val created: TimeMark = timeSource.markNow(),
    private val now: () -> Instant = { Clock.System.now() },
) {
    private val ownedReview = review.snapshot()

    /** Authenticated recipient, requested data, retention and purpose to show before approval. */
    public val review: ProximityReview get() = ownedReview.snapshot()

    /** Display deadline for this recent request. Authoritative expiry uses monotonic time. */
    public val expiresAt: Instant = now() + PLAN_LIFETIME

    /** Exact certificate identity; display names or CA membership alone never identify the recipient. */
    public val readerCertificateSha256: String get() = scope.readerCertificateSha256

    /** Whether the request is still recent enough to prepare. It is checked again during approval. */
    public val isExpired: Boolean get() = created.elapsedNow() >= PLAN_LIFETIME

    /**
     * Call only after deliberately showing and approving [submission]. No radio or key operation occurs here.
     * The returned approval is valid for 60 seconds, one wallet and one connection attempt. It is never persisted.
     */
    public fun approve(submission: ProximitySubmission): ProximityPreparationResult {
        if (isExpired) return preparationRejected("sharing_plan_expired", "This request has expired. Connect to the reader again.")
        val owned = try { submission.snapshot() } catch (_: IllegalArgumentException) {
            return preparationRejected("invalid_prepared_submission", "Choose the data to share before preparing.")
        }
        if (owned.continueAfterResponse) {
            return preparationRejected("prepared_sharing_single_use", "Prepared sharing allows one request only.")
        }
        validateProximitySubmission(ownedReview, owned)?.let { return ProximityPreparationResult.Rejected(it) }
        if (!owned.disclosesRequestedPortrait(ownedReview)) {
            return preparationRejected("portrait_required", "This identity request requires the portrait. Include it or decline sharing.")
        }
        return ProximityPreparationResult.Prepared(
            ProximityPreparedSharing(this, owned, now(), timeSource.markNow()),
        )
    }
}

/** Result of an explicit holder decision made before connection. */
public sealed interface ProximityPreparationResult {
    /** The reviewed selection was accepted for one subsequent connection attempt. */
    public data class Prepared(
        /** Wallet-bound approval to supply through [ProximityApproval.Prepared]. */
        public val sharing: ProximityPreparedSharing,
    ) : ProximityPreparationResult

    /** The plan or selection could not be approved; no sharing approval was created. */
    public data class Rejected(
        /** Reason preparation failed and the available recovery action. */
        public val error: ProximityError,
    ) : ProximityPreparationResult
}

/** Opaque one-use approval. Hosts may display or cancel it, but cannot construct or expand its scope. */
public class ProximityPreparedSharing internal constructor(
    internal val plan: ProximitySharingPlan,
    submission: ProximitySubmission,
    approvedAt: Instant,
    private val approved: TimeMark,
) {
    private val ownedSubmission = submission.snapshot()
    private val mutex = Mutex()
    private var claimed = false
    private val mutableRevoked = MutableStateFlow(false)
    internal val revoked: StateFlow<Boolean> = mutableRevoked.asStateFlow()

    /** Original reader request to render together with the exact holder selection. */
    public val review: ProximityReview get() = plan.review

    /** Exact credential and field choices. Each access returns an independently owned snapshot. */
    public val submission: ProximitySubmission get() = ownedSubmission.snapshot()

    /** Display deadline; wall-clock changes cannot extend the approval. */
    public val expiresAt: Instant = approvedAt + APPROVAL_LIFETIME

    /** Remaining time for a matching request to begin its single approved response. */
    public val remainingSeconds: Int get() = ((remaining.inWholeNanoseconds + 999_999_999) / 1_000_000_000).toInt()

    internal val remaining: Duration get() = (APPROVAL_LIFETIME - approved.elapsedNow()).coerceAtLeast(Duration.ZERO)

    /** Revokes future release. Cancellation cannot undo a response already authorized and sent. */
    public suspend fun revoke(): Unit = mutex.withLock { mutableRevoked.value = true }

    internal suspend fun claim(walletIdentity: Any): ProximityError? = mutex.withLock {
        when {
            plan.walletIdentity !== walletIdentity -> approvalError("prepared_sharing_wrong_wallet", "This approval belongs to another wallet.")
            claimed -> approvalError("prepared_sharing_used", "This approval has already been used. Approve again before reconnecting.")
            mutableRevoked.value -> approvalError("prepared_sharing_cancelled", "Prepared sharing was cancelled.")
            remaining <= Duration.ZERO -> approvalError("prepared_sharing_expired", "Prepared sharing expired. Approve again before connecting.")
            else -> { claimed = true; null }
        }
    }

    /** Linearizes cancellation, expiry and the final matching decision before holder-key authorization. */
    internal suspend fun accept(scope: ProximityApprovalScope, review: ProximityReview): ProximitySubmission? = mutex.withLock {
        if (!claimed || mutableRevoked.value || remaining <= Duration.ZERO || !plan.scope.matches(scope, ownedSubmission)) {
            return@withLock null
        }
        if (validateProximitySubmission(review, ownedSubmission) != null) return@withLock null
        mutableRevoked.value = true // An accepted decision is also permanently unavailable to every later request.
        ownedSubmission.snapshot()
    }
}

/** Scope retained without credential contents, keys, request signatures, or session secrets. */
internal data class ProximityApprovalScope(
    val profile: ProximityProfile,
    val readerCertificateSha256: String,
    val requestDigest: ImmutableBytes,
    val credentials: Map<String, ImmutableBytes>,
    val applicationAuthorizations: List<ProximityApplicationAuthorization>,
) {
    fun matches(other: ProximityApprovalScope, submission: ProximitySubmission): Boolean =
        profile == other.profile && readerCertificateSha256 == other.readerCertificateSha256 &&
            requestDigest == other.requestDigest && applicationAuthorizations == other.applicationAuthorizations &&
            submission.documents.all { selected ->
                credentials[selected.credentialId]?.let { it == other.credentials[selected.credentialId] } == true
            }
}

internal fun validateProximitySubmission(
    review: ProximityReview,
    submission: ProximitySubmission,
): ProximityError? {
    if (submission.documents.map { it.requestIndex }.toSet() != review.documents.map { it.requestIndex }.toSet()) {
        return invalidSubmission("Exactly one current credential choice is required for every reviewed document")
    }
    submission.documents.forEach { selected ->
        val document = review.documents.singleOrNull { it.requestIndex == selected.requestIndex }
            ?: return invalidSubmission("A submitted document was not part of the current review")
        val credential = document.credentialOptions.singleOrNull { it.credentialId == selected.credentialId }
            ?: return invalidSubmission("A submitted credential was not offered by the current review")
        val offered = credential.requestedElements.map {
            ProximityElementReference(it.namespace, it.elementIdentifier)
        }.toSet()
        if (!selected.disclosedElements.all { it in offered }) {
            return invalidSubmission("A submitted disclosure was not offered by the current review")
        }
    }
    return null
}

/** Matches the mDL response builder's existing all-or-nothing rule when a requested portrait is declined. */
internal fun ProximitySubmission.disclosesRequestedPortrait(review: ProximityReview): Boolean {
    val portrait = ProximityElementReference("org.iso.18013.5.1", "portrait")
    return documents.all { selected ->
        val document = review.documents.single { it.requestIndex == selected.requestIndex }
        val credential = document.credentialOptions.single { it.credentialId == selected.credentialId }
        document.requiredElements.all { it in selected.disclosedElements } && (credential.requestedElements.none {
            it.namespace == portrait.namespace && it.elementIdentifier == portrait.elementIdentifier
        } || portrait in selected.disclosedElements)
    }
}

internal fun approvalError(code: String, message: String): ProximityError = ProximityError(
    ProximityErrorCategory.Policy, code, message, ProximityRecovery.StartNewSession,
)

private fun preparationRejected(code: String, message: String): ProximityPreparationResult =
    ProximityPreparationResult.Rejected(approvalError(code, message))

private val PLAN_LIFETIME = 10.minutes
private val APPROVAL_LIFETIME = 60.seconds

private fun invalidSubmission(message: String): ProximityError = ProximityError(
    ProximityErrorCategory.StaleSubmission, "stale_submission", message, ProximityRecovery.None,
)
