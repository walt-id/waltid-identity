package id.walt.walletdemo.compose.logic

data class WalletDemoOfferPreview(
    val issuer: WalletDemoIssuerMetadata,
    val offeredCredentials: List<WalletDemoOfferedCredentialMetadata>,
    val transactionCode: WalletDemoTransactionCodeRequirement?,
    val requiresIssuerAuthentication: Boolean = false,
    val batchSize: Int? = null,
    val holderKeyBudget: Int? = null,
)

fun initialIssuanceCopyCounts(preview: WalletDemoOfferPreview): Map<String, Int> {
    val perType = (preview.batchSize ?: 1).coerceAtLeast(1)
    val budget = preview.holderKeyBudget
    val single = if (budget == null || budget >= 1) minOf(1, perType) else 0
    return preview.offeredCredentials.associate { it.configurationId to single }
}

fun issuanceCopyLimit(
    preview: WalletDemoOfferPreview,
    counts: Map<String, Int>,
    configurationId: String,
): Int {
    val perType = (preview.batchSize ?: 1).coerceAtLeast(1)
    val budget = preview.holderKeyBudget ?: return perType
    val reservedByOthers = preview.offeredCredentials.sumOf { credential ->
        if (credential.configurationId == configurationId) 0
        else distinctHolderKeysRequired(counts[credential.configurationId] ?: 1)
    }
    val remaining = (budget - reservedByOthers).coerceAtLeast(0)
    val sharedSingle = if (budget >= 1) 1 else 0
    return minOf(perType, maxOf(sharedSingle, remaining))
}

fun issuanceSelectionFitsHolderBudget(preview: WalletDemoOfferPreview, counts: Map<String, Int>): Boolean {
    val budget = preview.holderKeyBudget ?: return true
    val reserved = preview.offeredCredentials.sumOf { distinctHolderKeysRequired(counts[it.configurationId] ?: 1) }
    val needsCurrentHolder = preview.offeredCredentials.any { (counts[it.configurationId] ?: 1) == 1 }
    val required = if (needsCurrentHolder) maxOf(reserved, 1) else reserved
    return required <= budget
}

/** One copy reuses the current holder. Further copies each need their own stored key. */
private fun distinctHolderKeysRequired(count: Int): Int = if (count > 1) count else 0
