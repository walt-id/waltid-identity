package id.walt.webwallet.config

data class NotificationConfig(
    val url: String,
    val apiKey: String? = null,
) : WalletConfig