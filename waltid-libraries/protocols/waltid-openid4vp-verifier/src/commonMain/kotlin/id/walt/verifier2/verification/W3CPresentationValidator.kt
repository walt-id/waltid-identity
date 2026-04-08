package id.walt.verifier2.verification

import id.walt.credentials.presentations.formats.JwtVcJsonPresentation
import id.walt.verifier2.verification.Verifier2PresentationValidator.PresentationValidationResult

@Deprecated(
    message = "Use PresentationVerificationEngine and VP policies for verifier flows. This validator remains as a compatibility shim.",
)
object W3CPresentationValidator {

    suspend fun validateW3cVpJwt(
        vpJwtString: String,
        expectedAudience: String?,
        expectedNonce: String,
    ): Result<PresentationValidationResult> {
        val presentation = JwtVcJsonPresentation.parse(vpJwtString).getOrThrow()
        presentation.presentationVerification(expectedAudience, expectedNonce)

        return Result.success(
            PresentationValidationResult(
                presentation = presentation,
                credentials = presentation.credentials ?: emptyList(),
            ),
        )
    }
}
