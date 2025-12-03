@file:Suppress("PackageDirectoryMismatch")

package id.walt.policies2.vp.policies

import id.walt.credentials.presentations.formats.DcSdJwtPresentation
import kotlinx.serialization.Serializable

@Serializable
sealed class DcSdJwtVPPolicy() : VPPolicy2() {

    abstract val sdJwtId: String
    override val id = "dc+sd-jwt/$sdJwtId"
    abstract override val description: String

    abstract suspend fun VPPolicyRunContext.verifySdJwtPolicy(
        presentation: DcSdJwtPresentation,
        verificationContext: DcSdJwtVPVerificationRequest
    ): Result<Unit>

    suspend fun runPolicy(
        presentation: DcSdJwtPresentation,
        verificationContext: DcSdJwtVPVerificationRequest
    ) = runPolicy {
        verifySdJwtPolicy(presentation, verificationContext)
    }

}
