@file:Suppress("PackageDirectoryMismatch")

package id.walt.policies2.vp.policies

import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.objects.mso.MobileSecurityObject
import id.walt.verifier.openid.TransactionDataUtils
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val policyId = "mso_mdoc/transaction-data-hash-check"

@Serializable
@SerialName(policyId)
class TransactionDataMdocVpPolicy : MdocVPPolicy() {

    override val id = policyId
    override val description = "Verify transaction_data binding embedded in mdoc DeviceSigned data"

    override suspend fun VPPolicyRunContext.verifyMdocPolicy(
        document: Document,
        mso: MobileSecurityObject,
        verificationContext: VerificationSessionContext?
    ): Result<Unit> {
        requireNotNull(verificationContext) {
            "Verification context needs to be provided for TransactionData mdoc VP Policy"
        }

        val expectedTransactionData = verificationContext.expectedTransactionData.orEmpty()
        val embeddedTransactionData = TransactionDataUtils.extractMdocEmbeddedTransactionData(
            deviceSignedItems = document.deviceSigned
                ?.namespaces
                ?.value
                ?.entries
                ?.get(TransactionDataUtils.MDOC_DEVICE_SIGNED_NAMESPACE)
                ?.entries
                ?.associate { it.key to it.value }
                .orEmpty()
        )

        addResult("expected_transaction_data_items", expectedTransactionData.size)
        addResult("embedded_transaction_data_items", embeddedTransactionData.size)

        if (expectedTransactionData.isEmpty()) {
            require(embeddedTransactionData.isEmpty()) {
                "mdoc transaction_data entries must be omitted when transaction_data is not requested"
            }
            return success()
        }

        val algorithm = TransactionDataUtils.resolveHashAlgorithm(
            TransactionDataUtils.decodeTransactionDataList(expectedTransactionData)
        ) ?: TransactionDataUtils.DEFAULT_HASH_ALGORITHM
        val expectedHashes = TransactionDataUtils.calculateTransactionDataHashes(expectedTransactionData, algorithm)
        val embeddedHashes = TransactionDataUtils.calculateTransactionDataHashes(embeddedTransactionData, algorithm)

        addResult("transaction_data_hash_algorithm", algorithm)
        addResult("embedded_transaction_data_hashes", embeddedHashes)

        require(embeddedHashes == expectedHashes) {
            "mdoc transaction_data does not match the requested transaction_data"
        }

        return success()
    }
}
