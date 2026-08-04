package id.walt.walletdemo.compose.logic

sealed interface WalletDemoOperationResult {
    val message: String

    data class Success(
        override val message: String,
        val continuation: WalletDemoPresentationContinuation? = null,
    ) : WalletDemoOperationResult

    data class Failure(
        override val message: String,
    ) : WalletDemoOperationResult
}

sealed interface WalletDemoPresentationContinuation {
    data class Url(val value: String) : WalletDemoPresentationContinuation
    data class FormPostHtml(val value: String) : WalletDemoPresentationContinuation
}

data class WalletDemoPendingPresentationContinuation(
    val continuation: WalletDemoPresentationContinuation,
    val successMessage: String,
)
