@file:Suppress("PackageDirectoryMismatch")

package id.walt.policies2.vp.policies

import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.objects.mso.MobileSecurityObject
import id.walt.verifier.openid.transactiondata.DEFAULT_HASH_ALGORITHM
import id.walt.verifier.openid.transactiondata.MDOC_DEVICE_SIGNED_NAMESPACE
import id.walt.verifier.openid.transactiondata.calculateTransactionDataHashes
import id.walt.verifier.openid.transactiondata.decodeList
import id.walt.verifier.openid.transactiondata.parseDeviceSignedItemIndex
import id.walt.verifier.openid.transactiondata.requireContiguousIndices
import id.walt.verifier.openid.transactiondata.resolveHashAlgorithm
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
        val embeddedTransactionData = extract(
            deviceSignedItems = document.deviceSigned
                ?.namespaces
                ?.value
                ?.entries
                ?.get(MDOC_DEVICE_SIGNED_NAMESPACE)
                ?.entries
                ?.associate { it.key to it.value }
                .orEmpty()
        )
        val expectedTransactionData = verificationContext?.expectedTransactionData.orEmpty()

        addResult("expected_transaction_data_items", expectedTransactionData.size)
        addResult("embedded_transaction_data_items", embeddedTransactionData.size)

        if (verificationContext == null) {
            require(embeddedTransactionData.isEmpty()) {
                "mdoc transaction_data entries must be omitted when verification context is not provided"
            }
            return success()
        }

        if (expectedTransactionData.isEmpty()) {
            require(embeddedTransactionData.isEmpty()) {
                "mdoc transaction_data entries must be omitted when transaction_data is not requested"
            }
            return success()
        }

        val algorithm = resolveHashAlgorithm(decodeList(expectedTransactionData)) ?: DEFAULT_HASH_ALGORITHM
        val expectedHashes = calculateTransactionDataHashes(expectedTransactionData, algorithm)
        val embeddedHashes = calculateTransactionDataHashes(embeddedTransactionData, algorithm)

        addResult("transaction_data_hash_algorithm", algorithm)
        addResult("embedded_transaction_data_hashes", embeddedHashes)

        require(embeddedHashes == expectedHashes) {
            "mdoc transaction_data does not match the requested transaction_data"
        }

        return success()
    }

    private fun extract(deviceSignedItems: Map<String, Any>): List<String> {
        if (deviceSignedItems.isEmpty()) return emptyList()

        val indexedItems = deviceSignedItems.map { (key, value) ->
            val index = parseDeviceSignedItemIndex(key)
                ?: throw IllegalArgumentException("Unsupported mdoc transaction_data entry: $key")
            val encodedTransactionData = value as? String
                ?: throw IllegalArgumentException("mdoc transaction_data entries must be strings")

            index to encodedTransactionData
        }.sortedBy { it.first }

        requireContiguousIndices(indexedItems.map { it.first })

        return indexedItems.map { it.second }
    }
}
