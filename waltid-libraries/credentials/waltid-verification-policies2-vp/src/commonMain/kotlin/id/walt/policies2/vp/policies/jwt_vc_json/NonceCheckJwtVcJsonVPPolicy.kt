@file:Suppress("PackageDirectoryMismatch")

package id.walt.policies2.vp.policies

import id.walt.credentials.presentations.PresentationValidationExceptionFunctions.presentationRequire
import id.walt.credentials.presentations.W3CPresentationValidationError
import id.walt.credentials.presentations.formats.JwtVcJsonPresentation
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val policyId = "jwt_vc_json/nonce-check"

@Serializable
@SerialName(policyId)
class NonceCheckJwtVcJsonVPPolicy : JwtVcJsonVPPolicy() {

    override val id = policyId
    override val description = "Check if presentation nonce matches expected nonce for session"

    companion object {
        val log = KotlinLogging.logger { }
    }

    override suspend fun VPPolicyRunContext.verifyJwtVcJsonPolicy(
        presentation: JwtVcJsonPresentation,
        verificationContext: VerificationSessionContext
    ): Result<Unit> {
        presentationRequire(
            presentation.nonce == verificationContext.expectedNonce,
            W3CPresentationValidationError.NONCE_MISMATCH
        ) { "Expected ${verificationContext.expectedNonce}, got ${presentation.nonce}" }

        return success()
    }
}
