@file:Suppress("PackageDirectoryMismatch")

package id.walt.policies2.vp.policies

import id.walt.credentials.presentations.DcSdJwtPresentationValidationError
import id.walt.credentials.presentations.PresentationValidationExceptionFunctions.presentationRequire
import id.walt.credentials.presentations.formats.DcSdJwtPresentation
import id.walt.verifier.openid.TransactionDataUtils
import id.walt.verifier.openid.TransactionDataUtils.TransactionDataValidationErrorReason.HASH_ALGORITHM_MISMATCH
import id.walt.verifier.openid.TransactionDataUtils.TransactionDataValidationErrorReason.MISSING_HASHES
import id.walt.verifier.openid.TransactionDataUtils.TransactionDataValidationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val policyId = "dc+sd-jwt/transaction-data-hash-check"

@Serializable
@SerialName(policyId)
class TransactionDataHashCheckSdJwtVPPolicy : DcSdJwtVPPolicy() {

    override val id = policyId
    override val description = "Verify transaction_data hash binding against the verifier request"

    override suspend fun VPPolicyRunContext.verifySdJwtPolicy(
        presentation: DcSdJwtPresentation,
        verificationContext: VerificationSessionContext?
    ): Result<Unit> {
        requireNotNull(verificationContext) {
            "Verification context needs to be provided for TransactionDataHashCheck SD-JWT VP Policy"
        }

        addResult("expected_transaction_data_items", verificationContext.expectedTransactionData?.size ?: 0)
        addResult("presentation_transaction_data_hashes", presentation.transactionDataHashes)
        addResult("presentation_transaction_data_hashes_alg", presentation.transactionDataHashesAlg)

        val validationError = runCatching {
            TransactionDataUtils.validateResponseTransactionData(
                expectedTransactionData = verificationContext.expectedTransactionData,
                transactionDataHashes = presentation.transactionDataHashes,
                transactionDataHashesAlg = presentation.transactionDataHashesAlg,
            )
        }.exceptionOrNull()

        if (validationError != null) {
            val error = when ((validationError as? TransactionDataValidationException)?.reason) {
                MISSING_HASHES -> DcSdJwtPresentationValidationError.MISSING_TRANSACTION_DATA_HASHES
                HASH_ALGORITHM_MISMATCH -> DcSdJwtPresentationValidationError.TRANSACTION_DATA_HASH_ALGORITHM_MISMATCH
                else -> DcSdJwtPresentationValidationError.TRANSACTION_DATA_HASHES_MISMATCH
            }
            presentationRequire(false, error) { validationError.message.orEmpty() }
        }

        return success()
    }
}
