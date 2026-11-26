package id.walt.wallet2.mobile

import id.walt.wallet2.mobile.proximity.ProximityApproval
import id.walt.wallet2.mobile.proximity.ProximityConfiguration
import id.walt.wallet2.mobile.proximity.ProximityPreparationResult
import id.walt.wallet2.mobile.proximity.ProximitySession
import id.walt.wallet2.mobile.proximity.ProximitySharingPlan
import id.walt.wallet2.mobile.proximity.ProximityState
import id.walt.wallet2.mobile.proximity.ProximitySubmission
import id.walt.wallet2.mobile.proximity.ProximityUnsatisfiedRequest
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Compiled examples checked against the public guide; never opens radios during tests. */
@Suppress("UNUSED_PARAMETER")
internal class MobileWalletProximitySnippets {
    suspend fun session(wallet: Wallet) {
        // doc-snippet:start kotlin-proximity-session
        val configuration = ProximityConfiguration()
        val capabilities = wallet.proximityPresentationCapabilities(configuration)
        showUnavailableMethods(capabilities)
        val session = wallet.startProximityPresentation(configuration)

        try {
            session.state.collect { state ->
                when (state) {
                    is ProximityState.CheckingPrerequisites -> showUnavailableMethods(state.capabilities)
                    is ProximityState.EngagementReady -> showEngagements(state.engagements)
                    is ProximityState.ReviewRequired -> showProximityReview(state.review)
                    is ProximityState.PreparationRequired -> showPreparationReview(state.plan, state.reason)
                    is ProximityState.AuthorizingHolderKey -> showHolderAuthorization(state.authorization)
                    is ProximityState.Completed -> showCompletion(state.exchanges, state.declined)
                    is ProximityState.NoData -> showNoData(state.exchange, state.unsatisfiedRequest)
                    is ProximityState.Failed -> showProximityError(state.error)
                    ProximityState.Cancelled -> showCancelled()
                    is ProximityState.Preparing,
                    is ProximityState.Connecting,
                    is ProximityState.AwaitingRequest,
                    is ProximityState.SendingResponse,
                    is ProximityState.AwaitingNextRequest,
                    is ProximityState.Terminating -> showProximityProgress(state)
                }
            }
        } finally {
            withContext(NonCancellable) { session.close() }
        }
        // doc-snippet:end kotlin-proximity-session
    }

    suspend fun reconnect(
        wallet: Wallet,
        configuration: ProximityConfiguration,
        plan: ProximitySharingPlan,
        submission: ProximitySubmission,
    ): ProximitySession? {
        // doc-snippet:start kotlin-proximity-preparation
        return when (val result = plan.approve(submission)) {
            is ProximityPreparationResult.Prepared -> wallet.startProximityPresentation(
                configuration.copy(approval = ProximityApproval.Prepared(result.sharing))
            )
            is ProximityPreparationResult.Rejected -> {
                showProximityError(result.error)
                null
            }
        }
        // doc-snippet:end kotlin-proximity-preparation
    }

    private fun showUnavailableMethods(value: Any) = Unit
    private fun showEngagements(value: Any) = Unit
    private fun showProximityReview(value: Any) = Unit
    private fun showPreparationReview(plan: Any, reason: Any) = Unit
    private fun showHolderAuthorization(value: Any) = Unit
    private fun showCompletion(exchanges: Any, declined: Any) = Unit
    private fun showNoData(exchange: Any, request: ProximityUnsatisfiedRequest) = Unit
    private fun showProximityError(value: Any) = Unit
    private fun showCancelled() = Unit
    private fun showProximityProgress(value: Any) = Unit
}
