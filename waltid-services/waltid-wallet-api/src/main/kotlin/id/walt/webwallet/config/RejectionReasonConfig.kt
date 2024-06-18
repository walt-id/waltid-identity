package id.walt.webwallet.config

import kotlinx.serialization.Serializable

@Serializable
data class RejectionReasonConfig(
    val reasons: List<String> = listOf(
        "Unknown sender",
        "Not relevant to me",
        "Unsure about accuracy",
        "Need more details",
    ),
) : WalletConfig()
