package id.walt.walletdemo.compose.logic

data class WalletDemoOfferPreview(
    val issuer: WalletDemoIssuerMetadata,
    val offeredCredentials: List<WalletDemoOfferedCredentialMetadata>,
    val transactionCode: WalletDemoTransactionCodeRequirement?,
    val requiresIssuerAuthentication: Boolean = false,
)
