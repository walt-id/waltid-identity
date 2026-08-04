package id.walt.walletdemo.compose.logic

sealed interface WalletOperationState {
    data object Idle : WalletOperationState
    data object ResolvingOffer : WalletOperationState
    data object OfferPreview : WalletOperationState
    data object Receiving : WalletOperationState
    data object ResolvingPresentation : WalletOperationState
    data object Presenting : WalletOperationState
    data object DecliningPresentation : WalletOperationState

    data class Succeeded(
        val message: String,
        val tab: WalletDemoTab? = null,
    ) : WalletOperationState

    data class Failed(
        val message: String,
        val tab: WalletDemoTab? = null,
    ) : WalletOperationState
}
