package id.walt.walletdemo.compose.logic

enum class WalletDemoIssuanceGrant { PreAuthorizedCode, AuthorizationCode }

data class WalletDemoIssuanceSession(
    val id: String,
    val grant: WalletDemoIssuanceGrant,
    val preview: WalletDemoOfferPreview,
    val authorizationUrl: String? = null,
)

data class WalletDemoDeferredCredential(
    val id: String,
    val credentialConfigurationId: String,
    val intervalSeconds: Long?,
)

sealed interface WalletDemoIssuanceOutcome {
    data class Stored(val credentialIds: List<String>) : WalletDemoIssuanceOutcome
    data class Deferred(
        val storedCredentialIds: List<String>,
        val credentials: List<WalletDemoDeferredCredential> = emptyList(),
    ) : WalletDemoIssuanceOutcome
    data object Cancelled : WalletDemoIssuanceOutcome
    data class Failed(val message: String) : WalletDemoIssuanceOutcome
}
