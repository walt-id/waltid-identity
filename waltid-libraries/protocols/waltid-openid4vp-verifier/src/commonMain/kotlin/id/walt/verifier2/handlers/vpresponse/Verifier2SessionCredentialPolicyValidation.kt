package id.walt.verifier2.handlers.vpresponse

import id.walt.credentials.formats.DigitalCredential
import id.walt.policies2.vc.CredentialPolicyResult
import id.walt.policies2.vc.ExpirationDatePolicyException
import id.walt.policies2.vc.NotBeforePolicyException
import id.walt.policies2.vc.policies.CredentialVerificationPolicy2
import id.walt.policies2.vc.policies.PolicyExecutionContext
import id.walt.verifier2.data.Verification2Session
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
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
        val specificVcPolicies: Map<String, List<CredentialPolicyResult>>
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
                        evaluate(policy, credential, context, queryId, credentialIndex)
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
                        queryId to evaluate(policy, specificCredential, context, queryId, credentialIndex)
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

    private suspend fun evaluate(
        policy: CredentialVerificationPolicy2,
        credential: DigitalCredential,
        context: PolicyExecutionContext,
        queryId: String,
        credentialIndex: Int,
    ): CredentialPolicyResult {
        val result = try {
            policy.verify(credential, context)
        } catch (cause: Exception) {
            Result.failure(cause)
        }
        val error = when (val cause = result.exceptionOrNull()) {
            null -> null
            is CancellationException -> throw cause
            is Error -> throw cause
            is ExpirationDatePolicyException -> "Credential has expired"
            is NotBeforePolicyException -> "Credential is not yet valid"
            else -> {
                log.warn(cause) { "Credential policy '${policy.id}' failed for '$queryId' credential #$credentialIndex" }
                "Credential did not pass the '${policy.id}' policy"
            }
        }
        return CredentialPolicyResult(policy, result.isSuccess, result.getOrNull(), error, queryId, credentialIndex)
    }
}
