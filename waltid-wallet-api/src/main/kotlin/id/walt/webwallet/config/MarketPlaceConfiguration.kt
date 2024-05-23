package id.walt.webwallet.config

import kotlinx.serialization.Serializable

@Serializable
data class MarketPlaceConfiguration(
    val marketplaces: List<MarketPlace>,
) : WalletConfig {
    @Serializable
    data class MarketPlace(
        val chain: String,
        val name: String,
        val url: String,
    )
}
