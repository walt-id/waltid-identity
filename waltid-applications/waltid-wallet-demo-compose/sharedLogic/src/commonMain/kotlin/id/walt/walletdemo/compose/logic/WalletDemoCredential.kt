package id.walt.walletdemo.compose.logic

data class WalletDemoCredential(
    val id: String,
    val format: String,
    val issuer: String,
    val label: String,
    val addedAt: String,
)
