package id.walt.webwallet.config

data class OciKeyConfig(
    val compartmentId: String,
    val vaultId: String,

    ) : WalletConfig
