package id.walt.verifier.openid.transactiondata

import id.walt.crypto.utils.ShaUtils.calculateSha256Base64Url

fun resolveHashAlgorithm(decodedItems: List<DecodedTransactionData>): String? {
    if (decodedItems.isEmpty()) return null

    decodedItems
        .flatMap { it.transactionData.transactionDataHashesAlg.orEmpty() }
        .takeIf { it.isNotEmpty() }
        ?.let(::requireSupportedHashAlgorithms)

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

internal fun requireSupportedHashAlgorithms(algorithms: List<String>) {
    require(algorithms.isNotEmpty()) { "transaction_data_hashes_alg must not be empty" }
    require(algorithms.any { normalizeHashAlgorithm(it) == DEFAULT_HASH_ALGORITHM }) {
        "Unsupported transaction_data_hashes_alg values: $algorithms"
    }
}

internal fun normalizeHashAlgorithm(algorithm: String): String = algorithm.lowercase()
