package id.walt.walletdemo.compose.logic

data class WalletDemoBootstrapResult(
    val keyId: String,
    val did: String,
    val publicJwk: String,
    val signingProtection: WalletDemoSigningProtection,
    val warning: String? = null,
)
