package id.walt.walletdemo.compose.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.WalletDemoCredentialStatus
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
    isPayment: Boolean = false,
    showCancelWithReject: Boolean = true,
    credentialStatuses: List<Pair<String, WalletDemoCredentialStatus>> = emptyList(),
) {
    val canSubmit = enabled && selectionComplete && paymentReview.canConfirm
    val submit = rememberPaymentSubmission(paymentReview, canSubmit, onSubmit)
    val submitLabel = paymentReview.consent?.affirmativeAction ?: if (isPayment) stringResource(Res.string.payment_confirm) else when (presentation) {
        ReviewActionPresentation.Sharing -> if (credentialStatuses.any { it.second.adverse }) stringResource(Res.string.credential_status_share_anyway) else "Share"
        ReviewActionPresentation.Proximity -> stringResource(Res.string.proximity_approve)
    }
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    CredentialStatusAdvisory(credentialStatuses)
    WalletActions(
        primary = WalletAction(submitLabel, submit, canSubmit, presentation.submitTestTag),
        secondary = onReject?.let { WalletAction(rejectLabel, it, enabled, presentation.rejectTestTag) } ?: cancel,
        tertiary = cancel.takeIf { onReject != null && showCancelWithReject },
        modifier = Modifier.testTag(WalletUiTestTags.PresentationActions),
    )
    }
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
