package id.walt.verifier2.verification

import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.presentations.formats.DcSdJwtPresentation
import id.walt.credentials.presentations.formats.JwtVcJsonPresentation
import id.walt.credentials.presentations.formats.VerifiablePresentation
import id.walt.dcql.models.ClaimsQuery
import id.walt.dcql.models.CredentialFormat
import io.github.oshai.kotlinlogging.KotlinLogging

object Verifier2PresentationValidator {

    private val log = KotlinLogging.logger("Verifier2PresentationValidator")

    data class PresentationValidationResult(
        val presentation: VerifiablePresentation,
        val credentials: List<DigitalCredential>
    )

    suspend fun validatePresentation(
        presentationString: String,
        expectedFormat: CredentialFormat,
        expectedAudience: String,
        expectedNonce: String,
        originalClaimsQuery: List<ClaimsQuery>?
    ): Result<PresentationValidationResult> {
        return when (expectedFormat) {
            CredentialFormat.JWT_VC_JSON -> validateW3cVpJwtInternal(
                presentationString, expectedAudience, expectedNonce
            )

            CredentialFormat.DC_SD_JWT -> validateSdJwtVcPresentationInternal(
                presentationString, expectedAudience, expectedNonce, originalClaimsQuery
            )
            // TODO: Implement other formats (Mdoc, LDP)
            else -> Result.failure(UnsupportedOperationException("Format $expectedFormat not supported for validation yet."))
        }
    }

    private suspend fun validateW3cVpJwtInternal(
        vpJwtString: String,
        expectedAudience: String,
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

    /**
     * Validates a full SD-JWT VC presentation string (core~disclosures~kb-jwt).
     */
    private suspend fun validateSdJwtVcPresentationInternal(
        sdJwtPresentationString: String,
        expectedAudience: String,
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
