package id.walt.verifier.openid

import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.crypto.utils.ShaUtils.calculateSha256Base64Url
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.verifier.openid.models.authorization.TransactionDataItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

object TransactionDataUtils {

    const val DEFAULT_HASH_ALGORITHM = "sha-256"
    const val MDOC_DEVICE_SIGNED_NAMESPACE = "org.waltid.openid4vp.transaction_data"
    const val DEMO_TRANSACTION_DATA_TYPE = "org.waltid.transaction-data.payment-authorization"

    val SUPPORTED_TRANSACTION_DATA_TYPES = setOf(DEMO_TRANSACTION_DATA_TYPE)

    private const val MDOC_DEVICE_SIGNED_ITEM_PREFIX = "transaction_data_"

    private val knownParameterNames = setOf(
        "type",
        "credential_ids",
        "transaction_data_hashes_alg",
        "require_cryptographic_holder_binding",
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false
    }

    @Serializable
    data class DecodedTransactionData(
        val encoded: String,
        val rawJson: JsonObject,
        val transactionData: TransactionDataItem,
        val details: JsonObject,
    )

    enum class TransactionDataValidationErrorReason {
        MISSING_HASHES,
        HASHES_MISMATCH,
        HASH_ALGORITHM_MISMATCH,
    }

    class TransactionDataValidationException(
        val reason: TransactionDataValidationErrorReason,
        message: String,
    ) : IllegalArgumentException(message)

    fun decodeTransactionData(encoded: String): DecodedTransactionData {
        val rawJson = json.parseToJsonElement(encoded.decodeFromBase64Url().decodeToString()).jsonObject
        val transactionData = json.decodeFromJsonElement(
            TransactionDataItem.serializer(),
            rawJson,
        )

        return DecodedTransactionData(
            encoded = encoded,
            rawJson = rawJson,
            transactionData = transactionData,
            details = JsonObject(rawJson.filterKeys { it !in knownParameterNames }),
        )
    }

    fun decodeTransactionDataList(transactionData: List<String>?): List<DecodedTransactionData> =
        transactionData?.map(::decodeTransactionData).orEmpty()

    fun validateRequestTransactionData(
        transactionData: List<String>?,
        supportedTypes: Set<String>? = null,
        credentialQueriesById: Map<String, CredentialQuery>? = null,
    ): List<DecodedTransactionData> {
        val decodedItems = decodeTransactionDataList(transactionData)

        decodedItems.forEach { decodedItem ->
            val item = decodedItem.transactionData
            require(item.type.isNotBlank()) { "transaction_data.type must not be blank" }
            if (supportedTypes != null) {
                require(item.type in supportedTypes) { "Unsupported transaction_data type: ${item.type}" }
            }
            require(item.credentialIds.isNotEmpty()) { "transaction_data.credential_ids must not be empty" }
            require(item.requireCryptographicHolderBinding == true) {
                "transaction_data type ${item.type} requires cryptographic holder binding"
            }

            if (credentialQueriesById != null) {
                require(item.credentialIds.all { it in credentialQueriesById }) {
                    "transaction_data.credential_ids must refer to credential ids from the authorization request"
                }
                item.credentialIds.forEach { credentialId ->
                    val credentialQuery = credentialQueriesById.getValue(credentialId)
                    require(isTransactionDataSupportedFormat(credentialQuery.format)) {
                        "transaction_data.credential_ids must reference credential queries with a supported transaction_data profile"
                    }
                    require(credentialQuery.requireCryptographicHolderBinding) {
                        "transaction_data.credential_ids must require cryptographic holder binding"
                    }
                }
            }

            item.transactionDataHashesAlg?.let(::requireSupportedHashAlgorithms)
        }

        return decodedItems
    }

    fun resolveHashAlgorithm(decodedItems: List<DecodedTransactionData>): String? {
        if (decodedItems.isEmpty()) {
            return null
        }

        decodedItems.forEach { item ->
            item.transactionData.transactionDataHashesAlg?.let(::requireSupportedHashAlgorithms)
        }

        return DEFAULT_HASH_ALGORITHM
    }

    fun calculateTransactionDataHashes(
        transactionData: List<String>,
        algorithm: String = DEFAULT_HASH_ALGORITHM,
    ): List<String> {
        require(normalizeHashAlgorithm(algorithm) == DEFAULT_HASH_ALGORITHM) {
            "Unsupported transaction_data hash algorithm: $algorithm"
        }

        return transactionData.map(::calculateSha256Base64Url)
    }

    fun filterTransactionDataForCredentialId(
        transactionData: List<String>?,
        credentialId: String,
    ): List<String> = decodeTransactionDataList(transactionData)
        .filter { credentialId in it.transactionData.credentialIds }
        .map { it.encoded }

    fun buildMdocEmbeddedTransactionData(transactionData: List<String>): Map<String, String> = transactionData
        .mapIndexed { index, encodedTransactionData ->
            mdocDeviceSignedItemKey(index) to encodedTransactionData
        }
        .toMap()

