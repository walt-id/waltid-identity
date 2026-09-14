package id.walt.walletdemo.compose.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import id.walt.walletdemo.compose.logic.WalletDemoPresentationCredentialSelection
import id.walt.walletdemo.compose.logic.WalletDemoSharingReview
import id.walt.walletdemo.compose.logic.WalletDemoSharingSelection
import id.walt.walletdemo.compose.logic.defaultCredentialSelection
import id.walt.walletdemo.compose.logic.hasCompleteCredentialSelection
import id.walt.walletdemo.compose.logic.toggleCredential
import id.walt.walletdemo.compose.logic.toggleDisclosure
import id.walt.walletdemo.compose.ui.components.ReviewScaffold
import id.walt.walletdemo.compose.ui.components.SharingActionsRow
import id.walt.walletdemo.compose.ui.components.SharingReviewSection

/**
 * Standalone presentation-review screen for a platform-invoked sharing flow.
 *
 * The host owns the transport and the operating-system result; this screen owns only what the user
 * chooses. It therefore keeps credential and disclosure selection internally and hands the finished
 * [WalletDemoSharingSelection] to [onSubmit], so a host launched by the OS does not have to reproduce
 * the selection rules the in-app flow already implements.
 *
 * At the review root a platform back gesture is passed to [onBackAtRoot] - the host, not the review,
 * decides what leaving an OS-invoked surface means, and that is deliberately not assumed to equal
 * [onCancel]. Claims review is a dialog on this surface, not a pushed destination.
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
    compact: Boolean = true,
) {
    var selection by remember(review) {
        mutableStateOf(WalletDemoSharingSelection(credentials = review.defaultCredentialSelection()))
    }

    // Called unconditionally, as the platform handlers require. A submission already in flight
    // consumes the gesture and does nothing: the response is on its way, so neither closing this
    // screen nor abandoning it is an outcome the user can still choose.
    SystemBackHandler(
        enabled = !enabled || onBackAtRoot != null,
    ) {
        when {
            !enabled -> Unit
            else -> onBackAtRoot?.invoke()
        }
    }

    WalletDemoTheme {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .exportTestTagsForPlatformAutomation(),
            color = MaterialTheme.colorScheme.background,
        ) {
            ReviewScaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding(),
                    actions = {
                        SharingActionsRow(
                            enabled = enabled,
                            selectionComplete = review.hasCompleteCredentialSelection(selection.credentials),
                            onSubmit = { onSubmit(selection) },
                            onCancel = onCancel,
                            onReject = onReject,
                        )
                    },
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    SharingReviewSection(
                        review = review,
                        selectedCredentialOptions = selection.credentials,
                        selectedDisclosureOptions = selection.disclosures,
                        selectionComplete = review.hasCompleteCredentialSelection(selection.credentials),
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
                        onSubmit = { onSubmit(selection) },
                        onCancel = onCancel,
                        onReject = onReject,
                    )
                }
        }
    }
}

/**
 * Compact in-tray sharing review for Digital Credentials GET fulfillment.
 *
 * Cancel ends the caller's `getCredential`. Dismissing the sheet or backing out at the review root
 * leaves the provider without a Credential Manager result so the system selector can return.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletDemoSharingReviewSheet(
    review: WalletDemoSharingReview,
    title: String,
    onSubmit: (WalletDemoSharingSelection) -> Unit,
    onCancel: () -> Unit,
    onBackAtRoot: () -> Unit,
    enabled: Boolean = true,
) {
    var selection by remember(review) {
        mutableStateOf(WalletDemoSharingSelection(credentials = review.defaultCredentialSelection()))
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    SystemBackHandler(enabled = true) {
        when {
            !enabled -> Unit
            else -> onBackAtRoot()
        }
    }

    WalletDemoTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .exportTestTagsForPlatformAutomation(),
        ) {
            ModalBottomSheet(
                onDismissRequest = {
                    if (enabled) onBackAtRoot()
                },
                sheetState = sheetState,
            ) {
                Box(modifier = Modifier.exportTestTagsForPlatformAutomation()) {
                    ReviewScaffold(
                        fillViewport = false,
                        actions = {
                            SharingActionsRow(
                                enabled = enabled,
                                selectionComplete = review.hasCompleteCredentialSelection(selection.credentials),
                                onSubmit = { onSubmit(selection) },
                                onCancel = onCancel,
                                onReject = null,
                            )
                        },
                    ) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        SharingReviewSection(
                            review = review,
                            selectedCredentialOptions = selection.credentials,
                            selectedDisclosureOptions = selection.disclosures,
                            selectionComplete = review.hasCompleteCredentialSelection(selection.credentials),
                            enabled = enabled,
                            compact = true,
                            showActions = false,
                            onToggleCredential = { credential ->
                                selection = selection.toggleCredential(
                                    selection = credential,
                                    option = review.credentialOptions.firstOrNull { it.selection == credential },
                                )
                            },
                            onToggleDisclosure = { disclosure ->
                                selection = selection.toggleDisclosure(disclosure)
                            },
                            onSubmit = { onSubmit(selection) },
                            onCancel = onCancel,
                        )
                    }
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

    /** Claims dialog opened from a compact credential card. */
    val ClaimsDialog: String get() = WalletUiTestTags.PresentationClaimsDialog

    /** Close button in the claims dialog. */
    val ClaimsCloseButton: String get() = WalletUiTestTags.PresentationClaimsClose

    /** Compact credential card for the given presentation option. */
    fun credentialCard(queryId: String, credentialId: String): String =
        WalletUiTestTags.credentialCard(
            WalletDemoPresentationCredentialSelection(queryId = queryId, credentialId = credentialId).id,
        )

    /** Requester section. */
    val RequesterSection: String get() = WalletUiTestTags.PresentationVerifierSection

    /** Reader-authentication section, rendered only for protocols that have reader auth. */
    val ReaderTrustSection: String get() = WalletUiTestTags.PresentationReaderTrustSection

    /** Response-protection section. */
    val ResponseProtectionSection: String get() = WalletUiTestTags.PresentationResponseProtectionSection
}
