package id.walt.webwallet.config

import kotlinx.serialization.Serializable

@Serializable
data class TrustedCAConfig(
    val certificates: List<String> = emptyList(),
) : WalletConfig()
