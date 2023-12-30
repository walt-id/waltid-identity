package id.walt.webwallet.config

data class ChainExplorerConfiguration(
    val explorers: List<Explorer>
) : WalletConfig {
    data class Explorer(
        val chain: String,
        val url: String,
    )
}
