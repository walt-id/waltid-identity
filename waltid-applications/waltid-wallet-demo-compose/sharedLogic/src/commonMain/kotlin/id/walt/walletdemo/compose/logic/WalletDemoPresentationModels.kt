package id.walt.walletdemo.compose.logic

data class WalletDemoPresentationPreview(
    val verifierName: String?,
    val clientId: String?,
    val responseUri: String? = null,
    val state: String? = null,
    val nonce: String? = null,
    val credentialOptions: List<WalletDemoPresentationCredentialOption>,
)

data class WalletDemoPresentationCredentialOption(
    val queryId: String,
    val credentialId: String,
    val label: String,
    val issuer: String,
    val subject: String? = null,
    val format: String,
    val credentialDataJson: String? = null,
    val disclosures: List<WalletDemoPresentationDisclosure>,
)

data class WalletDemoPresentationDisclosure(
    val label: String,
    val path: String = "",
    val valueJson: String,
    val displayValue: String? = null,
    val selectivelyDisclosable: Boolean,
)