    fun extractMdocEmbeddedTransactionData(deviceSignedItems: Map<String, Any>): List<String> {
        if (deviceSignedItems.isEmpty()) {
            return emptyList()
        }

        val indexedItems = deviceSignedItems.map { (key, value) ->
            val index = parseMdocDeviceSignedItemIndex(key)
                ?: throw IllegalArgumentException("Unsupported mdoc transaction_data entry: $key")
            val encodedTransactionData = value as? String
                ?: throw IllegalArgumentException("mdoc transaction_data entries must be strings")

            index to encodedTransactionData
        }.sortedBy { it.first }

        indexedItems.forEachIndexed { expectedIndex, (actualIndex, _) ->
            require(expectedIndex == actualIndex) {
                "mdoc transaction_data entries must use contiguous indices starting at 0"
            }
        }

        return indexedItems.map { it.second }
    }

    fun mdocDeviceSignedItemKeys(transactionDataItemsCount: Int): Set<String> =
        (0 until transactionDataItemsCount)
            .map(::mdocDeviceSignedItemKey)
            .toSet()

    fun validateResponseTransactionData(
        expectedTransactionData: List<String>?,
        transactionDataHashes: List<String>?,
        transactionDataHashesAlg: String?,
    ) {
        val expectedItems = expectedTransactionData.orEmpty()
        if (expectedItems.isEmpty()) {
            require(transactionDataHashes.isNullOrEmpty()) {
                "transaction_data_hashes must be omitted when transaction_data is not requested"
            }
            require(transactionDataHashesAlg == null) {
                "transaction_data_hashes_alg must be omitted when transaction_data is not requested"
            }
            return
        }

        val decodedExpectedItems = decodeTransactionDataList(expectedTransactionData)
        val expectedAlgorithm = normalizeHashAlgorithm(transactionDataHashesAlg ?: DEFAULT_HASH_ALGORITHM)
        requireValidation(
            expectedAlgorithm == DEFAULT_HASH_ALGORITHM,
            TransactionDataValidationErrorReason.HASH_ALGORITHM_MISMATCH,
        ) {
            "Unsupported transaction_data hash algorithm: $transactionDataHashesAlg"
        }
        decodedExpectedItems.forEach { decodedItem ->
            decodedItem.transactionData.transactionDataHashesAlg?.let { requestedAlgorithms ->
                requireSupportedHashAlgorithms(requestedAlgorithms)
                requireValidation(
                    requestedAlgorithms.any { normalizeHashAlgorithm(it) == expectedAlgorithm },
                    TransactionDataValidationErrorReason.HASH_ALGORITHM_MISMATCH,
                ) {
                    "transaction_data_hashes_alg must match one of the requested transaction_data_hashes_alg values"
                }
            }
        }

        requireValidation(
            !transactionDataHashes.isNullOrEmpty(),
            TransactionDataValidationErrorReason.MISSING_HASHES,
        ) {
            "transaction_data_hashes must be present when transaction_data is requested"
        }
        val actualTransactionDataHashes = requireNotNull(transactionDataHashes)
        requireValidation(
            actualTransactionDataHashes.size == expectedItems.size,
            TransactionDataValidationErrorReason.HASHES_MISMATCH,
        ) {
            "transaction_data_hashes must contain one entry per transaction_data item"
        }

        val expectedHashes = calculateTransactionDataHashes(expectedItems, expectedAlgorithm)
        requireValidation(
            actualTransactionDataHashes == expectedHashes,
            TransactionDataValidationErrorReason.HASHES_MISMATCH,
        ) {
            "transaction_data_hashes do not match the requested transaction_data"
        }

        val requestedAlgorithmsWereExplicit = decodedExpectedItems
            .any { !it.transactionData.transactionDataHashesAlg.isNullOrEmpty() }

        if (requestedAlgorithmsWereExplicit) {
            requireValidation(
                transactionDataHashesAlg != null,
                TransactionDataValidationErrorReason.HASH_ALGORITHM_MISMATCH,
            ) {
                "transaction_data_hashes_alg is required when transaction_data_hashes_alg is present in the request"
            }
        }
    }

    private fun normalizeHashAlgorithm(algorithm: String): String = algorithm.lowercase()

    private fun isTransactionDataSupportedFormat(format: CredentialFormat): Boolean = format in setOf(
        CredentialFormat.DC_SD_JWT,
        CredentialFormat.MSO_MDOC,
    )

    private fun mdocDeviceSignedItemKey(index: Int): String = "$MDOC_DEVICE_SIGNED_ITEM_PREFIX$index"

    private fun parseMdocDeviceSignedItemIndex(key: String): Int? =
        key.removePrefix(MDOC_DEVICE_SIGNED_ITEM_PREFIX)
            .takeIf { it != key }
            ?.toIntOrNull()

    private fun requireSupportedHashAlgorithms(algorithms: List<String>) {
        require(algorithms.isNotEmpty()) { "transaction_data_hashes_alg must not be empty" }
        require(algorithms.any { normalizeHashAlgorithm(it) == DEFAULT_HASH_ALGORITHM }) {
            "Unsupported transaction_data_hashes_alg values: $algorithms"
        }
    }

    private inline fun requireValidation(
        condition: Boolean,
        reason: TransactionDataValidationErrorReason,
        lazyMessage: () -> String,
    ) {
        if (!condition) {
            throw TransactionDataValidationException(reason, lazyMessage())
        }
    }
}
