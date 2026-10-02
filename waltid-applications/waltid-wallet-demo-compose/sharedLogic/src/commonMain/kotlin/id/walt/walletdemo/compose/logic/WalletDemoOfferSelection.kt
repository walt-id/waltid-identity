package id.walt.walletdemo.compose.logic

/** Shared by in-app receiving and the platform create provider; does not create keys or issue. */
fun WalletDemoOfferPreview.credentialSelections(
    copies: Map<String, Int>,
    defaultHolder: WalletDemoHolderBinding,
): List<WalletDemoCredentialSelection> {
    val offeredIds = offeredCredentials.map { it.configurationId }.toSet()
    require(copies.keys.all { it in offeredIds }) { "Selection contains a credential outside this offer" }
    val limit = (batchSize ?: 1).coerceAtLeast(1)
    return offeredCredentials.mapNotNull { credential ->
        val count = copies[credential.configurationId] ?: 1
        require(count in 0..limit) { "Copy count exceeds this offer's supported range" }
        if (count == 0) null else WalletDemoCredentialSelection(
            credentialConfigurationId = credential.configurationId,
            holders = if (count == 1) WalletDemoCredentialHolders.Existing(listOf(defaultHolder))
                else WalletDemoCredentialHolders.NewKeys(count),
        )
    }.also { require(it.isNotEmpty()) { "Select at least one credential" } }
}
