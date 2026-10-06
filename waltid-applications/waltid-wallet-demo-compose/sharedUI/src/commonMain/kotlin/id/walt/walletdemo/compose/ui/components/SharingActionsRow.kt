package id.walt.walletdemo.compose.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import id.walt.walletdemo.compose.logic.WalletDemoPaymentReview
import id.walt.walletdemo.compose.logic.consent
import id.walt.walletdemo.compose.logic.canConfirm
import id.walt.walletdemo.compose.ui.WalletUiTestTags
import id.walt.walletdemo.compose.ui.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SharingActionsRow(
    enabled: Boolean,
    selectionComplete: Boolean,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    onReject: (() -> Unit)?,
    presentation: ReviewActionPresentation = ReviewActionPresentation.Sharing,
    paymentReview: WalletDemoPaymentReview = WalletDemoPaymentReview.NotRequired,
    showCancelWithReject: Boolean = true,
) {
    val canSubmit = enabled && selectionComplete && paymentReview.canConfirm
    val submit = rememberPaymentSubmission(paymentReview, canSubmit, onSubmit)
    val submitLabel = paymentReview.consent?.affirmativeAction ?: when (presentation) {
        ReviewActionPresentation.Sharing -> "Share"
        ReviewActionPresentation.Proximity -> stringResource(Res.string.proximity_approve)
    }
    val rejectLabel = paymentReview.consent?.denialAction ?: when (presentation) {
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
        primary = WalletAction(submitLabel, submit, canSubmit, presentation.submitTestTag),
        secondary = onReject?.let { WalletAction(rejectLabel, it, enabled, presentation.rejectTestTag) } ?: cancel,
        tertiary = cancel.takeIf { onReject != null && showCancelWithReject },
        modifier = Modifier.testTag(WalletUiTestTags.PresentationActions),
    )
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
