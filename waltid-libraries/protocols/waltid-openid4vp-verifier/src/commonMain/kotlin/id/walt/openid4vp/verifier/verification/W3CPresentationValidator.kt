package id.walt.openid4vp.verifier.verification

import id.walt.credentials.presentations.formats.JwtVcJsonPresentation
import id.walt.openid4vp.verifier.verification.Verifier2PresentationValidator.PresentationValidationResult

object W3CPresentationValidator {

    suspend fun validateW3cVpJwt(
        vpJwtString: String,
        expectedAudience: String?,
        expectedNonce: String
    ): Result<PresentationValidationResult> {
        val presentation = JwtVcJsonPresentation.parse(vpJwtString)
            .getOrThrow()
        presentation.presentationVerification(expectedAudience, expectedNonce)

        return Result.success(
            PresentationValidationResult(
                presentation,
                presentation.credentials ?: emptyList()
            )
        )
    }

}
