package id.walt.webwallet.config

data class LoginMethodsConfig(
    val enabledLoginMethods: List<String>
) : WalletConfig
