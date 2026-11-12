package id.walt.walletdemo.compose.ui

import id.walt.walletdemo.compose.ui.components.WalletHeaderButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import id.walt.walletdemo.compose.logic.WalletDemoPaymentConsent
import id.walt.walletdemo.compose.logic.WalletDemoPresentationCredentialSelection
import id.walt.walletdemo.compose.logic.WalletDemoSharingReview
import id.walt.walletdemo.compose.logic.WalletDemoSharingSelection
import id.walt.walletdemo.compose.logic.WalletDemoSharingReviewController
import id.walt.walletdemo.compose.logic.hasCompleteCredentialSelection
import id.walt.walletdemo.compose.logic.isPaymentTransaction
import id.walt.walletdemo.compose.ui.components.ReviewScaffold
import id.walt.walletdemo.compose.ui.components.SharingActionsRow
import id.walt.walletdemo.compose.ui.components.LocalCredentialStatusUi
import id.walt.walletdemo.compose.ui.components.SharingReviewSection
import id.walt.walletdemo.compose.ui.components.rememberSharingInformation
import id.walt.walletdemo.compose.ui.components.WalletScreenHeader
import id.walt.walletdemo.compose.ui.components.WalletIcon
import id.walt.walletdemo.compose.ui.components.WalletSymbol

/**
 * Shared modal review for provider hosts. Choices and payment consent belong to the request owner.
 * The caller owns transport and OS results: [onCancel] declines, while [onBackAtRoot] can return
 * to Credential Manager's selector without answering. Busy reviews block dismissal; a null
 * [onBackAtRoot] makes the sheet non-dismissible. [compact] shortens the selection heading.
 */
@Composable
fun WalletDemoSharingReviewScreen(
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
    credentialStatuses: id.walt.walletdemo.compose.logic.WalletDemoCredentialStatuses? = null,
    statusReviewNotice: String? = null,
) {
    val scope = rememberCoroutineScope()
    val owner = controller ?: remember(review) { WalletDemoSharingReviewController(review, scope, preparePaymentConsent) }
    require(owner.review == review) { "The review controller belongs to another request" }
    DisposableEffect(owner) { onDispose { if (controller == null) owner.close() } }
    val state by owner.state.collectAsState()
    val selection = state.selection
    val paymentReview = state.payment
    val statuses = credentialStatuses ?: LocalCredentialStatusUi.current.snapshot
    val submit = { owner.selectionForSubmission(state)?.let { onSubmit(it.copy(credentialStatusRevisions = statuses.credentials.mapValues { it.value.advisoryRevision })) }; Unit }
    val information by rememberSharingInformation(review.credentialOptions)
    val selectionComplete = information != null && review.hasCompleteCredentialSelection(selection.credentials)

    androidx.compose.runtime.CompositionLocalProvider(LocalCredentialStatusUi provides id.walt.walletdemo.compose.ui.components.CredentialStatusUiContext(statuses)) {
    WalletReviewHost(dismissEnabled = enabled, onDismiss = onBackAtRoot) {
        ReviewScaffold(
            fillViewport = false,
            header = {
                WalletScreenHeader(title) {
                    WalletHeaderButton(onClick = onBackAtRoot ?: onCancel, enabled = enabled,
                        modifier = Modifier.testTag(WalletUiTestTags.FlowBack)) {
                        WalletIcon(WalletSymbol.Decline, "Close request")
                    }
                }
            },
            actions = {
                SharingActionsRow(
                    credentialStatuses = review.credentialOptions.filter { it.selection in selection.credentials }
                        .mapNotNull { option -> LocalCredentialStatusUi.current.snapshot.credentials[option.credentialId]?.let { (information?.firstOrNull { info -> info.option.selection == option.selection }?.summary?.title ?: option.label ?: "Credential") to it } },
                    paymentReview = paymentReview,
                    isPayment = review.request.transactionData.any { it.isPaymentTransaction },
                    enabled = enabled,
                    selectionComplete = selectionComplete,
                    onSubmit = submit,
                    onCancel = onCancel,
                    onReject = onReject,
                    showCancelWithReject = false,
                )
            },
        ) {
            statusReviewNotice?.let { androidx.compose.material3.Text(it) }
            SharingReviewSection(
                paymentReview = paymentReview,
                review = review,
                information = information,
                selectedCredentialOptions = selection.credentials,
                selectedDisclosureOptions = selection.disclosures,
                enabled = enabled,
                compact = compact,
                onToggleCredential = owner::toggleCredential,
                onToggleDisclosure = owner::toggleDisclosure,
            )
        }
    }
    }
}

/** Compose test tags the sharing review exposes to platform UI automation. */
object WalletDemoSharingReviewTestTags {
    /** Root of the review surface. */
    val Review: String get() = WalletUiTestTags.PresentationReview

    /** Share confirmation button. */
    val ShareButton: String get() = WalletUiTestTags.PresentationSubmitButton

    /** Cancel button. */
    val CancelButton: String get() = WalletUiTestTags.PresentationCancelButton

    /** Selection row for the given presentation option. */
    fun credentialRow(queryId: String, credentialId: String): String =
        WalletUiTestTags.presentationCredential(
            WalletDemoPresentationCredentialSelection(queryId = queryId, credentialId = credentialId).id,
        )

    /** Requester section. */
    val RequesterSection: String get() = WalletUiTestTags.PresentationVerifierSection

    /** Reader-authentication section, rendered only for protocols that have reader auth. */
    val ReaderTrustSection: String get() = WalletUiTestTags.PresentationReaderTrustSection

    /** Response-protection section. */
    val ResponseProtectionSection: String get() = WalletUiTestTags.PresentationResponseProtectionSection
}
