package id.walt.webwallet.config

import kotlinx.serialization.Serializable

@Serializable
data class RejectionReasonConfig(
    val reasons: List<String>
) : WalletConfig()