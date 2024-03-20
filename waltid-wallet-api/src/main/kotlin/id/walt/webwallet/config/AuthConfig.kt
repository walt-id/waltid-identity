package id.walt.webwallet.config

data class AuthConfig(
    val encryptionKey: String,
    val signKey: String,
) : WalletConfig