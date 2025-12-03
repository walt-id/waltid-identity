@file:Suppress("PackageDirectoryMismatch")

package id.walt.policies2.vp.policies

import id.walt.credentials.presentations.formats.JwtVcJsonPresentation
import kotlinx.serialization.Serializable

@Serializable
abstract class JwtVcJsonVPPolicy() : VPPolicy2() {
    abstract suspend fun VPPolicyRunContext.verifyJwtVcJsonPolicy(
        presentation: JwtVcJsonPresentation,
        verificationContext: JwtVcJsonVPVerificationRequest
    ): Result<Unit>

    suspend fun runPolicy(
        presentation: JwtVcJsonPresentation,
        verificationContext: JwtVcJsonVPVerificationRequest
    ) = runPolicy {
        verifyJwtVcJsonPolicy(
            presentation = presentation,
            verificationContext = verificationContext
        )
    }

}
