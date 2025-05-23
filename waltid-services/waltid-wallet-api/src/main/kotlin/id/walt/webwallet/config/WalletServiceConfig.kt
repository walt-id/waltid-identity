package id.walt.webwallet.config

import kotlinx.serialization.Serializable

@Serializable
data class WalletServiceConfig (
    val baseUrl: String
) : WalletConfig()
