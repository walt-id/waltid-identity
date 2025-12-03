@file:Suppress("PackageDirectoryMismatch")

package id.walt.policies2.vp.policies

import id.walt.credentials.presentations.DcSdJwtPresentationValidationError
import id.walt.credentials.presentations.PresentationValidationExceptionFunctions.presentationRequire
import id.walt.credentials.presentations.formats.DcSdJwtPresentation
import io.github.oshai.kotlinlogging.KotlinLogging

class NonceCheckSdJwtVPPolicy : DcSdJwtVPPolicy(
    "nonce-check",
    "Check if presentation nonce matches expected nonce for session"
) {

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
