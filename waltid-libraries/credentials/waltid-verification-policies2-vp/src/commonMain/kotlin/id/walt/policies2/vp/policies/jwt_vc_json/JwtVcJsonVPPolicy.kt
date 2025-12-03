@file:Suppress("PackageDirectoryMismatch")

package id.walt.policies2.vp.policies

import id.walt.credentials.presentations.formats.JwtVcJsonPresentation

abstract class JwtVcJsonVPPolicy(jwtVcJsonId: String, description: String) : VPPolicy2("jwt_vc-json/$jwtVcJsonId", description) {
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
