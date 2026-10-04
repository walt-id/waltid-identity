package id.walt.walletdemo.compose.logic

/** Resolved plain-text instructions; source resolution and validation belong to the wallet SDK. */
data class WalletDemoPaymentConsent(
    val revision: String,
    val locale: String,
    val title: String?,
    val securityHint: String?,
    val affirmativeAction: String,
    val denialAction: String?,
    val requiresUnsignedRequestWarning: Boolean,
    val fields: List<WalletDemoPaymentField>,
)

data class WalletDemoPaymentField(
    val label: String,
    val description: String?,
    val value: String,
    val placement: WalletDemoPaymentFieldPlacement,
)

enum class WalletDemoPaymentFieldPlacement { Prominent, Main, Details, Omitted }

sealed interface WalletDemoPaymentReview {
    data object NotRequired : WalletDemoPaymentReview
    data object Loading : WalletDemoPaymentReview
    data class Ready(val consent: WalletDemoPaymentConsent) : WalletDemoPaymentReview
    data class Blocked(val message: String) : WalletDemoPaymentReview
}

val WalletDemoPaymentReview.canConfirm: Boolean
    get() = this is WalletDemoPaymentReview.NotRequired || this is WalletDemoPaymentReview.Ready
val WalletDemoPaymentReview.consent: WalletDemoPaymentConsent?
    get() = (this as? WalletDemoPaymentReview.Ready)?.consent
