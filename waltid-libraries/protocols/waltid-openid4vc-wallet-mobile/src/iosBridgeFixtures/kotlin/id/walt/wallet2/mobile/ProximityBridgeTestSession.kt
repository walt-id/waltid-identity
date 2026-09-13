package id.walt.wallet2.mobile

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Instant

/** Native values and flow for exercising the real Swift bridge; absent from release artifacts. */
public class ProximityBridgeTestSession {
    private val review = ProximityReview(
        ProximityReviewId("d8d237bf-5035-4c17-952d-cddc7b7a2da4"), 1,
        listOf(ProximityDocumentReview(0, "org.iso.18013.5.1.mDL", listOf(ProximityCredentialOption(
            "credential-1", "Identity", null, Instant.fromEpochSeconds(2_000_000_000),
            ProximityDeviceAuthenticationMethod.Signature,
            listOf("given_name", "family_name").map { ProximityRequestedElement("org.iso.18013.5.1", it, false) },
        )))), emptyList(), emptyList(), emptyList(),
    )
    private val mutableState = MutableStateFlow<ProximityState>(ProximityState.ReviewRequired(
        review, ProximityReviewReason.PreparedSharingChanged,
    ))
    public var lastApproval: ProximityAction.Approve? = null
        private set
    public var closeCalls: Int = 0
        private set
    public var cancelCalls: Int = 0
        private set
    public val activeCollectors: Int get() = mutableState.subscriptionCount.value

    public val session: ProximitySession = object : ProximitySession {
        override val state: StateFlow<ProximityState> = mutableState
        override suspend fun dispatch(action: ProximityAction): ProximityActionResult {
            when (action) {
                is ProximityAction.Approve -> {
                    lastApproval = action
                    mutableState.value = ProximityState.SendingResponse(1)
                }
                ProximityAction.Cancel -> {
                    cancelCalls++
                    mutableState.value = ProximityState.Cancelled
                }
                else -> error("Unsupported scripted action")
            }
            return ProximityActionResult.Accepted
        }
        override suspend fun close() { closeCalls++ }
    }

    public fun completeResponse() {
        val submission = requireNotNull(lastApproval).submission
        mutableState.value = ProximityState.Completed(1, false, ProximitySharingReceipt(
            review, submission, ProximityApprovalTiming.DuringConnection,
        ))
    }

    public fun publishLateReview() {
        mutableState.value = ProximityState.ReviewRequired(review)
    }
}
