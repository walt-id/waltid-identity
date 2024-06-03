package id.walt.webwallet.config

import kotlinx.serialization.Serializable

@Serializable
data class LoginMethodsConfig(
    val enabledLoginMethods: List<String>
) : WalletConfig()
