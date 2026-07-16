package id.walt.walletdemo.compose.logic

data class WalletDemoPresentationPreview(
    val verifierName: String?,
    val clientId: String?,
    val responseUri: String? = null,
    val state: String? = null,
    val nonce: String? = null,
    val transactionData: List<ClaimGroup> = emptyList(),
    val credentialOptions: List<WalletDemoPresentationCredentialOption>,
    val credentialRequirements: List<WalletDemoPresentationCredentialRequirement> = emptyList(),
)

data class VerifierDetails(
    val name: String?,
    val clientId: String?,
    val responseUri: String? = null,
    val state: String? = null,
    val nonce: String? = null,
    val transactionData: List<ClaimGroup> = emptyList(),
    val trustStatus: String = CredentialDisplayText.Unknown,
)

data class WalletDemoPresentationCredentialRequirement(
    val options: List<List<String>>,
)

data class WalletDemoPresentationCredentialOption(
    val queryId: String,
    val credentialId: String,
    val multiple: Boolean = false,
    val label: String,
    val issuer: String,
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
): Boolean {
    val optionBySelection = credentialOptions.associateBy { it.selection }
    val selectedOptions = selectedCredentialOptions
        .mapNotNull { selection -> optionBySelection[selection] }
    if (selectedOptions.isEmpty()) return false
    val selectedCountsByQuery = selectedOptions.groupingBy { it.queryId }.eachCount()
    if (selectedOptions.any { option -> selectedCountsByQuery.getValue(option.queryId) > 1 && !option.multiple }) return false

    val selectedQueryIds = selectedOptions
        .map { it.queryId }
        .toSet()

    if (credentialRequirements.isEmpty()) return true
    return credentialRequirements.all { requirement ->
        requirement.options.any { option ->
            option.isNotEmpty() && option.all { queryId -> queryId in selectedQueryIds }
        }
    }
}

fun Set<WalletDemoPresentationDisclosureSelection>.forSelectedCredentials(
    selectedCredentialOptions: Set<WalletDemoPresentationCredentialSelection>,
): Set<WalletDemoPresentationDisclosureSelection> {
    val selectedOptionKeys = selectedCredentialOptions
        .map { it.queryId to it.credentialId }
        .toSet()
    return filter { disclosure -> disclosure.queryId to disclosure.credentialId in selectedOptionKeys }
        .toSet()
}

fun WalletDemoPresentationPreview.defaultCredentialSelection(): Set<WalletDemoPresentationCredentialSelection> {
    val firstSelectionByQuery = credentialOptions
        .groupBy { it.queryId }
        .mapValues { (_, options) -> options.first().selection }
    if (firstSelectionByQuery.isEmpty()) return emptySet()
    if (credentialRequirements.isEmpty()) return setOf(firstSelectionByQuery.values.first())

    val selectedQueryIds = linkedSetOf<String>()
    credentialRequirements.forEach { requirement ->
        val queryIds = requirement.options.firstOrNull { option ->
            option.isNotEmpty() && option.all { queryId -> queryId in firstSelectionByQuery }
        }
            ?: requirement.options.firstOrNull()
                ?.filter { queryId -> queryId in firstSelectionByQuery }
        queryIds?.let { selectedQueryIds += it }
    }
    return selectedQueryIds
        .mapNotNull { queryId -> firstSelectionByQuery[queryId] }
        .toSet()
}

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
