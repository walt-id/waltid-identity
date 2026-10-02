package id.walt.walletdemo.compose.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import id.walt.walletdemo.compose.logic.WalletDemoPaymentConsent
import id.walt.walletdemo.compose.logic.WalletDemoPresentationCredentialSelection
import id.walt.walletdemo.compose.logic.WalletDemoSharingReview
import id.walt.walletdemo.compose.logic.WalletDemoSharingSelection
import id.walt.walletdemo.compose.logic.consent
import id.walt.walletdemo.compose.logic.defaultCredentialSelection
import id.walt.walletdemo.compose.logic.hasCompleteCredentialSelection
import id.walt.walletdemo.compose.logic.toggleCredential
import id.walt.walletdemo.compose.logic.toggleDisclosure
import id.walt.walletdemo.compose.ui.components.ReviewScaffold
import id.walt.walletdemo.compose.ui.components.SharingActionsRow
import id.walt.walletdemo.compose.ui.components.SharingReviewSection
import id.walt.walletdemo.compose.ui.components.rememberPaymentReview

/**
 * One sharing review for full-screen and platform-invoked sheet hosts.
 *
 * Credential/disclosure choices and resolved payment consent belong to the request, outside the
 * presentation host. Changing [presentation] neither resets them nor prepares/authorizes a new
 * transaction. [compact] shortens the selection heading; every host uses the same thumbnail rows.
 *
 * The caller owns the transport and OS result. [onCancel] declines the request; [onBackAtRoot]
 * leaves this review (for example, returning to Credential Manager's selector without a result).
 * Both the root back gesture and sheet dismissal are blocked while [enabled] is false. A null
 * [onBackAtRoot] lets a full-screen caller handle Back and makes a sheet non-dismissible.
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
    presentation: WalletReviewPresentation = WalletReviewPresentation.FullScreen,
    preparePaymentConsent: (suspend (WalletDemoSharingSelection) -> WalletDemoPaymentConsent?)? = null,
) {
    var selection by remember(review) {
        mutableStateOf(WalletDemoSharingSelection(credentials = review.defaultCredentialSelection()))
    }
    val paymentReview = rememberPaymentReview(review, selection, preparePaymentConsent)
    val submit = { onSubmit(selection.copy(paymentConsentRevision = paymentReview.consent?.revision)) }
    val selectionComplete = review.hasCompleteCredentialSelection(selection.credentials)

    WalletReviewHost(presentation, dismissEnabled = enabled, onDismiss = onBackAtRoot) { fillViewport ->
        ReviewScaffold(
            fillViewport = fillViewport,
            actions = {
                SharingActionsRow(
                    paymentReview = paymentReview,
                    enabled = enabled,
                    selectionComplete = selectionComplete,
                    onSubmit = submit,
                    onCancel = onCancel,
                    onReject = onReject,
                )
            },
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            SharingReviewSection(
                paymentReview = paymentReview,
                review = review,
                selectedCredentialOptions = selection.credentials,
                selectedDisclosureOptions = selection.disclosures,
                selectionComplete = selectionComplete,
                enabled = enabled,
                compact = compact,
                showActions = false,
                onToggleCredential = { credential ->
                    selection = selection.toggleCredential(
                        selection = credential,
                        option = review.credentialOptions.firstOrNull { it.selection == credential },
                    )
                },
                onToggleDisclosure = { disclosure -> selection = selection.toggleDisclosure(disclosure) },
                onSubmit = submit,
                onCancel = onCancel,
                onReject = onReject,
            )
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

    /** Claims dialog opened from a compact credential card. */
    val ClaimsDialog: String get() = WalletUiTestTags.PresentationClaimsDialog

    /** Close button in the claims dialog. */
    val ClaimsCloseButton: String get() = WalletUiTestTags.PresentationClaimsClose

    /** Compact credential card for the given presentation option. */
    fun credentialCard(queryId: String, credentialId: String): String =
        WalletUiTestTags.presentationClaimsToggle(
            WalletDemoPresentationCredentialSelection(queryId = queryId, credentialId = credentialId).id,
        )

    /** Requester section. */
    val RequesterSection: String get() = WalletUiTestTags.PresentationVerifierSection

    /** Reader-authentication section, rendered only for protocols that have reader auth. */
    val ReaderTrustSection: String get() = WalletUiTestTags.PresentationReaderTrustSection

    /** Response-protection section. */
    val ResponseProtectionSection: String get() = WalletUiTestTags.PresentationResponseProtectionSection
}
