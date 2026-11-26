package id.walt.walletdemo.compose.logic

data class CredentialSummary(
    val id: String,
    val format: String,
    val issuer: String?,
    val subject: String? = null,
    val label: String,
    val addedAt: String? = null,
    val content: CredentialContent,
    val metadataJson: String? = null,
) {
    constructor(
        id: String, format: String, issuer: String?, subject: String? = null, label: String,
        addedAt: String? = null, credentialDataJson: String? = null, metadataJson: String? = null,
    ) : this(id, format, issuer, subject, label, addedAt, CredentialContent.Full(credentialDataJson), metadataJson)

    val credentialDataJson: String? get() = (content as? CredentialContent.Full)?.json
}

sealed interface CredentialContent {
    val json: String?
    data class Full(override val json: String?) : CredentialContent
    data class Inventory(override val json: String) : CredentialContent
}

typealias WalletDemoCredential = CredentialSummary
