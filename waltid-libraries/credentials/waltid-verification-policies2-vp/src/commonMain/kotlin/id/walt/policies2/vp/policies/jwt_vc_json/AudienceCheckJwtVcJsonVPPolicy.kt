@file:Suppress("PackageDirectoryMismatch")

package id.walt.policies2.vp.policies

import id.walt.credentials.presentations.PresentationValidationExceptionFunctions.presentationRequire
import id.walt.credentials.presentations.W3CPresentationValidationError
import id.walt.credentials.presentations.formats.JwtVcJsonPresentation
import io.github.oshai.kotlinlogging.KotlinLogging

class AudienceCheckJwtVcJsonVPPolicy : JwtVcJsonVPPolicy(
    "audience-check",
    "Check if presentation audience matches expected audience for session"
) {

    companion object {
        val log = KotlinLogging.logger { }
    }

    override suspend fun VPPolicyRunContext.verifyJwtVcJsonPolicy(
        presentation: JwtVcJsonPresentation,
        verificationContext: JwtVcJsonVPVerificationRequest
    ): Result<Unit> {
        addResult("presentation_audience", presentation.audience)
        addResult("expected_audience", verificationContext.base.expectedAudience)
        presentationRequire(
            presentation.audience == verificationContext.base.expectedAudience,
            W3CPresentationValidationError.AUDIENCE_MISMATCH
        ) { "Expected ${verificationContext.base.expectedAudience}, got ${presentation.audience}" }

        return success()
    }
}
