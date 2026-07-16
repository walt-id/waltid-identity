package id.walt.walletdemo.compose.logic

data class WalletDemoUiState(
    val auth: WalletAuthState = WalletAuthState.Setup(),
    val session: WalletSessionState = WalletSessionState.NotBootstrapped,
    val operation: WalletOperationState = WalletOperationState.Idle,
    val selectedTab: WalletDemoTab = WalletDemoTab.Credentials,
    val requestDrafts: WalletRequestDrafts = WalletRequestDrafts(),
    val offerPreview: WalletDemoOfferPreview? = null,
    val lastReceivedCredentialIds: List<String> = emptyList(),
    val lastIssuerMetadataJson: String? = null,
    val receiveCompleted: Boolean = false,
    val receiveNavigationResetKey: Int = 0,
    val presentationPreview: WalletDemoPresentationPreview? = null,
    val selectedPresentationCredentialOptions: Set<WalletDemoPresentationCredentialSelection> = emptySet(),
    val selectedPresentationDisclosureOptions: Set<WalletDemoPresentationDisclosureSelection> = emptySet(),
    val presentationCompleted: Boolean = false,
    val presentationNavigationResetKey: Int = 0,
    val warning: String? = null,
)

fun WalletDemoUiState.receivedCredentials(): List<WalletDemoCredential> {
    val ready = session as? WalletSessionState.Ready ?: return emptyList()
    val credentialsById = ready.credentials.associateBy { it.id }
    return lastReceivedCredentialIds.mapNotNull { id -> credentialsById[id] }
}

fun WalletDemoUiState.presentationCredentialSelectionComplete(): Boolean =
    presentationPreview?.hasCompleteCredentialSelection(selectedPresentationCredentialOptions) == true

internal fun resolvedReceivedCredentialIds(
    returnedCredentialIds: List<String>,
    previousCredentials: List<WalletDemoCredential>,
    refreshedCredentials: List<WalletDemoCredential>,
): List<String> {
    val refreshedIds = refreshedCredentials.map { it.id }.toSet()
    val returnedResolvedIds = returnedCredentialIds.filter { id -> id in refreshedIds }
    if (returnedResolvedIds.isNotEmpty()) {
        return returnedResolvedIds
    }

    val previousIds = previousCredentials.map { it.id }.toSet()
    val newCredentialIds = refreshedCredentials
        .map { it.id }
        .filterNot { id -> id in previousIds }
    return newCredentialIds.ifEmpty { returnedCredentialIds }
}
