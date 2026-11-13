package id.walt.walletdemo.compose.ui

import androidx.compose.runtime.Composable
import id.walt.walletdemo.compose.logic.WalletDemoCredentialStatuses
import id.walt.walletdemo.compose.logic.WalletDemoPaymentConsent
import id.walt.walletdemo.compose.logic.WalletDemoSharingReview
import id.walt.walletdemo.compose.logic.WalletDemoSharingReviewController
import id.walt.walletdemo.compose.logic.WalletDemoSharingSelection

/** The status-capable fixture supplies a real derived no-reference assessment for each record. */
@Composable
internal fun TestSharingReviewScreen(
    review: WalletDemoSharingReview,
    title: String,
    onSubmit: (WalletDemoSharingSelection) -> Unit,
    onCancel: () -> Unit,
    onReject: (() -> Unit)? = null,
    enabled: Boolean = true,
    onBackAtRoot: (() -> Unit)? = null,
    compact: Boolean = true,
    preparePaymentConsent: (suspend (WalletDemoSharingSelection) -> WalletDemoPaymentConsent?)? = null,
    controller: WalletDemoSharingReviewController? = null,
    credentialStatuses: WalletDemoCredentialStatuses? = null,
    statusReviewNotice: String? = null,
) {
    val statuses = credentialStatuses ?: WalletDemoCredentialStatuses(review.credentialOptions.associate {
        it.credentialId to WalletVisualFixtures.credentialStatus("not_provided")
    })
    WalletDemoSharingReviewScreen(review, title, onSubmit, onCancel, onReject, enabled, onBackAtRoot,
        compact, preparePaymentConsent, controller, statuses, statusReviewNotice)
}
