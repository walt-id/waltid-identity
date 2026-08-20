package id.walt.walletdemo.compose.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.WalletDemoSharingReview
import id.walt.walletdemo.compose.logic.WalletDemoSharingSelection
import id.walt.walletdemo.compose.logic.WalletDemoReviewSurfaceContext
import id.walt.walletdemo.compose.logic.defaultCredentialSelection
import id.walt.walletdemo.compose.logic.hasCompleteCredentialSelection
import id.walt.walletdemo.compose.logic.toCredentialDetails
import id.walt.walletdemo.compose.logic.toggleCredential
import id.walt.walletdemo.compose.logic.toggleDisclosure
import id.walt.walletdemo.compose.ui.components.CredentialDetailsContent
import id.walt.walletdemo.compose.ui.components.SharingReviewActionBar
import id.walt.walletdemo.compose.ui.components.SharingReviewSection
import id.walt.walletdemo.compose.ui.screens.CredentialDetailsScreen

/**
 * Standalone presentation-review screen for a platform-invoked sharing flow.
 *
 * The host owns the transport and the operating-system result; this screen owns only what the user
 * chooses. It therefore keeps credential and disclosure selection internally and hands the finished
 * [WalletDemoSharingSelection] to [onSubmit], so a host launched by the OS does not have to reproduce
 * the selection rules the in-app flow already implements.
 *
 * A platform back gesture is handled here only as far as this screen's own navigation goes: while
 * credential details are open it closes them. At the review root there is nothing left for the screen
 * to undo, so the gesture is passed to [onBackAtRoot] - the host, not the review, decides what leaving
 * an OS-invoked surface means, and that is deliberately not assumed to equal [onCancel].
 *
 * @param title Heading naming the kind of request, since a provider screen has no surrounding app chrome.
 * @param enabled Whether the user can still act; pass false while a submission is in flight.
 * @param onSubmit Invoked with the user's selection when Share is confirmed.
 * @param onCancel Invoked when the user declines without a protocol-level rejection.
 * @param onReject Protocol-level refusal, or null when the transport has no such message.
 * @param onBackAtRoot Platform back gesture at the review root, or null to let the host handle it.
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
) {
    var selection by remember(review) {
        mutableStateOf(WalletDemoSharingSelection(credentials = review.defaultCredentialSelection()))
    }
    var openCredentialDetailsId by remember(review) { mutableStateOf<String?>(null) }
    val openDetails = openCredentialDetailsId?.let { detailsId ->
        review.credentialOptions
            .map { it.toCredentialDetails() }
            .firstOrNull { it.summary.id == detailsId }
    }

    // Called unconditionally, as the platform handlers require. A submission already in flight
    // consumes the gesture and does nothing: the response is on its way, so neither closing this
    // screen nor abandoning it is an outcome the user can still choose.
    SystemBackHandler(
        enabled = openDetails != null || !enabled || onBackAtRoot != null,
    ) {
        when {
            openDetails != null -> openCredentialDetailsId = null
            !enabled -> Unit
            else -> onBackAtRoot?.invoke()
        }
    }

    MaterialTheme {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .exportTestTagsForPlatformAutomation(),
            color = MaterialTheme.colorScheme.background,
        ) {
            if (openDetails != null) {
                CredentialDetailsScreen(
                    details = openDetails,
                    onBack = { openCredentialDetailsId = null },
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding(),
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                    )
                    SharingReviewSection(
                        review = review,
                        selectedCredentialOptions = selection.credentials,
                        selectedDisclosureOptions = selection.disclosures,
                        selectionComplete = review.hasCompleteCredentialSelection(selection.credentials),
                        enabled = enabled,
                        onToggleCredential = { credential ->
                            selection = selection.toggleCredential(
                                selection = credential,
                                option = review.credentialOptions.firstOrNull { it.selection == credential },
                            )
                        },
                        onToggleDisclosure = { disclosure -> selection = selection.toggleDisclosure(disclosure) },
                        onCredentialClick = { detailsId -> openCredentialDetailsId = detailsId },
                        onSubmit = { onSubmit(selection) },
                        onCancel = onCancel,
                        onReject = onReject,
                        showActions = false,
                        scrollContent = true,
                        context = WalletDemoReviewSurfaceContext.PlatformInvoked,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 20.dp),
                    )
                    SharingReviewActionBar(
                        enabled = enabled,
                        selectionComplete = review.hasCompleteCredentialSelection(selection.credentials),
                        onSubmit = { onSubmit(selection) },
                        onCancel = onCancel,
                        onReject = onReject,
                    )
                }
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

    /** Verifier section. */
    val VerifierSection: String get() = WalletUiTestTags.PresentationVerifierSection

    /** Legacy automation alias retained while downstream tests migrate. */
    @Deprecated("Use VerifierSection")

    /** Reader-authentication section, rendered only for protocols that have reader auth. */
    val ReaderTrustSection: String get() = WalletUiTestTags.PresentationReaderTrustSection

    /** Response-protection section. */
    val ResponseProtectionSection: String get() = WalletUiTestTags.PresentationResponseProtectionSection
}
