package id.walt.walletdemo.compose.logic

enum class WalletDemoIssuanceGrant { PreAuthorizedCode, AuthorizationCode }

data class WalletDemoHolderBinding(val keyId: String, val did: String? = null) {
    init { require(keyId.isNotBlank()) }
}

data class WalletDemoCredentialSelection(
    val credentialConfigurationId: String,
    val holderBindings: List<WalletDemoHolderBinding>,
) {
    init {
        require(credentialConfigurationId.isNotBlank())
        require(holderBindings.isNotEmpty())
    }
}

data class WalletDemoIssuanceSession(
    val id: String,
    val grant: WalletDemoIssuanceGrant,
    val preview: WalletDemoOfferPreview,
)

data class WalletDemoIssuanceAuthorization(
    val url: String,
)

data class WalletDemoDeferredCredential(
    val id: String,
    val credentialConfigurationId: String? = null,
    val intervalSeconds: Long?,
    val credentialIdentifier: String? = null,
)

sealed interface WalletDemoIssuanceOutcome {
    data class Stored(val credentialIds: List<String>) : WalletDemoIssuanceOutcome
    data class Deferred(
        val storedCredentialIds: List<String>,
        val credentials: List<WalletDemoDeferredCredential> = emptyList(),
    ) : WalletDemoIssuanceOutcome
    data object Cancelled : WalletDemoIssuanceOutcome
    data class Failed(
        val message: String,
        val storedCredentialIds: List<String> = emptyList(),
        val deferredCredentials: List<WalletDemoDeferredCredential> = emptyList(),
        val offerConsumed: Boolean = false,
        val failedTargetCount: Int = 0,
        val notAttemptedTargetCount: Int = 0,
    ) : WalletDemoIssuanceOutcome
}
