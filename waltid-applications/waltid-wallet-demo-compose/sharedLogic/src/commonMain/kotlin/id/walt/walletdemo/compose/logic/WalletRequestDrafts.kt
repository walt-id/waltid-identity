package id.walt.walletdemo.compose.logic

data class WalletRequestDrafts(
    val offerUrl: String = "",
    val txCode: String = "",
    val txCodeRequirement: WalletDemoTxCode? = null,
    val presentationRequestUrl: String = "",
)
