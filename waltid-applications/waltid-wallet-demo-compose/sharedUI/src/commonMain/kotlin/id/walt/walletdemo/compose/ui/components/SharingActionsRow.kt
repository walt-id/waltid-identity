package id.walt.walletdemo.compose.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import id.walt.walletdemo.compose.logic.WalletDemoPaymentReview
import id.walt.walletdemo.compose.logic.consent
import id.walt.walletdemo.compose.logic.canConfirm
import id.walt.walletdemo.compose.ui.WalletUiTestTags
import id.walt.walletdemo.compose.ui.resources.Res
import id.walt.walletdemo.compose.ui.resources.payment_confirm
import id.walt.walletdemo.compose.ui.resources.payment_decline
import id.walt.walletdemo.compose.ui.resources.proximity_approve
import id.walt.walletdemo.compose.ui.resources.proximity_decline
import id.walt.walletdemo.compose.ui.resources.proximity_cancel
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun rememberSharingSubmission(
    enabled: Boolean,
    selectionComplete: Boolean,
    reviewKey: Any,
    credentialStatuses: List<ReviewCredentialStatus>,
    onSubmit: (Map<String, String>) -> Unit,
    presentation: ReviewActionPresentation = ReviewActionPresentation.Sharing,
    paymentReview: WalletDemoPaymentReview = WalletDemoPaymentReview.NotRequired,
    isPayment: Boolean = false,
    submitLabelOverride: String? = null,
): ReviewedSubmission {
    val revisions = credentialStatuses.reviewedRevisions(LocalCredentialStatusUi.current.snapshot)
    val canSubmit = enabled && selectionComplete && paymentReview.canConfirm && revisions != null
    val submitLabel = sharingSubmitLabel(presentation, paymentReview, isPayment, submitLabelOverride)
    return rememberReviewedSubmission(listOf(LocalCredentialStatusUi.current.contextKey, reviewKey,
        LocalCredentialStatusUi.current.snapshot.reviewEpoch),
        credentialStatuses, paymentReview, canSubmit, revisions, submitLabel,
        ordinarySharing = presentation == ReviewActionPresentation.Sharing && !isPayment && paymentReview.consent == null,
        onSubmit = onSubmit)
}

@Composable
internal fun SharingActionsRow(
    submission: ReviewedSubmission,
    enabled: Boolean,
    onCancel: () -> Unit,
    onReject: (() -> Unit)?,
    presentation: ReviewActionPresentation = ReviewActionPresentation.Sharing,
    paymentReview: WalletDemoPaymentReview = WalletDemoPaymentReview.NotRequired,
    isPayment: Boolean = false,
    showCancelWithReject: Boolean = true,
    showCancel: Boolean = true,
    submitLabelOverride: String? = null,
) {
    val submitLabel = sharingSubmitLabel(presentation, paymentReview, isPayment, submitLabelOverride)
    val rejectLabel = paymentReview.consent?.denialAction ?: if (isPayment) stringResource(Res.string.payment_decline) else when (presentation) {
        ReviewActionPresentation.Sharing -> "Reject"
        ReviewActionPresentation.Proximity -> stringResource(Res.string.proximity_decline)
    }
    val cancelLabel = (if (onReject == null) paymentReview.consent?.denialAction else null) ?: when (presentation) {
        ReviewActionPresentation.Sharing -> if (onReject == null) "Cancel" else "Cancel review"
        ReviewActionPresentation.Proximity -> stringResource(Res.string.proximity_cancel)
    }
    val cancel = WalletAction(cancelLabel, onCancel,
        enabled = enabled || presentation == ReviewActionPresentation.Proximity, testTag = presentation.cancelTestTag)
    WalletActions(
        primary = WalletAction(submitLabel, submission.submit, submission.enabled, presentation.submitTestTag),
        secondary = onReject?.let { WalletAction(rejectLabel, it, enabled, presentation.rejectTestTag) } ?: cancel.takeIf { showCancel },
        tertiary = cancel.takeIf { onReject != null && showCancelWithReject && showCancel },
        modifier = Modifier.testTag(WalletUiTestTags.PresentationActions),
    )
}

@Composable
private fun sharingSubmitLabel(
    presentation: ReviewActionPresentation,
    paymentReview: WalletDemoPaymentReview,
    isPayment: Boolean,
    labelOverride: String?,
): String = labelOverride ?: paymentReview.consent?.affirmativeAction ?: if (isPayment) stringResource(Res.string.payment_confirm) else when (presentation) {
    ReviewActionPresentation.Sharing -> "Share"
    ReviewActionPresentation.Proximity -> stringResource(Res.string.proximity_approve)
}

internal enum class ReviewActionPresentation {
    Sharing,
    Proximity;

    val submitTestTag: String
        get() = when (this) {
            Sharing -> WalletUiTestTags.PresentationSubmitButton
            Proximity -> WalletUiTestTags.ProximityApprove
        }

    val rejectTestTag: String
        get() = when (this) {
            Sharing -> WalletUiTestTags.PresentationRejectButton
            Proximity -> WalletUiTestTags.ProximityDecline
        }

    val cancelTestTag: String
        get() = when (this) {
            Sharing -> WalletUiTestTags.PresentationCancelButton
            Proximity -> WalletUiTestTags.ProximityCancel
        }
}
