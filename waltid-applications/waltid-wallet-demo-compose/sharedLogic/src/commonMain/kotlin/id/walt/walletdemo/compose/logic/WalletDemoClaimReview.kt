package id.walt.walletdemo.compose.logic

/** SDK-owned effective values. Received paths describe the payload; source paths own metadata/controls. */
data class WalletDemoPreparedClaimReview(val revision: String, val credentials: List<WalletDemoCredentialClaims>)
data class WalletDemoCredentialClaims(
    val queryId: String,
    val credentialId: String,
    val format: String,
    val claimsJson: String,
    val wholeCredential: Boolean,
    val locations: List<WalletDemoClaimLocation>,
)
data class WalletDemoClaimLocation(
    val sourcePathJson: String,
    val receivedPathJson: String,
    val sharedBy: List<String>,
    val alwaysCarried: Boolean,
)

sealed interface WalletDemoClaimReview {
    data object Legacy : WalletDemoClaimReview
    data class Loading(val previous: Ready? = null) : WalletDemoClaimReview
    data class Ready(val review: WalletDemoPreparedClaimReview, val groups: List<EffectiveSharingGroup>) : WalletDemoClaimReview
    data class Failed(val message: String) : WalletDemoClaimReview
}
val WalletDemoClaimReview.canConfirm: Boolean get() = this is WalletDemoClaimReview.Legacy || this is WalletDemoClaimReview.Ready
val WalletDemoClaimReview.revision: String? get() = (this as? WalletDemoClaimReview.Ready)?.review?.revision

internal val WalletDemoClaimReview.previousReady: WalletDemoClaimReview.Ready?
    get() = when (this) {
        is WalletDemoClaimReview.Ready -> this
        is WalletDemoClaimReview.Loading -> previous
        else -> null
    }
