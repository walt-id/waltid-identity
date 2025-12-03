@file:Suppress("PackageDirectoryMismatch")

package id.walt.policies2.vp.policies

import id.walt.credentials.presentations.PresentationValidationExceptionFunctions.presentationRequire
import id.walt.credentials.presentations.W3CPresentationValidationError
import id.walt.credentials.presentations.formats.JwtVcJsonPresentation
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val policyId = "jwt_vc_json/audience-check"

@Serializable
@SerialName(policyId)
class AudienceCheckJwtVcJsonVPPolicy : JwtVcJsonVPPolicy() {

    override val id = policyId
    override val description = "Check if presentation audience matches expected audience for session"

    companion object {
        val log = KotlinLogging.logger { }
    }

    override suspend fun VPPolicyRunContext.verifyJwtVcJsonPolicy(
        presentation: JwtVcJsonPresentation,
        verificationContext: VerificationSessionContext
    ): Result<Unit> {
        addResult("presentation_audience", presentation.audience)
        addResult("expected_audience", verificationContext.expectedAudience)
        presentationRequire(
            presentation.audience == verificationContext.expectedAudience,
            W3CPresentationValidationError.AUDIENCE_MISMATCH
        ) { "Expected ${verificationContext.expectedAudience}, got ${presentation.audience}" }

        return success()
    }
}
