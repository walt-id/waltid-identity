package id.walt.walletdemo.compose.logic

data class WalletRequestDrafts(
    val offerUrl: String = "",
    val txCode: String = "",
    val transactionCodeRequired: Boolean = false,
    val presentationRequestUrl: String = "",
)
