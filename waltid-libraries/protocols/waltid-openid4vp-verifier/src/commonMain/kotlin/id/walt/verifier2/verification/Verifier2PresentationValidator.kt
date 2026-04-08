package id.walt.verifier2.verification

import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.presentations.formats.VerifiablePresentation
import id.walt.dcql.models.ClaimsQuery
import id.walt.dcql.models.CredentialFormat
import kotlinx.serialization.ExperimentalSerializationApi

@Deprecated(
    message = "Use PresentationVerificationEngine and VP policies for verifier flows. This validator remains as a compatibility shim.",
)
@OptIn(ExperimentalSerializationApi::class)
object Verifier2PresentationValidator {

    data class PresentationValidationResult(
        val presentation: VerifiablePresentation,
        val credentials: List<DigitalCredential>,
    )

    suspend fun validatePresentation(
        presentationString: String,
        expectedFormat: CredentialFormat,
        expectedAudience: String?,
        expectedNonce: String,
        responseUri: String?,
        originalClaimsQuery: List<ClaimsQuery>?,
        expectedTransactionData: List<String>? = null,
        isDcApi: Boolean,
        isEncrypted: Boolean,
        verifierOrigin: String?,
        jwkThumbprint: String?,
    ): Result<PresentationValidationResult> = when (expectedFormat) {
        CredentialFormat.JWT_VC_JSON -> W3CPresentationValidator.validateW3cVpJwt(
            vpJwtString = presentationString,
            expectedAudience = expectedAudience,
            expectedNonce = expectedNonce,
        )

        CredentialFormat.DC_SD_JWT -> SdJwtVcPresentationValidator.validateSdJwtVcPresentation(
            sdJwtPresentationString = presentationString,
            expectedAudience = expectedAudience,
            expectedNonce = expectedNonce,
            originalClaimsQuery = originalClaimsQuery,
            expectedTransactionData = expectedTransactionData,
        )

        CredentialFormat.MSO_MDOC -> MdocPresentationValidator.validateMsoMdocPresentation(
            mdocBase64UrlString = presentationString,
            expectedNonce = expectedNonce,
            expectedAudience = if (isDcApi) verifierOrigin else expectedAudience,
            responseUri = responseUri,
            isDcApi = isDcApi,
            isEncrypted = isEncrypted,
            jwkThumbprint = jwkThumbprint,
            expectedTransactionData = expectedTransactionData,
        )

        CredentialFormat.LDP_VC, CredentialFormat.AC_VP -> Result.failure(
            UnsupportedOperationException("Format $expectedFormat not supported for validation yet."),
        )
    }
}
