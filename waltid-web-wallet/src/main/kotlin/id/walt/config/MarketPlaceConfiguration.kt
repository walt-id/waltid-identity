package id.walt.config

data class MarketPlaceConfiguration(
    val marketplaces: List<MarketPlace>,
) : WalletConfig {
    data class MarketPlace(
        val chain: String,
        val name: String,
        val url: String,
    )
}
