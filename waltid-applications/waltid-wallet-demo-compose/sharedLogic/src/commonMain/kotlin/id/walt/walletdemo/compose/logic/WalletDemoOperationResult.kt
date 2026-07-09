package id.walt.walletdemo.compose.logic

sealed interface WalletDemoOperationResult {
    val message: String

    data class Success(
        override val message: String,
    ) : WalletDemoOperationResult

    data class Failure(
        override val message: String,
    ) : WalletDemoOperationResult
}
