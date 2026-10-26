package id.walt.walletdemo.compose.logic

/** A recognized external request remains pending through unlock and signing-key setup. */
sealed interface WalletExternalFlow {
    val url: String
    val kind: Kind
    val tab: WalletDemoTab get() = when (kind) { Kind.Offer -> WalletDemoTab.Receive; Kind.Presentation -> WalletDemoTab.Present }

    enum class Kind { Offer, Presentation }

    data class Pending(override val url: String, override val kind: Kind) : WalletExternalFlow
    data class Active(override val url: String, override val kind: Kind) : WalletExternalFlow
    data class UnavailableCallback(override val url: String) : WalletExternalFlow {
        override val kind: Kind = Kind.Offer
    }
}

/** Resolving a preview is cancellable; accepting or sending must retain its owner and outcome. */
val WalletDemoUiState.canDismissExternalFlow: Boolean
    get() = !isAuthenticating && !identityBusy && !isChangingSigningProtection &&
        canAcceptExternalRequest

internal val WalletDemoUiState.canAcceptExternalRequest: Boolean
    get() = operation != WalletOperationState.Receiving && operation != WalletOperationState.Presenting &&
        operation != WalletOperationState.DecliningPresentation && pendingPresentationContinuation == null
