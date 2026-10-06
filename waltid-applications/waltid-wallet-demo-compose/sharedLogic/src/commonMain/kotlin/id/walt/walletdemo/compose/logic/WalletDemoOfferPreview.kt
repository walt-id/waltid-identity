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
    val budget = preview.holderKeyBudget ?: return preview.offeredCredentials.associate { it.configurationId to 1 }
    var remaining = budget.coerceAtLeast(0)
    val perType = (preview.batchSize ?: 1).coerceAtLeast(1)
    return preview.offeredCredentials.associate { credential ->
        val count = minOf(1, perType, remaining)
        remaining -= count
        credential.configurationId to count
    }
}

fun issuanceCopyLimit(
    preview: WalletDemoOfferPreview,
    counts: Map<String, Int>,
    configurationId: String,
): Int {
    val perType = (preview.batchSize ?: 1).coerceAtLeast(1)
    val budget = preview.holderKeyBudget ?: return perType
    val others = preview.offeredCredentials.sumOf { credential ->
        if (credential.configurationId == configurationId) 0
        else counts[credential.configurationId] ?: 1
    }
    return minOf(perType, (budget - others).coerceAtLeast(0))
}

fun issuanceSelectionFitsHolderBudget(preview: WalletDemoOfferPreview, counts: Map<String, Int>): Boolean {
    val budget = preview.holderKeyBudget ?: return true
    val selected = preview.offeredCredentials.sumOf { counts[it.configurationId] ?: 1 }
    return selected <= budget
}
