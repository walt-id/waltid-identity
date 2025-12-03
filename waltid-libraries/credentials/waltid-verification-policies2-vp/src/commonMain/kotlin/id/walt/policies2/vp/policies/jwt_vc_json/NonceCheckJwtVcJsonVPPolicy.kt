@file:Suppress("PackageDirectoryMismatch")

package id.walt.policies2.vp.policies

import id.walt.credentials.presentations.PresentationValidationExceptionFunctions.presentationRequire
import id.walt.credentials.presentations.W3CPresentationValidationError
import id.walt.credentials.presentations.formats.JwtVcJsonPresentation
import io.github.oshai.kotlinlogging.KotlinLogging

class NonceCheckJwtVcJsonVPPolicy : JwtVcJsonVPPolicy(
    "nonce-check",
    "Check if presentation nonce matches expected nonce for session"
) {

    companion object {
        val log = KotlinLogging.logger { }
    }

    override suspend fun VPPolicyRunContext.verifyJwtVcJsonPolicy(
        presentation: JwtVcJsonPresentation,
        verificationContext: JwtVcJsonVPVerificationRequest
    ): Result<Unit> {
        presentationRequire(
            presentation.nonce == verificationContext.base.expectedNonce,
            W3CPresentationValidationError.NONCE_MISMATCH
        ) { "Expected ${verificationContext.base.expectedNonce}, got ${presentation.nonce}" }

        return success()
    }
}
