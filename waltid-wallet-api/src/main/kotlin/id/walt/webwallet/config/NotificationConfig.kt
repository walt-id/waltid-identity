package id.walt.webwallet.config

import kotlinx.serialization.Serializable

@Serializable
data class NotificationConfig(
    val url: String,
    val apiKey: String? = null,
) : WalletConfig
