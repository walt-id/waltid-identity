package id.walt.walletdemo.compose.logic

data class WalletDemoUiState(
    val auth: WalletAuthState = WalletAuthState.Setup(),
    val session: WalletSessionState = WalletSessionState.NotBootstrapped,
    val operation: WalletOperationState = WalletOperationState.Idle,
    val selectedTab: WalletDemoTab = WalletDemoTab.Credentials,
    val requestDrafts: WalletRequestDrafts = WalletRequestDrafts(),
    val lastReceivedCredentialIds: List<String> = emptyList(),
    val receiveCompleted: Boolean = false,
    val receiveNavigationResetKey: Int = 0,
    val presentationPreview: WalletDemoPresentationPreview? = null,
    val selectedPresentationCredentialIds: Set<String> = emptySet(),
    val presentationCompleted: Boolean = false,
    val presentationNavigationResetKey: Int = 0,
)

fun WalletDemoUiState.receivedCredentials(): List<WalletDemoCredential> {
    val ready = session as? WalletSessionState.Ready ?: return emptyList()
    val credentialsById = ready.credentials.associateBy { it.id }
    return lastReceivedCredentialIds.mapNotNull { id -> credentialsById[id] }
}

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
