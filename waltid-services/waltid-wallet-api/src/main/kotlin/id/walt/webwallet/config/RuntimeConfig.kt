package id.walt.webwallet.config

import kotlinx.serialization.Serializable

@Serializable
data class RuntimeConfig(
    val mock: Boolean = false,
    val enableDidWebResolverHttps: Boolean = true,
) : WalletConfig()
