@file:Suppress("PackageDirectoryMismatch")

package id.walt.policies2.vp.policies

import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.objects.elements.DeviceSignedItemList
import id.walt.mdoc.objects.mso.MobileSecurityObject
import id.walt.verifier.openid.transactiondata.DEFAULT_HASH_ALGORITHM
import id.walt.verifier.openid.transactiondata.DecodedTransactionData
import id.walt.verifier.openid.transactiondata.calculateTransactionDataHashes
import id.walt.verifier.openid.transactiondata.decodeList
import id.walt.verifier.openid.transactiondata.normalizeHashAlgorithm
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
        val algorithm = resolveHashAlgorithm(decoded) ?: DEFAULT_HASH_ALGORITHM
        val embeddedBindings = extractHashBindingsFromProfileNamespaces(decoded, deviceNameSpaces)
        val embeddedHashes = embeddedBindings.map { it.hash }
        val expectedHashes = calculateTransactionDataHashes(decoded.map { it.encoded }, algorithm)

        addResult("embedded_transaction_data_items", embeddedHashes.size)

        addResult("transaction_data_hash_algorithm", algorithm)
        addResult("embedded_transaction_data_hashes", embeddedHashes)

        requireHashAlgorithmBinding(decoded, embeddedBindings, algorithm)

        require(embeddedHashes == expectedHashes) {
            "mdoc transaction_data does not match the requested transaction_data"
        }

        return success()
    }

    private fun hasAnyTransactionData(deviceNameSpaces: Map<String, DeviceSignedItemList>): Boolean =
        deviceNameSpaces.values.any { itemList ->
            itemList.entries.any { it.key == "transaction_data_hash" || it.key == "transaction_data_hash_alg" }
        }

    private fun extractHashBindingsFromProfileNamespaces(
        decoded: List<DecodedTransactionData>,
        deviceNameSpaces: Map<String, DeviceSignedItemList>,
    ): List<MdocTransactionDataHashBinding> {
        val byNamespace = decoded.groupBy { it.transactionData.type }
        require(byNamespace.values.all { it.size == 1 }) {
            "mdoc transaction_data supports one item per response namespace"
        }

        return byNamespace.keys.map { namespace ->
            val entries = requireNotNull(deviceNameSpaces[namespace]?.entries) {
                "mdoc transaction_data namespace missing from DeviceSigned: $namespace"
            }
            val hashValue = requireNotNull(entries.singleOrNull { it.key == "transaction_data_hash" }?.value) {
                "mdoc transaction_data_hash element missing for namespace: $namespace"
            }
            val hash = when (hashValue) {
                is ByteArray -> hashValue.encodeToBase64Url()
                is String -> hashValue
                else -> error("mdoc transaction_data_hash must be a byte string or string, got: ${hashValue::class.simpleName}")
            }
            val algorithm = entries
                .singleOrNull { it.key == "transaction_data_hash_alg" }
                ?.value as? String
            MdocTransactionDataHashBinding(hash, algorithm)
        }
    }

    private fun requireHashAlgorithmBinding(
        decoded: List<DecodedTransactionData>,
        embeddedBindings: List<MdocTransactionDataHashBinding>,
        expectedAlgorithm: String,
    ) {
        if (decoded.none { !it.transactionData.transactionDataHashesAlg.isNullOrEmpty() }) return

        require(embeddedBindings.all { it.algorithm != null }) {
            "mdoc transaction_data_hash_alg is required when transaction_data_hashes_alg is present in the request"
        }
        require(embeddedBindings.all { normalizeHashAlgorithm(it.algorithm!!) == normalizeHashAlgorithm(expectedAlgorithm) }) {
            "mdoc transaction_data_hash_alg does not match the requested transaction_data_hashes_alg"
        }
    }

    private data class MdocTransactionDataHashBinding(
        val hash: String,
        val algorithm: String?,
    )
}
