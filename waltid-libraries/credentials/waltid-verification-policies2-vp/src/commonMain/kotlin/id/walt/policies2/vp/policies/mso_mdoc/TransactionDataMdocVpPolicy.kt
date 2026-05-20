@file:Suppress("PackageDirectoryMismatch")

package id.walt.policies2.vp.policies

import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.objects.elements.DeviceSignedItem
import id.walt.mdoc.objects.elements.DeviceSignedItemList
import id.walt.mdoc.objects.mso.MobileSecurityObject
import id.walt.verifier.openid.transactiondata.DEFAULT_HASH_ALGORITHM
import id.walt.verifier.openid.transactiondata.DecodedTransactionData
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
        val expectedTransactionData = verificationContext?.expectedTransactionData.orEmpty()
        val deviceNameSpaces = document.deviceSigned?.namespaces?.value?.entries.orEmpty()

        addResult("expected_transaction_data_items", expectedTransactionData.size)

        if (verificationContext == null || expectedTransactionData.isEmpty()) {
            val anyEmbedded = hasAnyTransactionData(deviceNameSpaces)
            require(!anyEmbedded) {
                "mdoc transaction_data entries must be omitted when transaction_data is not requested"
            }
            addResult("embedded_transaction_data_items", 0)
            return success()
        }

        val decoded = decodeList(expectedTransactionData)
        val embeddedTransactionData = extractFromTypeNamespaces(decoded, deviceNameSpaces)

        addResult("embedded_transaction_data_items", embeddedTransactionData.size)

        val algorithm = resolveHashAlgorithm(decoded) ?: DEFAULT_HASH_ALGORITHM
        val expectedInTypeOrder = decoded.groupBy { it.transactionData.type }.flatMap { (_, items) -> items.map { it.encoded } }
        val expectedHashes = calculateTransactionDataHashes(expectedInTypeOrder, algorithm)
        val embeddedHashes = calculateTransactionDataHashes(embeddedTransactionData, algorithm)

        addResult("transaction_data_hash_algorithm", algorithm)
        addResult("embedded_transaction_data_hashes", embeddedHashes)

        require(embeddedHashes == expectedHashes) {
            "mdoc transaction_data does not match the requested transaction_data"
        }

        return success()
    }

    private fun hasAnyTransactionData(deviceNameSpaces: Map<String, DeviceSignedItemList>): Boolean =
        deviceNameSpaces.values.any { itemList -> itemList.entries.any { parseDeviceSignedItemIndex(it.key) != null } }

    private fun extractFromTypeNamespaces(
        decoded: List<DecodedTransactionData>,
        deviceNameSpaces: Map<String, DeviceSignedItemList>,
    ): List<String> = decoded
        .groupBy { it.transactionData.type }
        .flatMap { (type, _) -> extractItems(deviceNameSpaces[type]?.entries.orEmpty()) }

    private fun extractItems(items: List<DeviceSignedItem>): List<String> {
        if (items.isEmpty()) return emptyList()

        val indexedItems = items.map { item ->
            val index = parseDeviceSignedItemIndex(item.key)
                ?: throw IllegalArgumentException("Unsupported mdoc transaction_data entry: ${item.key}")
            val encodedTransactionData = item.value as? String
                ?: throw IllegalArgumentException("mdoc transaction_data entries must be strings")
            index to encodedTransactionData
        }.sortedBy { it.first }

        requireContiguousIndices(indexedItems.map { it.first })

        return indexedItems.map { it.second }
    }
}
