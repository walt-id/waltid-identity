package id.walt.walletdemo.compose.logic

data class CredentialSummary(
    val id: String,
    val format: String,
    val issuer: String?,
    val subject: String? = null,
    val label: String,
    val addedAt: String? = null,
    val credentialDataJson: String? = null,
    val metadataJson: String? = null,
    /** Wallet-local store identifier used for deletion. Differs from [id] for presentation options. */
    val credentialId: String = id,
)

typealias WalletDemoCredential = CredentialSummary
