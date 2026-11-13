package id.walt.trust.decision

import id.walt.trust.model.FreshnessState
import id.walt.trust.model.TrustDecision
import id.walt.trust.model.TrustDecisionCode
import id.walt.trust.model.TrustEvidence

/**
 * Development only: trusts every subject without checking anything.
 *
 * Decisions name no entity and carry [EVIDENCE_TYPE] evidence, so they satisfy [TrustRequirement.Trusted]
 * for any expected entity type. They report [FreshnessState.UNKNOWN] and an unaccepted source, so a call
 * site that requires [TrustRequirement.RegistryFresh] still rejects them.
 */
object InsecureAllowAllTrustDecisionEvaluator : TrustDecisionEvaluator {
    const val EVIDENCE_TYPE = "INSECURE_ALLOW_ALL"

    override suspend fun evaluate(request: TrustDecisionRequest): TrustDecision = TrustDecision(
        decision = TrustDecisionCode.TRUSTED,
        sourceFreshness = FreshnessState.UNKNOWN,
        evidence = listOf(TrustEvidence(EVIDENCE_TYPE, "No trust check was performed")),
        warnings = listOf("Insecure allow-all trust evaluator: no trust check was performed"),
    )
}
