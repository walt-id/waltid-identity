package id.walt.walletdemo.compose.logic

data class WalletDemoOfferPreview(
    val credentialIssuer: String,
    val offeredCredentials: List<String>,
    val transactionCodeRequired: Boolean,
)
