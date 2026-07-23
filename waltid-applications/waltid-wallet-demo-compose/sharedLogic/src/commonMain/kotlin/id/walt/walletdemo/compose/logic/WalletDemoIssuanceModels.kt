package id.walt.walletdemo.compose.logic

enum class WalletDemoIssuanceGrant { PreAuthorizedCode, AuthorizationCode }

data class WalletDemoIssuanceSession(
    val id: String,
    val grant: WalletDemoIssuanceGrant,
    val preview: WalletDemoOfferPreview,
    val authorizationUrl: String? = null,
)

sealed interface WalletDemoIssuanceOutcome {
    data class Stored(val credentialIds: List<String>) : WalletDemoIssuanceOutcome
    data class Deferred(val storedCredentialIds: List<String>) : WalletDemoIssuanceOutcome
    data object Cancelled : WalletDemoIssuanceOutcome
    data class Failed(val message: String) : WalletDemoIssuanceOutcome
}
