package id.walt.trust.decision

import id.walt.trust.model.AuthenticityState
import id.walt.trust.model.FreshnessState
import id.walt.trust.model.TrustDecision
import id.walt.trust.model.TrustDecisionCode
import id.walt.trust.model.TrustedEntityType

/**
 * Resolves a request whose [TrustDecisionRequest.expectedEntityTypes] may name several roles against a
 * source that accepts one expected type per call.
 *
 * Calls [resolveForType] once per expected type (or once with `null` when none is expected) and returns
 * the best answer. It stops at the first [TrustDecisionCode.TRUSTED] decision from a fresh, accepted and
 * authenticated source, because no other answer can satisfy more requirements. A trusted decision that
 * falls short of that (for example a stale source) is kept only until another type answers better, so
 * a role listed by a fresh source wins over the same chain listed by a stale one.
 *
 * Without a trusted decision it returns the most informative denial: a stale source or an ambiguous
 * match ranks before an unavailable source, which ranks before a plain [TrustDecisionCode.NOT_TRUSTED].
 */
suspend fun TrustDecisionRequest.anyOfEntityTypes(
    resolveForType: suspend (expectedEntityType: TrustedEntityType?) -> TrustDecision,
): TrustDecision {
    if (expectedEntityTypes.isEmpty()) return resolveForType(null)

    var best: TrustDecision? = null
    for (type in expectedEntityTypes.sortedBy { it.ordinal }) {
        val decision = resolveForType(type)
        if (decision.rank == BEST_RANK) return decision
        if (best == null || decision.rank < best.rank) best = decision
    }
    return checkNotNull(best)
}

private const val BEST_RANK = 0

/** Lower is better. Trusted decisions are ordered by the [TrustRequirement]s they can still satisfy. */
private val TrustDecision.rank: Int
    get() = when (decision) {
        TrustDecisionCode.TRUSTED -> when {
            sourceFreshness == FreshnessState.FRESH && sourceAssurance.accepted &&
                sourceAssurance.authenticityState == AuthenticityState.AUTHENTICATED -> BEST_RANK
            sourceFreshness == FreshnessState.FRESH && sourceAssurance.accepted -> 1
            sourceFreshness != FreshnessState.EXPIRED && sourceAssurance.authenticityState != AuthenticityState.FAILED -> 2
            else -> 3
        }
        TrustDecisionCode.STALE_SOURCE -> 4
        TrustDecisionCode.MULTIPLE_MATCHES -> 5
        TrustDecisionCode.UNKNOWN, TrustDecisionCode.PROCESSING_ERROR, TrustDecisionCode.UNSUPPORTED_SOURCE -> 6
        TrustDecisionCode.NOT_TRUSTED -> 7
    }
