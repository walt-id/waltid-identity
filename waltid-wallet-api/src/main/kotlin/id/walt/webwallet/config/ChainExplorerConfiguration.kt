package id.walt.webwallet.config

import kotlinx.serialization.Serializable

@Serializable
data class ChainExplorerConfiguration(
    val explorers: List<Explorer>
) : WalletConfig() {
    @Serializable
    data class Explorer(
        val chain: String,
        val url: String,
    )
}
