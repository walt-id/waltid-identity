@file:Suppress("PackageDirectoryMismatch")

package id.walt.policies2.vp.policies

import id.walt.credentials.presentations.DcSdJwtPresentationValidationError
import id.walt.credentials.presentations.PresentationValidationExceptionFunctions.presentationRequire
import id.walt.credentials.presentations.formats.DcSdJwtPresentation
import id.walt.policies2.vp.policies.TransactionDataHashCheckSdJwtVPPolicy.ValidationErrorReason.HASH_ALGORITHM_MISMATCH
import id.walt.policies2.vp.policies.TransactionDataHashCheckSdJwtVPPolicy.ValidationErrorReason.MISSING_HASHES
import id.walt.verifier.openid.transactiondata.DEFAULT_HASH_ALGORITHM
import id.walt.verifier.openid.transactiondata.calculateTransactionDataHashes
import id.walt.verifier.openid.transactiondata.decodeList
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val policyId = "dc+sd-jwt/transaction-data-hash-check"

@Serializable
@SerialName(policyId)
class TransactionDataHashCheckSdJwtVPPolicy : DcSdJwtVPPolicy() {

    private enum class ValidationErrorReason {
        MISSING_HASHES,
        HASHES_MISMATCH,
        HASH_ALGORITHM_MISMATCH,
    }

    private class ValidationException(
        val reason: ValidationErrorReason,
        message: String,
    ) : IllegalArgumentException(message)

    override val id = policyId
    override val description = "Verify transaction_data hash binding against the verifier request"

    override suspend fun VPPolicyRunContext.verifySdJwtPolicy(
        presentation: DcSdJwtPresentation,
        verificationContext: VerificationSessionContext?
    ): Result<Unit> {
        addResult("expected_transaction_data_items", verificationContext?.expectedTransactionData?.size ?: 0)
        addResult("presentation_transaction_data_hashes", presentation.transactionDataHashes)
        addResult("presentation_transaction_data_hashes_alg", presentation.transactionDataHashesAlg)

        if (verificationContext == null) {
            require(presentation.transactionDataHashes == null && presentation.transactionDataHashesAlg == null) {
                "transaction_data_hashes must be omitted when verification context is not provided"
            }
            return success()
        }

        val validationError = runCatching {
            validate(
                expectedTransactionData = verificationContext.expectedTransactionData,
                transactionDataHashes = presentation.transactionDataHashes,
                transactionDataHashesAlg = presentation.transactionDataHashesAlg,
            )
        }.exceptionOrNull()

        if (validationError != null) {
            val error = when ((validationError as? ValidationException)?.reason) {
                MISSING_HASHES -> DcSdJwtPresentationValidationError.MISSING_TRANSACTION_DATA_HASHES
                HASH_ALGORITHM_MISMATCH -> DcSdJwtPresentationValidationError.TRANSACTION_DATA_HASH_ALGORITHM_MISMATCH

                else -> DcSdJwtPresentationValidationError.TRANSACTION_DATA_HASHES_MISMATCH
            }
            presentationRequire(false, error) { validationError.message.orEmpty() }
        }

        return success()
    }

    private fun validate(
        expectedTransactionData: List<String>?,
        transactionDataHashes: List<String>?,
        transactionDataHashesAlg: String?,
    ) {
        val expectedItems = expectedTransactionData.orEmpty()
        if (expectedItems.isEmpty()) {
            requireValidation(transactionDataHashes == null, ValidationErrorReason.HASHES_MISMATCH) {
                "transaction_data_hashes must be omitted when transaction_data is not requested"
            }
            requireValidation(transactionDataHashesAlg == null, HASH_ALGORITHM_MISMATCH) {
                "transaction_data_hashes_alg must be omitted when transaction_data is not requested"
            }
            return
        }

        val decodedExpectedItems = decodeList(expectedItems)
        val expectedAlgorithm = normalizeHashAlgorithm(transactionDataHashesAlg ?: DEFAULT_HASH_ALGORITHM)

        requireValidation(expectedAlgorithm == DEFAULT_HASH_ALGORITHM, HASH_ALGORITHM_MISMATCH) {
            "Unsupported transaction_data hash algorithm: $transactionDataHashesAlg"
        }

        decodedExpectedItems.forEach { decodedItem ->
            decodedItem.transactionData.transactionDataHashesAlg?.let(::requireSupportedHashAlgorithms)
        }

        requireValidation(!transactionDataHashes.isNullOrEmpty(), MISSING_HASHES) {
            "transaction_data_hashes must be present when transaction_data is requested"
        }
        val actualTransactionDataHashes = transactionDataHashes!!

        requireValidation(actualTransactionDataHashes.size == expectedItems.size, ValidationErrorReason.HASHES_MISMATCH) {
            "transaction_data_hashes must contain one entry per transaction_data item"
        }

        val expectedHashes = calculateTransactionDataHashes(expectedItems, expectedAlgorithm)
        requireValidation(actualTransactionDataHashes == expectedHashes, ValidationErrorReason.HASHES_MISMATCH) {
            "transaction_data_hashes do not match the requested transaction_data"
        }

        val requestedAlgorithmsWereExplicit = decodedExpectedItems
            .any { !it.transactionData.transactionDataHashesAlg.isNullOrEmpty() }

        if (requestedAlgorithmsWereExplicit) {
            requireValidation(transactionDataHashesAlg != null, HASH_ALGORITHM_MISMATCH) {
                "transaction_data_hashes_alg is required when transaction_data_hashes_alg is present in the request"
            }
        }
    }

    private fun normalizeHashAlgorithm(algorithm: String): String = algorithm.lowercase()

    private fun requireSupportedHashAlgorithms(algorithms: List<String>) {
        require(algorithms.isNotEmpty()) { "transaction_data_hashes_alg must not be empty" }
        require(algorithms.any { normalizeHashAlgorithm(it) == DEFAULT_HASH_ALGORITHM }) {
            "Unsupported transaction_data_hashes_alg values: $algorithms"
        }
    }

    private inline fun requireValidation(
        condition: Boolean,
        reason: ValidationErrorReason,
        lazyMessage: () -> String,
    ) {
        if (!condition) throw ValidationException(reason, lazyMessage())
    }
}
