package id.walt.walletdemo.compose.logic

data class DemoWalletConfig(
    val walletId: String = "default",
    val attestationBaseUrl: String = "",
    val attestationAttesterPath: String = "",
    val attestationBearerToken: String = "",
    val attestationHostHeader: String = "",
)
