package id.walt.openid4vp.verifier.handlers.vpresponse

import id.walt.credentials.formats.DigitalCredential
import id.walt.openid4vp.verifier.data.Verification2Session
import id.walt.policies2.vc.PolicyResult
import id.walt.policies2.vc.CredentialPolicyResults
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

object Verifier2SessionCredentialPolicyValidation {

    private val log = KotlinLogging.logger {}

    internal suspend fun validateCredentialPolicies(
        policies: Verification2Session.DefinedVerificationPolicies,
        validatedCredentials: Map<String, List<DigitalCredential>>
    ): CredentialPolicyResults = coroutineScope {

        // --- General VC Policies ---
        val generalPolicyJobs = validatedCredentials.flatMap { (queryId, credentials) ->
            credentials.flatMap { credential ->
                policies.vcPolicies?.policies.orEmpty().map { policy ->
                    async(Dispatchers.Default) {
                        log.trace { "Validating '$queryId' credential with policy '${policy.id}': $credential" }
                        val result = policy.verify(credential)
                        log.trace { "'$queryId' credential '${policy.id}' result: $result" }

                        PolicyResult(
                            policy = policy,
                            success = result.isSuccess,
                            result = result.getOrNull(),
                            error = result.exceptionOrNull()?.message
                        )
                    }
                }
            }
        }

        // --- Specific VC Policies ---
        val specificPolicyJobs = policies.specificVcPolicies.orEmpty().flatMap { (queryId, queryPolicies) ->
            val credentials = validatedCredentials[queryId].orEmpty()

            credentials.flatMap { specificCredential ->
                queryPolicies.policies.map { policy ->
                    async(Dispatchers.Default) {
                        val result = policy.verify(specificCredential)

                        queryId to PolicyResult(
                            policy = policy,
                            success = result.isSuccess,
                            result = result.getOrNull(),
                            error = result.exceptionOrNull()?.message
                        )
                    }
                }
            }
        }

        // --- Await all policy runs ---
        // Wait for all to finish
        val vcPolicyResults = generalPolicyJobs.awaitAll()
        val specificPolicyPairs = specificPolicyJobs.awaitAll()

        // Group the specific results back into a Map<String, List<PolicyResult>>
        val specificVcPolicyResults = specificPolicyPairs
            .groupBy({ it.first }, { it.second })

        return@coroutineScope CredentialPolicyResults(
            vcPolicies = ArrayList(vcPolicyResults),
            specificVcPolicies = specificVcPolicyResults
        )
    }
}
