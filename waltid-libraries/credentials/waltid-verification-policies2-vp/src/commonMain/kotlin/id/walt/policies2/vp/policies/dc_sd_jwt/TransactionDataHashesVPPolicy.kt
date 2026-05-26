@file:Suppress("PackageDirectoryMismatch")

package id.walt.policies2.vp.policies

import id.walt.credentials.presentations.DcSdJwtPresentationValidationError
import id.walt.credentials.presentations.PresentationValidationExceptionFunctions.presentationRequire
import id.walt.credentials.presentations.PresentationValidationExceptionFunctions.presentationRequireNotNull
import id.walt.credentials.presentations.formats.DcSdJwtPresentation
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.crypto.utils.ShaUtils.calculateSha256Base64Url
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
class TransactionDataHashesVPPolicy : DcSdJwtVPPolicy() {

    override val id = policyId
    override val description =
        "Verify KB-JWT 'transaction_data_hashes' against the requested transaction data items"

    companion object {
        private val log = KotlinLogging.logger { }
        private const val SHA_256 = "sha-256"
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

        // Determine the set of allowed algorithms from each transaction_data item's
        // transaction_data_hashes_alg field. If any item specifies it, the KB-JWT MUST
        // include transaction_data_hashes_alg naming the algorithm used.
        val requestedAlgsPerItem = expectedTransactionData.map { itemB64 ->
            runCatching {
                val itemJson = Json.parseToJsonElement(
                    itemB64.decodeFromBase64Url().decodeToString()
                ).jsonObject
                itemJson["transaction_data_hashes_alg"]?.jsonArray
                    ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                    ?: emptyList()
            }.getOrDefault(emptyList())
        }
        val anyItemSpecifiesAlg = requestedAlgsPerItem.any { it.isNotEmpty() }

        // Resolve the algorithm to verify against.
        val algToVerify: String
        if (anyItemSpecifiesAlg) {
            // transaction_data_hashes_alg MUST be present in the KB-JWT.
            val presentedAlg = kbJwtPayload["transaction_data_hashes_alg"]?.jsonPrimitive?.contentOrNull
            presentationRequireNotNull(
                presentedAlg,
                DcSdJwtPresentationValidationError.TRANSACTION_DATA_HASH_ALGORITHM_MISMATCH
            ) { "KB-JWT 'transaction_data_hashes_alg' is required when request specified hash algorithms" }

            // The presented alg must be in the allowed set for every item that specified one.
            val allowedAlgs = requestedAlgsPerItem.filter { it.isNotEmpty() }.flatten().toSet()
            presentationRequire(
                presentedAlg!! in allowedAlgs,
                DcSdJwtPresentationValidationError.TRANSACTION_DATA_HASH_ALGORITHM_MISMATCH
            ) { "transaction_data_hashes_alg '$presentedAlg' is not in allowed set $allowedAlgs" }

            algToVerify = presentedAlg
            addResult("transaction_data_hashes_alg", algToVerify)
        } else {
            // Default: sha-256.
            algToVerify = SHA_256
        }

        // Only SHA-256 is currently supported. Reject any other algorithm.
        presentationRequire(
            algToVerify == SHA_256,
            DcSdJwtPresentationValidationError.TRANSACTION_DATA_HASH_ALGORITHM_MISMATCH
        ) { "Unsupported transaction_data_hashes_alg '$algToVerify' (only '$SHA_256' is supported)" }

        // The number of hashes must match the number of transaction_data items.
        presentationRequire(
            presentedHashes.size == expectedTransactionData.size,
            DcSdJwtPresentationValidationError.TRANSACTION_DATA_HASHES_MISMATCH
        ) {
            "transaction_data_hashes count (${presentedHashes.size}) does not match " +
                "transaction_data count (${expectedTransactionData.size})"
        }

        // Each hash must equal SHA-256(raw base64url string, not decoded).
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

        log.trace { "transaction_data_hashes validated successfully (${presentedHashes.size} items, alg=$algToVerify)" }
        return success()
    }
}
