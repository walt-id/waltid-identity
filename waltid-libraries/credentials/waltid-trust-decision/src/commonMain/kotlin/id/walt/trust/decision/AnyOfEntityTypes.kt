package id.walt.trust.decision

import id.walt.trust.model.TrustDecision
import id.walt.trust.model.TrustDecisionCode
import id.walt.trust.model.TrustedEntityType

/**
 * Resolves a request whose [TrustDecisionRequest.expectedEntityTypes] may name several roles against a
 * source that accepts one expected type per call.
 *
 * Calls [resolveForType] once per expected type (or once with `null` when none is expected) and returns
 * the first [TrustDecisionCode.TRUSTED] decision. Without one, it returns the most informative denial:
 * a stale source or an ambiguous match ranks before an unavailable source, which ranks before a plain
 * [TrustDecisionCode.NOT_TRUSTED].
 */
suspend fun TrustDecisionRequest.anyOfEntityTypes(
    resolveForType: suspend (expectedEntityType: TrustedEntityType?) -> TrustDecision,
): TrustDecision {
    if (expectedEntityTypes.isEmpty()) return resolveForType(null)

    var best: TrustDecision? = null
    for (type in expectedEntityTypes.sortedBy { it.ordinal }) {
        val decision = resolveForType(type)
        if (decision.decision == TrustDecisionCode.TRUSTED) return decision
        if (best == null || decision.decision.rank < best.decision.rank) best = decision
    }
    return checkNotNull(best)
}

private val TrustDecisionCode.rank: Int
    get() = when (this) {
        TrustDecisionCode.TRUSTED -> 0
        TrustDecisionCode.STALE_SOURCE -> 1
        TrustDecisionCode.MULTIPLE_MATCHES -> 2
        TrustDecisionCode.UNKNOWN, TrustDecisionCode.PROCESSING_ERROR, TrustDecisionCode.UNSUPPORTED_SOURCE -> 3
        TrustDecisionCode.NOT_TRUSTED -> 4
    }
