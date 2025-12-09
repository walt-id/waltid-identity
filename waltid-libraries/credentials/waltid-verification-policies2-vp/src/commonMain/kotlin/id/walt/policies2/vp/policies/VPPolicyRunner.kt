package id.walt.policies2.vp.policies

import id.walt.credentials.presentations.formats.*
import id.walt.policies2.vp.policies.VPPolicy2.PolicyRunResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

object VPPolicyRunner {

    suspend fun <T : VPPolicy2> executeParallelPolicies(policies: List<T>, block: suspend T.() -> PolicyRunResult) = coroutineScope {
        policies
            .map { policy ->
                /*
                 * runPolicy is doing heavy CPU calculation (cryptography),
                 * as such Dispatchers.Default is used instead of the default context / Dispatchers.IO
                 */
                async(Dispatchers.Default) {
                    val result = block.invoke(policy)
                    policy.id to result
                }
            }.awaitAll()
            .toMap()
    }


    suspend fun verifyPresentation(
        presentation: VerifiablePresentation,
        policies: VPPolicyList,
        verificationContext: VerificationSessionContext
    ): Map<String, PolicyRunResult> {
        return when (presentation) {
            is JwtVcJsonPresentation -> executeParallelPolicies(policies.jwtVcJson) { runPolicy(presentation, verificationContext) }
            is DcSdJwtPresentation -> executeParallelPolicies(policies.dcSdJwt) { runPolicy(presentation, verificationContext) }
            is MsoMdocPresentation -> executeParallelPolicies(policies.msoMdoc) {
                runPolicy(document = presentation.mdoc.document, mso = presentation.mdoc.documentMso, verificationContext)
            }

            is LdpVcPresentation -> throw NotImplementedError("Verifying LDP presentations is not yet implemented!")
        }

    }

}
