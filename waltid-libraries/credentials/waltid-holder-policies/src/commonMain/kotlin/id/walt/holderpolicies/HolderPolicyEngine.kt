package id.walt.holderpolicies

import id.walt.credentials.formats.DigitalCredential
import id.walt.holderpolicies.HolderPolicy.HolderPolicyAction
import id.walt.holderpolicies.checks.DisclosedClaimsHolderPolicyCheck
import id.walt.holderpolicies.checks.HolderPolicyCheck
import id.walt.holderpolicies.checks.HolderPolicyMatch
import id.walt.holderpolicies.checks.TransactionDataHolderPolicyCheck
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList

object HolderPolicyEngine {

    private val log = KotlinLogging.logger { }

    suspend fun evaluate(policies: Flow<HolderPolicy>, credentials: Flow<DigitalCredential>): HolderPolicyAction? =
        evaluate(policies, credentials, HolderPolicyPresentationContext())

    /**
     * Applies policies in priority order to a prepared presentation. Unavailable evidence can make
     * a BLOCK policy apply, but cannot establish an ALLOW.
     */
    suspend fun evaluate(
        policies: Flow<HolderPolicy>,
        credentials: Flow<DigitalCredential>,
        presentation: HolderPolicyPresentationContext,
    ): HolderPolicyAction? {
        val evidence = PresentationEvidence(presentation)
        for (policy in policies.toList().sortedBy { it.priority }) {
            log.trace { "Evaluating holder policy with priority ${policy.priority}" }
            if (!evaluate(policy.apply, credentials, evidence).couldApply(policy.action)) continue
            if (evaluate(policy.check, credentials, evidence).couldApply(policy.action)) return policy.action
        }
        return null
    }

    private suspend fun evaluate(
        check: HolderPolicyCheck?,
        credentials: Flow<DigitalCredential>,
        evidence: PresentationEvidence,
    ): HolderPolicyMatch = when (check) {
        null -> HolderPolicyMatch.MATCH
        is DisclosedClaimsHolderPolicyCheck -> check.matchesDisclosures(evidence.disclosures())
        is TransactionDataHolderPolicyCheck -> check.matchesTransactions(evidence.transactionData)
        else -> if (check.matchesCredentials(credentials)) HolderPolicyMatch.MATCH else HolderPolicyMatch.NO_MATCH
    }

    private fun HolderPolicyMatch.couldApply(action: HolderPolicyAction): Boolean =
        this == HolderPolicyMatch.MATCH || (this == HolderPolicyMatch.UNAVAILABLE && action == HolderPolicyAction.BLOCK)

    private class PresentationEvidence(context: HolderPolicyPresentationContext) {
        val transactionData = context.transactionData
        private var loadDisclosures = context.disclosures
        private var disclosedCredentials: List<HolderPolicyDisclosedCredential>? = null

        suspend fun disclosures(): List<HolderPolicyDisclosedCredential>? {
            loadDisclosures?.let { load ->
                disclosedCredentials = load()
                loadDisclosures = null
            }
            return disclosedCredentials
        }
    }
}
