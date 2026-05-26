@file:Suppress("PackageDirectoryMismatch")

package id.walt.policies2.vp.policies

import id.walt.credentials.presentations.DcSdJwtPresentationValidationError
import id.walt.credentials.presentations.PresentationValidationExceptionFunctions.presentationRequire
import id.walt.credentials.presentations.PresentationValidationExceptionFunctions.presentationRequireNotNull
import id.walt.credentials.presentations.formats.DcSdJwtPresentation
import id.walt.crypto.utils.ShaUtils.calculateSha256Base64Url
import id.walt.crypto.utils.JwsUtils.decodeJws
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

private const val policyId = "dc+sd-jwt/transaction-data-hashes-check"

/**
 * Validates the `transaction_data_hashes` claim in the KB-JWT per OID4VP 1.0 §5.5.1.
 *
 * When the authorization request included `transaction_data`, the KB-JWT MUST contain
 * `transaction_data_hashes` — one SHA-256 hash per transaction data item, in the same order.
 * Each hash is computed over the base64url-encoded transaction data item string as it appeared
 * in the authorization request.
 *
 * If the authorization request contained no `transaction_data` this policy passes without error.
 */
@Serializable
@SerialName(policyId)
class TransactionDataHashesVPPolicy : DcSdJwtVPPolicy() {

    override val id = policyId
    override val description =
        "Verify KB-JWT 'transaction_data_hashes' against the requested transaction data items"

    companion object {
        private val log = KotlinLogging.logger { }
    }

    override suspend fun VPPolicyRunContext.verifySdJwtPolicy(
        presentation: DcSdJwtPresentation,
        verificationContext: VerificationSessionContext?
    ): Result<Unit> {
        val expectedTransactionData = verificationContext?.transactionData
        if (expectedTransactionData.isNullOrEmpty()) {
            // No transaction_data in the request — nothing to check.
            return success()
        }

        val kbJwtPayload = presentation.keyBindingJwt.decodeJws().payload

        // transaction_data_hashes MUST be present when transaction_data was requested.
        val hashesElement = kbJwtPayload["transaction_data_hashes"]
        presentationRequireNotNull(
            hashesElement,
            DcSdJwtPresentationValidationError.MISSING_TRANSACTION_DATA_HASHES
        )

        val presentedHashes = runCatching {
            hashesElement!!.jsonArray.map { it.jsonPrimitive.contentOrNull ?: "" }
        }.getOrElse {
            presentationRequire(false, DcSdJwtPresentationValidationError.MISSING_TRANSACTION_DATA_HASHES) {
                "transaction_data_hashes is not a JSON array of strings"
            }
            return success() // unreachable — presentationRequire always throws on false
        }

        addResult("presented_transaction_data_hashes_count", presentedHashes.size.toString())
        addResult("expected_transaction_data_count", expectedTransactionData.size.toString())

        // The number of hashes must match the number of transaction_data items.
        presentationRequire(
            presentedHashes.size == expectedTransactionData.size,
            DcSdJwtPresentationValidationError.TRANSACTION_DATA_HASHES_MISMATCH
        ) {
            "transaction_data_hashes count (${presentedHashes.size}) does not match " +
                "transaction_data count (${expectedTransactionData.size})"
        }

        // Each hash must equal SHA-256(base64url-encoded transaction_data item).
        expectedTransactionData.forEachIndexed { index, item ->
            val expectedHash = calculateSha256Base64Url(item)
            val presentedHash = presentedHashes[index]
            presentationRequire(
                presentedHash == expectedHash,
                DcSdJwtPresentationValidationError.TRANSACTION_DATA_HASHES_MISMATCH
            ) {
                "transaction_data_hashes[$index] mismatch: expected=$expectedHash, presented=$presentedHash"
            }
        }

        log.trace { "transaction_data_hashes validated successfully (${presentedHashes.size} items)" }
        return success()
    }
}
