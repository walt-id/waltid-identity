package id.walt.openid4vp.verifier.verification

import id.walt.credentials.presentations.formats.DcSdJwtPresentation
import id.walt.dcql.models.ClaimsQuery
import id.walt.openid4vp.verifier.verification.Verifier2PresentationValidator.PresentationValidationResult

object SdJwtVcPresentationValidator {

    /**
     * Validates a full SD-JWT VC presentation string (core~disclosures~kb-jwt).
     */
    suspend fun validateSdJwtVcPresentation(
        sdJwtPresentationString: String,
        expectedAudience: String?,
        expectedNonce: String,
        originalClaimsQuery: List<ClaimsQuery>?
    ): Result<PresentationValidationResult> {
        val presentation = DcSdJwtPresentation.parse(sdJwtPresentationString)
            .getOrThrow()
        presentation.presentationVerification(
            expectedAudience,
            expectedNonce,
            originalClaimsQuery
        )

        return Result.success(
            PresentationValidationResult(
                presentation,
                listOf(presentation.credential)
            )
        )
    }

}
