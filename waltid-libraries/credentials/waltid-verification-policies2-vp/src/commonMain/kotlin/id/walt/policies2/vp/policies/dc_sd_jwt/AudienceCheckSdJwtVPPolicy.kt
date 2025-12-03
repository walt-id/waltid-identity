@file:Suppress("PackageDirectoryMismatch")

package id.walt.policies2.vp.policies

import id.walt.credentials.presentations.DcSdJwtPresentationValidationError
import id.walt.credentials.presentations.PresentationValidationExceptionFunctions.presentationRequire
import id.walt.credentials.presentations.formats.DcSdJwtPresentation
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val policyId = "dc+sd-jwt/audience-check"

@Serializable
@SerialName(policyId)
class AudienceCheckSdJwtVPPolicy : DcSdJwtVPPolicy() {
    override val id = policyId
    override val description = "Check if presentation audience matches expected audience for session"

    companion object {
        val log = KotlinLogging.logger { }
    }

    override suspend fun VPPolicyRunContext.verifySdJwtPolicy(
        presentation: DcSdJwtPresentation,
        verificationContext: VerificationSessionContext
    ): Result<Unit> {
        addResult("presentation_audience", presentation.audience)
        addResult("expected_audience", verificationContext.expectedAudience)
        presentationRequire(
            presentation.audience == verificationContext.expectedAudience,
            DcSdJwtPresentationValidationError.AUDIENCE_MISMATCH
        ) { "Expected ${verificationContext.expectedAudience}, got ${presentation.audience}" }

        return success()
    }
}
