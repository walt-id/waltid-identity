package id.walt.walletdemo.compose.logic

data class WalletDemoOfferPreview(
    val previewHandle: WalletDemoIssuancePreviewHandle,
    val issuer: WalletDemoIssuerMetadata,
    val offeredCredentials: List<WalletDemoOfferedCredentialMetadata>,
    val transactionCode: WalletDemoTransactionCodeRequirement?,
)

data class WalletDemoIssuancePreviewHandle(val value: String)
