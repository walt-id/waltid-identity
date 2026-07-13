package id.walt.walletdemo.compose.logic

data class WalletRequestDrafts(
    val offerUrl: String = "",
    val txCode: String = "",
    val txCodeRequired: Boolean = false,
    val offerFromDeepLink: Boolean = false,
    val presentationRequestUrl: String = "",
)
