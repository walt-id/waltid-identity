package id.walt.webwallet.config

import kotlinx.serialization.Serializable

@Serializable
data class OciKeyConfig(
    val compartmentId: String,
    val vaultId: String,
) : WalletConfig()
