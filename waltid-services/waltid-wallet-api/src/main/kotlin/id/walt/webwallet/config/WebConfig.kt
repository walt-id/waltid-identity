package id.walt.webwallet.config

import kotlinx.serialization.Serializable

@Serializable
data class WebConfig(
    val webHost: String,
    val webPort: String,
) : WalletConfig()