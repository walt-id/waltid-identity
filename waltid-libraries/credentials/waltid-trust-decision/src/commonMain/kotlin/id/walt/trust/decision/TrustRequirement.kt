package id.walt.trust.decision

import id.walt.trust.model.AuthenticityState
import id.walt.trust.model.FreshnessState
import id.walt.trust.model.TrustDecision
import id.walt.trust.model.TrustDecisionCode

/** How strict a call site is about a trust decision. Comes from the caller's profile or configuration. */
sealed interface TrustRequirement {

    /** No trust check. The evaluator is not called. */
    data object NotRequired : TrustRequirement

    /**
     * The decision must be [TrustDecisionCode.TRUSTED], from a source that did not fail authentication
     * and has not expired. Static anchors satisfy this level.
     */
    data object Trusted : TrustRequirement

    /**
     * The decision must be [TrustDecisionCode.TRUSTED] by a registry whose source is accepted and
     * [FreshnessState.FRESH], for one of the request's expected entity types. Static and dev adapters
     * cannot satisfy this level.
     *
     * @property requireAuthenticated additionally require [AuthenticityState.AUTHENTICATED] sources.
     */
    data class RegistryFresh(val requireAuthenticated: Boolean = false) : TrustRequirement
}

sealed interface TrustCheck {
    data class Accepted(val decision: TrustDecision) : TrustCheck

    /** The requirement was [TrustRequirement.NotRequired]. */
    data object Skipped : TrustCheck

    data class Rejected(val reason: String, val decision: TrustDecision? = null) : TrustCheck
}

/**
 * Evaluates [request] and applies [requirement], failing closed: a missing evaluator or any decision
 * that does not meet the requirement is [TrustCheck.Rejected].
 *
 * Exceptions from the evaluator propagate unchanged, so an infrastructure failure aborts the
 * operation with its own error instead of passing for a trust rejection.
 */
suspend fun TrustDecisionEvaluator?.check(request: TrustDecisionRequest, requirement: TrustRequirement): TrustCheck {
    if (requirement == TrustRequirement.NotRequired) return TrustCheck.Skipped

    if (requirement is TrustRequirement.RegistryFresh && request.expectedEntityTypes.isEmpty()) {
        return TrustCheck.Rejected("Registry trust for ${request.purpose} requires at least one expected entity type")
    }
    val evaluator = this ?: return TrustCheck.Rejected("No trust evaluator is configured for ${request.purpose}")

    val decision = evaluator.evaluate(request)
    val reason = rejectionReason(decision, request, requirement)
    return if (reason == null) TrustCheck.Accepted(decision) else TrustCheck.Rejected(reason, decision)
}

private fun rejectionReason(decision: TrustDecision, request: TrustDecisionRequest, requirement: TrustRequirement): String? {
    if (decision.decision != TrustDecisionCode.TRUSTED) return "Trust decision is ${decision.decision}"

    val matchedType = decision.matchedEntity?.entityType
    if (matchedType != null && request.expectedEntityTypes.isNotEmpty() && matchedType !in request.expectedEntityTypes) {
        return "Matched entity type $matchedType is not one of ${request.expectedEntityTypes}"
    }

    val assurance = decision.sourceAssurance
    return when (requirement) {
        TrustRequirement.NotRequired -> null
        TrustRequirement.Trusted -> when {
            assurance.authenticityState == AuthenticityState.FAILED -> "Trust source authentication failed"
            decision.sourceFreshness == FreshnessState.EXPIRED -> "Trust source has expired"
            else -> null
        }

        is TrustRequirement.RegistryFresh -> when {
            matchedType == null -> "Trust decision names no matched registry entity"
            decision.sourceFreshness != FreshnessState.FRESH -> "Trust source freshness is ${decision.sourceFreshness}, FRESH is required"
            !assurance.accepted -> "Trust source is not accepted"
            requirement.requireAuthenticated && assurance.authenticityState != AuthenticityState.AUTHENTICATED ->
                "Trust source authenticity is ${assurance.authenticityState}, AUTHENTICATED is required"

            else -> null
        }
    }
}
