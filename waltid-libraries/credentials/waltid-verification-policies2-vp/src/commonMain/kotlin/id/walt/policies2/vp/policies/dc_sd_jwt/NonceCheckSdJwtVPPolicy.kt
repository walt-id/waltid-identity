@file:Suppress("PackageDirectoryMismatch")

package id.walt.policies2.vp.policies

import id.walt.credentials.presentations.DcSdJwtPresentationValidationError
import id.walt.credentials.presentations.PresentationValidationExceptionFunctions.presentationRequire
import id.walt.credentials.presentations.formats.DcSdJwtPresentation
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val policyId = "dc+sd-jwt/nonce-check"

@Serializable
@SerialName(policyId)
class NonceCheckSdJwtVPPolicy : DcSdJwtVPPolicy() {

    override val id = policyId
    override val description = "Check if presentation nonce matches expected nonce for session"

    companion object {
        val log = KotlinLogging.logger { }
    }

    override suspend fun VPPolicyRunContext.verifySdJwtPolicy(
        presentation: DcSdJwtPresentation,
        verificationContext: DcSdJwtVPVerificationRequest
    ): Result<Unit> {
        presentationRequire(
            presentation.nonce == verificationContext.base.expectedNonce,
            DcSdJwtPresentationValidationError.NONCE_MISMATCH
        ) { "Expected ${verificationContext.base.expectedNonce}, got ${presentation.nonce}" }

        return success()
    }
}
