package id.walt.webwallet.config

import kotlinx.serialization.Serializable

@Serializable
data class WebConfig(
    val webHost: String = "0.0.0.0",
    val webPort: Int = 4545,
    val publicBaseUrl: String = "http://localhost:3000",
) : WalletConfig

