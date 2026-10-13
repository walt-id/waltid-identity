package id.walt.walletdemo.compose.ui.screens

import id.walt.walletdemo.compose.ui.LocalWalletSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import id.walt.walletdemo.compose.logic.WalletDemoPresentationCredentialSelection
import id.walt.walletdemo.compose.logic.WalletDemoPresentationDisclosureSelection
import id.walt.walletdemo.compose.logic.WalletDemoUiState
import id.walt.walletdemo.compose.logic.presentationCredentialSelectionComplete
import id.walt.walletdemo.compose.logic.presentationPreviewActionEnabled
import id.walt.walletdemo.compose.logic.presentationReviewEnabled
import id.walt.walletdemo.compose.logic.toSharingReview
import id.walt.walletdemo.compose.logic.isPaymentTransaction
import id.walt.walletdemo.compose.ui.WalletUiTestTags
import id.walt.walletdemo.compose.ui.components.PresentationErrorSection
import id.walt.walletdemo.compose.ui.components.ReviewScaffold
import id.walt.walletdemo.compose.ui.components.SharingActionsRow
import id.walt.walletdemo.compose.ui.components.SharingReviewSection
import id.walt.walletdemo.compose.ui.components.rememberSharingInformation

@Composable
internal fun PresentTab(
    state: WalletDemoUiState,
    onPreview: () -> Unit,
    onToggleCredential: (WalletDemoPresentationCredentialSelection) -> Unit,
    onToggleDisclosure: (WalletDemoPresentationDisclosureSelection) -> Unit,
    onSubmit: () -> Unit,
    onReject: () -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    fillViewport: Boolean = !LocalWalletSheet.current,
    feedback: (@Composable () -> Unit)? = null,
) {
    val preview = state.presentationPreview
    val error = state.presentationError

    if (state.presentationCompleted) {
        val result = state.operation
        val dismissal = id.walt.walletdemo.compose.ui.components.rememberSuccessDismissal(
            state.presentationNavigationResetKey, result is id.walt.walletdemo.compose.logic.WalletOperationState.Succeeded,
            onDone)
        ReviewScaffold(modifier = modifier.then(dismissal).testTag("wallet.presentationResult"), fillViewport = fillViewport,
            actions = { id.walt.walletdemo.compose.ui.components.WalletActions(
                id.walt.walletdemo.compose.ui.components.WalletAction("Done", onDone, testTag = "wallet.presentationDone")) }) {
            Text(when (result) {
                is id.walt.walletdemo.compose.logic.WalletOperationState.Succeeded -> result.message
                is id.walt.walletdemo.compose.logic.WalletOperationState.Failed -> result.message
                else -> "The sharing operation has ended."
            }, style = MaterialTheme.typography.bodyLarge,
                color = if (result is id.walt.walletdemo.compose.logic.WalletOperationState.Failed)
                    MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        }
        return
    }
    if (state.pendingPresentationContinuation != null) {
        ReviewScaffold(modifier = modifier, fillViewport = fillViewport) {
            Text("Finishing the response…", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    if (preview != null) {
        val information by rememberSharingInformation(preview.credentialOptions)
        ReviewScaffold(
            fillViewport = fillViewport,
            modifier = modifier.testTag(WalletUiTestTags.PresentTabContent),
            actions = {
                SharingActionsRow(
                    paymentReview = state.paymentReview,
                    isPayment = preview.transactionData.any { it.isPaymentTransaction },
                    enabled = state.presentationReviewEnabled,
                    selectionComplete = information != null && state.presentationCredentialSelectionComplete(),
                    onSubmit = onSubmit,
                    onCancel = onCancel,
                    onReject = onReject,
                    showCancelWithReject = false,
                )
            },
        ) {
            feedback?.invoke()
            SharingReviewSection(
                paymentReview = state.paymentReview,
                review = preview.toSharingReview(),
                information = information,
                selectedCredentialOptions = state.selectedPresentationCredentialOptions,
                selectedDisclosureOptions = state.selectedPresentationDisclosureOptions,
                enabled = state.presentationReviewEnabled,
                readOnly = false,
                onToggleCredential = onToggleCredential,
                onToggleDisclosure = onToggleDisclosure,
                compact = false,
            )
        }
        return
    }

    if (error != null) ReviewScaffold(modifier, fillViewport) {
        PresentationErrorSection(error, enabled = state.presentationReviewEnabled,
            onNotifyVerifier = onReject, onDismiss = onCancel)
    } else WalletRequestStatus(state, onRetry = onPreview,
        retryEnabled = state.presentationPreviewActionEnabled && state.requestDrafts.presentationRequestUrl.isNotBlank(),
        modifier = modifier.testTag(WalletUiTestTags.PresentTabContent), fillViewport = fillViewport)
}
