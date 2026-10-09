package id.walt.walletdemo.compose.ui

import id.walt.walletdemo.compose.logic.*

/** Provider issuance states shared by wallet tasks and platform fulfillment hosts. */
sealed interface WalletDemoOfferCreateUiState {
    data object Loading : WalletDemoOfferCreateUiState

    data class Review(
        val preview: WalletDemoOfferPreview,
        val title: String = "Accept digital credential?",
        val submitting: Boolean = false,
        val errorMessage: String? = null,
    ) : WalletDemoOfferCreateUiState

    /**
     * Authorization-code grant: waiting while the shared external browser completes issuer/AS login.
     *
     * @property completing True after the `openid://` callback was delivered and issuance is finishing.
     */
    data class WaitingForAuthorization(
        val completing: Boolean = false,
    ) : WalletDemoOfferCreateUiState

    data class Receipt(
        val receipt: WalletDemoIssuanceReceipt,
        val saved: List<WalletDemoCredential>,
        val pending: List<WalletDemoDeferredCredential>,
        val busy: Boolean = false,
        val refreshError: String? = null,
    ) : WalletDemoOfferCreateUiState

    data class Failure(val message: String) : WalletDemoOfferCreateUiState
}
