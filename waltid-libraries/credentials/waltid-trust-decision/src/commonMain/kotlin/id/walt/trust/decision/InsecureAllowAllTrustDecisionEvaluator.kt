package id.walt.trust.decision

import id.walt.trust.model.FreshnessState
import id.walt.trust.model.TrustDecision
import id.walt.trust.model.TrustDecisionCode

/**
 * Development only: trusts every subject without checking anything.
 *
 * Decisions report [FreshnessState.UNKNOWN] and an unaccepted source, so a call site that requires
 * [TrustRequirement.RegistryFresh] still rejects them.
 */
object InsecureAllowAllTrustDecisionEvaluator : TrustDecisionEvaluator {
    override suspend fun evaluate(request: TrustDecisionRequest): TrustDecision = TrustDecision(
        decision = TrustDecisionCode.TRUSTED,
        sourceFreshness = FreshnessState.UNKNOWN,
        warnings = listOf("Insecure allow-all trust evaluator: no trust check was performed"),
    )
}
