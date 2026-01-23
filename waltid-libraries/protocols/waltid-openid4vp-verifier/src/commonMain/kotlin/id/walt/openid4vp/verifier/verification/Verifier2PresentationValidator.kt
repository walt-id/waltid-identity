package id.walt.openid4vp.verifier.verification

import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.presentations.formats.VerifiablePresentation
import id.walt.dcql.models.ClaimsQuery
import id.walt.dcql.models.CredentialFormat
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.ExperimentalSerializationApi

@OptIn(ExperimentalSerializationApi::class)
object Verifier2PresentationValidator {

    private val log = KotlinLogging.logger("Verifier2PresentationValidator")

    data class PresentationValidationResult(
        val presentation: VerifiablePresentation,
        val credentials: List<DigitalCredential>
    )

    suspend fun validatePresentation(
        presentationString: String,
        expectedFormat: CredentialFormat,
        expectedAudience: String?,
        expectedNonce: String,
        responseUri: String?,
        originalClaimsQuery: List<ClaimsQuery>?,

        isDcApi: Boolean,
        isEncrypted: Boolean,
        verifierOrigin: String?,
        jwkThumbprint: String?
    ): Result<PresentationValidationResult> {
        return when (expectedFormat) {
            CredentialFormat.JWT_VC_JSON -> W3CPresentationValidator.validateW3cVpJwt(
                vpJwtString = presentationString,
                expectedAudience = expectedAudience,
                expectedNonce = expectedNonce
            )

            CredentialFormat.DC_SD_JWT -> SdJwtVcPresentationValidator.validateSdJwtVcPresentation(
                sdJwtPresentationString = presentationString,
                expectedAudience = expectedAudience,
                expectedNonce = expectedNonce,
                originalClaimsQuery = originalClaimsQuery
            )

            CredentialFormat.MSO_MDOC -> MdocPresentationValidator.validateMsoMdocPresentation(
                mdocBase64UrlString = presentationString,
                expectedNonce = expectedNonce,
                expectedAudience = if (isDcApi) verifierOrigin else expectedAudience,
                responseUri = responseUri,

                isDcApi = isDcApi,
                isEncrypted = isEncrypted,
                jwkThumbprint = jwkThumbprint,
            )

            // Future: Implement other formats (e.g. LDP)
            CredentialFormat.LDP_VC, CredentialFormat.AC_VP -> Result.failure(UnsupportedOperationException("Format $expectedFormat not supported for validation yet."))
        }
    }


}
