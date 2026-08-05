@file:Suppress("PackageDirectoryMismatch")

package id.walt.policies2.vp.policies

import id.walt.credentials.presentations.formats.DcSdJwtPresentation
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val policyId = "dc+sd-jwt/transaction-data-hashes-check"

/**
 * Validates the `transaction_data_hashes` claim in the KB-JWT per OID4VP 1.0 §A.3.2.
 *
 * When the authorization request included `transaction_data`:
 * - The KB-JWT MUST contain `transaction_data_hashes` — one hash per item, in the same order.
 * - Each hash is computed over the **raw base64url-encoded string** as received in the request
 *   (no base64url decoding before hashing).
 * - If the request's `transaction_data` objects specified `transaction_data_hashes_alg`, the
 *   KB-JWT MUST include `transaction_data_hashes_alg` naming the algorithm used, and it MUST
 *   be one of the requested values. SHA-256 is the mandatory default.
 *
 * If the authorization request contained no `transaction_data` this policy passes without error.
 */
@Serializable
@SerialName(policyId)
@Deprecated("Use TransactionDataHashCheckSdJwtVPPolicy")
class TransactionDataHashesVPPolicy : DcSdJwtVPPolicy() {

    companion object {
        const val ID = policyId
    }

    override val id = policyId
    override val description = "Compatibility alias for canonical per-credential transaction data hash verification"

    override suspend fun VPPolicyRunContext.verifySdJwtPolicy(
        presentation: DcSdJwtPresentation,
        verificationContext: VerificationSessionContext?
    ): Result<Unit> = TransactionDataHashCheckSdJwtVPPolicy().verifyCanonical(
        context = this,
        presentation = presentation,
        verificationContext = verificationContext?.let { context ->
            if (context.expectedTransactionData == null && context.transactionData != null) {
                context.copy(expectedTransactionData = context.transactionData)
            } else context
        },
    )
}
