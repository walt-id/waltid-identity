package id.walt.holderpolicies

import id.walt.credentials.formats.DigitalCredential
import id.walt.holderpolicies.HolderPolicy.HolderPolicyAction
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.toList

object HolderPolicyEngine {

    private val log = KotlinLogging.logger { }

    suspend fun evaluate(policies: Flow<HolderPolicy>, credentials: Flow<DigitalCredential>): HolderPolicyAction? {
        log.trace { "Evaluating holder policies for credentials: $credentials" }
        return policies
            .filter { it.apply == null || it.apply.matchesCredentials(credentials) }
            .toList()
            .sortedBy { it.priority }
            .firstOrNull() { policy ->
                log.trace { "Holder policy check on credentials: ${policy.serialized()}" }
                (policy.check?.matchesCredentials(credentials) ?: true)
                    .also { log.trace { "Holder policy check match: $it" } }
            }?.action
    }
}
