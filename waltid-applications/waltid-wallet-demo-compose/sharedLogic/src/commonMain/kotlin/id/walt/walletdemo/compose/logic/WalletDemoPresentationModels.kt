package id.walt.walletdemo.compose.logic

sealed interface WalletDemoPresentationPreviewResult {
    data class Ready(val preview: WalletDemoPresentationPreview) : WalletDemoPresentationPreviewResult
    data class Invalid(val error: WalletDemoPresentationError) : WalletDemoPresentationPreviewResult
}

data class WalletDemoPresentationError(
    val previewHandle: WalletDemoPresentationPreviewHandle,
    override val verifierMetadata: WalletDemoVerifierMetadata?,
    override val clientId: String?,
    override val responseUri: String? = null,
    override val state: String? = null,
    override val nonce: String? = null,
    override val responseEncryption: WalletDemoResponseEncryption,
    override val transactionData: List<ClaimGroup> = emptyList(),
    val errorCode: String,
    val message: String,
) : WalletDemoPresentationRequestInfo

interface WalletDemoPresentationRequestInfo {
    val verifierMetadata: WalletDemoVerifierMetadata?
    val clientId: String?
    val responseUri: String?
    val state: String?
    val nonce: String?
    val responseEncryption: WalletDemoResponseEncryption
    val transactionData: List<ClaimGroup>
}

data class WalletDemoPresentationPreview(
    val previewHandle: WalletDemoPresentationPreviewHandle,
    override val verifierMetadata: WalletDemoVerifierMetadata?,
    override val clientId: String?,
    override val responseUri: String? = null,
    override val state: String? = null,
    override val nonce: String? = null,
    override val responseEncryption: WalletDemoResponseEncryption,
    override val transactionData: List<ClaimGroup> = emptyList(),
    val credentialOptions: List<WalletDemoPresentationCredentialOption>,
    val credentialRequirements: List<WalletDemoPresentationCredentialRequirement> = emptyList(),
) : WalletDemoPresentationRequestInfo

sealed interface WalletDemoResponseEncryption {
    data object NotRequired : WalletDemoResponseEncryption

    data class Required(
        val keyManagementAlgorithm: String,
        val contentEncryptionAlgorithm: String,
        val verifierKeyId: String?,
        val verifierKeyThumbprint: String,
    ) : WalletDemoResponseEncryption
}

data class WalletDemoPresentationPreviewHandle(val value: String)

data class WalletDemoPresentationCredentialRequirement(
    val options: List<List<String>>,
)

data class WalletDemoPresentationCredentialOption(
    val queryId: String,
    val credentialId: String,
    val multiple: Boolean = false,
    val label: String,
    val issuer: String?,
    val subject: String? = null,
    val format: String,
    val credentialDataJson: String,
    val disclosures: List<WalletDemoPresentationDisclosure>,
) {
    val selection: WalletDemoPresentationCredentialSelection
        get() = WalletDemoPresentationCredentialSelection(queryId = queryId, credentialId = credentialId)

}

data class WalletDemoPresentationCredentialSelection(
    val queryId: String,
    val credentialId: String,
) {
    val id: String
        get() = "${queryId.length}:$queryId${credentialId.length}:$credentialId"
}

data class WalletDemoPresentationDisclosureSelection(
    val queryId: String,
    val credentialId: String,
    val path: String,
) {
    val id: String
        get() = "${queryId.length}:$queryId${credentialId.length}:$credentialId${path.length}:$path"
}

fun WalletDemoPresentationPreview.hasCompleteCredentialSelection(
    selectedCredentialOptions: Set<WalletDemoPresentationCredentialSelection>,
): Boolean = hasCompleteCredentialSelection(credentialOptions, credentialRequirements, selectedCredentialOptions)

fun Set<WalletDemoPresentationDisclosureSelection>.forSelectedCredentials(
    selectedCredentialOptions: Set<WalletDemoPresentationCredentialSelection>,
): Set<WalletDemoPresentationDisclosureSelection> {
    val selectedOptionKeys = selectedCredentialOptions
        .map { it.queryId to it.credentialId }
        .toSet()
    return filter { disclosure -> disclosure.queryId to disclosure.credentialId in selectedOptionKeys }
        .toSet()
}

fun WalletDemoPresentationPreview.defaultCredentialSelection(): Set<WalletDemoPresentationCredentialSelection> =
    defaultCredentialSelection(credentialOptions, credentialRequirements)

data class WalletDemoPresentationDisclosure(
    val label: String,
    val path: String = "",
    val valueJson: String,
    val displayValue: String? = null,
    val selectivelyDisclosable: Boolean,
    val required: Boolean = !selectivelyDisclosable,
    val selectable: Boolean = selectivelyDisclosable && !required,
)

data class WalletDemoTransactionDataItem(
    val type: String,
    val displayName: String,
    val credentialQueryIds: List<String>,
    val supportedFields: List<String>,
    val rawJson: String,
    val detailsJson: String,
)
