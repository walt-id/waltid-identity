package id.walt.openid4vp.verifier.handlers.vpresponse

import id.walt.credentials.formats.DigitalCredential
import id.walt.openid4vp.verifier.data.Verification2Session
import id.walt.policies2.PolicyResult
import id.walt.policies2.PolicyResults
import io.github.oshai.kotlinlogging.KotlinLogging

object Verifier2SessionPolicyValidation {

    private val log = KotlinLogging.logger {}

    internal suspend fun validatePolicies(
        policies: Verification2Session.DefinedVerificationPolicies,
        allSuccessfullyValidatedAndProcessedData: Map<String, List<DigitalCredential>>
    ): PolicyResults {
        val vcPolicyResults = ArrayList<PolicyResult>()
        val specificVcPolicyResults = emptyMap<String, List<PolicyResult>>()


        // VP Policies:
        /*  // TODO: vpPolicies
        val vpPolicyResults = session.policies.vpPolicies.policies.forEach {
            it.verify(vpTokenContents)
        }*/

        allSuccessfullyValidatedAndProcessedData.forEach { (queryId, credentials) ->
            credentials.forEach { credential ->

                policies.vcPolicies.policies.forEach { policy ->
                    log.trace { "Validating '$queryId' credential with policy '${policy.id}': $credential" }
                    val result = policy.verify(credential)
                    log.trace { "'$queryId' credential '${policy.id}' result: $result" }

                    vcPolicyResults.add(
                        PolicyResult(
                            policy = policy,
                            success = result.isSuccess,
                            result = result.getOrNull(),
                            error = result.exceptionOrNull()?.message
                        )
                    )
                }
                // Specific VC Policies:
                //session.policies.specificVcPolicies
            }
        }

        val policyResults = PolicyResults(
            // vpPolicies = vpPolicyResults, // TODO: vpPolicies
            vcPolicies = vcPolicyResults,
            specificVcPolicies = specificVcPolicyResults
        )

        return policyResults
    }

}
