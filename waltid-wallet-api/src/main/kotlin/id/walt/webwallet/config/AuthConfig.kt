package id.walt.webwallet.config

data class AuthConfig(
    val encryptionKey: String,
    val signKey: String,
    val tokenKey: String,
    val issTokenClaim: String,
    val audTokenClaim: String? = null,
    val tokenLifetime: String,
) : WalletConfig
