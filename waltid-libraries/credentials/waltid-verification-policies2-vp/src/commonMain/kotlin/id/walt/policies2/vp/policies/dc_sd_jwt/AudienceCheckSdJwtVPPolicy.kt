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
        verificationContext: VerificationSessionContext?
    ): Result<Unit> {
        requireNotNull(verificationContext) { "Verification context needs to be provided for AudienceCheck SD-JWT VP Policy" }

        val presentationAudience = presentation.audience
        val expectedAudience = verificationContext.expectedAudience
        val x509HashAudience = verificationContext.x509HashAudience

        addResult("presentation_audience", presentationAudience)
        addResult("expected_audience", expectedAudience)
        if (x509HashAudience != null) {
            addResult("x509_hash_audience", x509HashAudience)
        }

        // Per HAIP: when using x509_san_dns with signed requests, the wallet MAY use
        // x509_hash:<base64url-sha256-of-der-cert> as the KB-JWT audience instead of
        // the original client_id. Accept either format.
        val audienceMatches = presentationAudience == expectedAudience ||
                (x509HashAudience != null && presentationAudience == x509HashAudience)

        presentationRequire(
            audienceMatches,
            DcSdJwtPresentationValidationError.AUDIENCE_MISMATCH
        ) {
            val acceptedAudiences = listOfNotNull(expectedAudience, x509HashAudience).joinToString(" or ")
            "Expected $acceptedAudiences, got $presentationAudience"
        }

        return success()
    }
}
