package id.walt.verifier2.handlers.vpresponse

import id.walt.credentials.formats.DigitalCredential
import id.walt.policies2.vc.CredentialPolicyResult
import id.walt.policies2.vc.policies.PolicyExecutionContext
import id.walt.verifier2.data.AttributedCredentialPolicyResult
import id.walt.verifier2.data.Verification2Session
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object Verifier2SessionCredentialPolicyValidation {

    private val log = KotlinLogging.logger {}

    @Serializable
    data class CredentialPolicyResults(
        @SerialName("vc_policies")
        val vcPolicies: List<CredentialPolicyResult>,

        @SerialName("specific_vc_policies")
        val specificVcPolicies: Map<String, List<CredentialPolicyResult>>,

        @SerialName("attributed_vc_policies")
        val attributedVcPolicies: List<AttributedCredentialPolicyResult>,

        @SerialName("attributed_specific_vc_policies")
        val attributedSpecificVcPolicies: Map<String, List<AttributedCredentialPolicyResult>>,
    )

    suspend fun validateCredentialPolicies(
        policies: Verification2Session.DefinedVerificationPolicies,
        validatedCredentials: Map<String, List<DigitalCredential>>,
        context: PolicyExecutionContext = PolicyExecutionContext.Empty
    ): CredentialPolicyResults = coroutineScope {

        // --- General VC Policies ---
        val generalPolicyJobs = validatedCredentials.flatMap { (queryId, credentials) ->
            credentials.flatMapIndexed { credentialIndex, credential ->
                policies.vc_policies?.policies.orEmpty().map { policy ->
                    async(Dispatchers.Default) {
                        log.trace { "Validating '$queryId' credential#$credentialIndex with policy '${policy.id}': $credential" }
                        val result = policy.verify(credential, context)
                        log.trace { "'$queryId' credential#$credentialIndex '${policy.id}' result: $result" }

                        val policyResult = CredentialPolicyResult(
                            policy = policy,
                            success = result.isSuccess,
                            result = result.getOrNull(),
                            error = result.exceptionOrNull()?.message
                        )
                        AttributedCredentialPolicyResult(
                            queryId = queryId,
                            credentialIndex = credentialIndex,
                            result = policyResult,
                        )
                    }
                }
            }
        }

        // --- Specific VC Policies ---
        val specificPolicyJobs = policies.specific_vc_policies.orEmpty().flatMap { (queryId, queryPolicies) ->
            val credentials = validatedCredentials[queryId].orEmpty()

            credentials.flatMapIndexed { credentialIndex, specificCredential ->
                queryPolicies.policies.map { policy ->
                    async(Dispatchers.Default) {
                        val result = policy.verify(specificCredential, context)

                        val policyResult = CredentialPolicyResult(
                            policy = policy,
                            success = result.isSuccess,
                            result = result.getOrNull(),
                            error = result.exceptionOrNull()?.message
                        )
                        queryId to AttributedCredentialPolicyResult(
                            queryId = queryId,
                            credentialIndex = credentialIndex,
                            result = policyResult,
                        )
                    }
                }
            }
        }

        // --- Await all policy runs ---
        val attributedVcResults: List<AttributedCredentialPolicyResult> = generalPolicyJobs.awaitAll()
        val attributedSpecificPairs: List<Pair<String, AttributedCredentialPolicyResult>> = specificPolicyJobs.awaitAll()

        val attributedSpecificResults: Map<String, List<AttributedCredentialPolicyResult>> =
            attributedSpecificPairs.groupBy({ it.first }, { it.second })

        return@coroutineScope CredentialPolicyResults(
            vcPolicies = attributedVcResults.map { it.result },
            specificVcPolicies = attributedSpecificResults.mapValues { (_, list) -> list.map { it.result } },
            attributedVcPolicies = attributedVcResults,
            attributedSpecificVcPolicies = attributedSpecificResults,
        )
    }
}
