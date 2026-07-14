package id.walt.walletdemo.compose.logic

data class CredentialSummary(
    val id: String,
    val format: String,
    val issuer: String?,
    val subject: String? = null,
    val label: String,
    val addedAt: String? = null,
    val credentialDataJson: String? = null,
)

typealias WalletDemoCredential = CredentialSummary

/*data class WalletDemoCredentialDetails(
    val id: String,
    val credentialDataJson: String,
)*/
