package id.walt.walletdemo.compose.logic

data class WalletDemoBootstrapResult(
    val keyId: String,
    val did: String,
    val publicJwk: String,
    val warning: String? = null,
)
