package id.walt.policies2.vp.policies

import id.walt.credentials.presentations.formats.*
import id.walt.policies2.vp.policies.VPPolicy2.PolicyRunResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

object VPPolicyRunner {

    suspend fun <T : VPPolicy2> runParallelPolicies(policies: List<T>, block: suspend T.() -> PolicyRunResult) = coroutineScope {
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


    suspend fun executePolicies(
        presentation: VerifiablePresentation,
        policies: VPPolicyList,
        verificationContext: VerificationSessionContext
    ) {
        when (presentation) {
            is JwtVcJsonPresentation -> runParallelPolicies(policies.jwtVcJson) { runPolicy(presentation, verificationContext) }
            is DcSdJwtPresentation -> runParallelPolicies(policies.dcSdJwt) { runPolicy(presentation, verificationContext) }
            is MsoMdocPresentation -> runParallelPolicies(policies.msoMdoc) {
                runPolicy(document = presentation.mdoc.document, mso = presentation.mdoc.documentMso, verificationContext)
            }

            is LdpVcPresentation -> throw NotImplementedError("Verifying LDP presentations is not yet implemented!")
        }

    }

}
