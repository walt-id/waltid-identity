package id.walt.walletdemo.compose.ui

import id.walt.walletdemo.compose.logic.WalletDemoOfferPreview

/** Provider issuance states; the host decides full-screen or sheet presentation. */
sealed interface WalletDemoOfferCreateUiState {
    data object Loading : WalletDemoOfferCreateUiState

    data class Review(
        val preview: WalletDemoOfferPreview,
        val title: String = "Accept digital credential?",
        val submitting: Boolean = false,
    ) : WalletDemoOfferCreateUiState

    /**
     * Authorization-code grant: waiting while the shared external browser completes issuer/AS login.
     *
     * @property completing True after the `openid://` callback was delivered and issuance is finishing.
     */
    data class WaitingForAuthorization(
        val completing: Boolean = false,
    ) : WalletDemoOfferCreateUiState
}

