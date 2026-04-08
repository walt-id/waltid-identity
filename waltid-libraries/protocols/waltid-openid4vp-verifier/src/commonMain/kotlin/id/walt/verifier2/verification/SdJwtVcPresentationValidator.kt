package id.walt.verifier2.verification

import id.walt.credentials.presentations.DcSdJwtPresentationValidationError
import id.walt.credentials.presentations.PresentationValidationExceptionFunctions.presentationRequire
import id.walt.credentials.presentations.formats.DcSdJwtPresentation
import id.walt.dcql.models.ClaimsQuery
import id.walt.verifier.openid.TransactionDataUtils
import id.walt.verifier2.verification.Verifier2PresentationValidator.PresentationValidationResult

@Deprecated(
    message = "Use PresentationVerificationEngine and VP policies for verifier flows. This validator remains as a compatibility shim.",
)
object SdJwtVcPresentationValidator {

    suspend fun validateSdJwtVcPresentation(
        sdJwtPresentationString: String,
        expectedAudience: String?,
        expectedNonce: String,
        originalClaimsQuery: List<ClaimsQuery>?,
        expectedTransactionData: List<String>? = null,
    ): Result<PresentationValidationResult> {
        val presentation = DcSdJwtPresentation.parse(sdJwtPresentationString).getOrThrow()
        presentation.presentationVerification(
            expectedAudience,
            expectedNonce,
            originalClaimsQuery,
        )

        val transactionDataValidationError = runCatching {
            TransactionDataUtils.validateResponseTransactionData(
                expectedTransactionData = expectedTransactionData,
                transactionDataHashes = presentation.transactionDataHashes,
                transactionDataHashesAlg = presentation.transactionDataHashesAlg,
            )
        }.exceptionOrNull()

        if (transactionDataValidationError != null) {
            val error = when ((transactionDataValidationError as? TransactionDataUtils.TransactionDataValidationException)?.reason) {
                TransactionDataUtils.TransactionDataValidationErrorReason.MISSING_HASHES ->
                    DcSdJwtPresentationValidationError.MISSING_TRANSACTION_DATA_HASHES

                TransactionDataUtils.TransactionDataValidationErrorReason.HASH_ALGORITHM_MISMATCH ->
                    DcSdJwtPresentationValidationError.TRANSACTION_DATA_HASH_ALGORITHM_MISMATCH

                else -> DcSdJwtPresentationValidationError.TRANSACTION_DATA_HASHES_MISMATCH
            }
            presentationRequire(false, error) { transactionDataValidationError.message.orEmpty() }
        }

        return Result.success(
            PresentationValidationResult(
                presentation = presentation,
                credentials = listOf(presentation.credential),
            ),
        )
    }
}
