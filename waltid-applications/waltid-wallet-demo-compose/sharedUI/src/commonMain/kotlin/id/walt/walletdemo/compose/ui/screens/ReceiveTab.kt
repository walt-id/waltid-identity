package id.walt.walletdemo.compose.ui.screens

import id.walt.walletdemo.compose.ui.LocalWalletSheet
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import id.walt.walletdemo.compose.logic.isBusy
import id.walt.walletdemo.compose.logic.receivedCredentials
import id.walt.walletdemo.compose.logic.issuanceReceiptComplete
import id.walt.walletdemo.compose.logic.missingReceivedCredentialIds
import id.walt.walletdemo.compose.ui.components.*
import id.walt.walletdemo.compose.ui.resources.*
import org.jetbrains.compose.resources.stringResource
import id.walt.walletdemo.compose.logic.WalletDemoUiState
import id.walt.walletdemo.compose.logic.WalletRequestDrafts
import id.walt.walletdemo.compose.logic.acceptOfferEnabled
import id.walt.walletdemo.compose.logic.offerReviewEnabled
import id.walt.walletdemo.compose.ui.WalletUiTestTags
import id.walt.walletdemo.compose.ui.components.OfferReviewActions
import id.walt.walletdemo.compose.ui.components.OfferReviewSection
import id.walt.walletdemo.compose.ui.components.ReviewScaffold

@Composable
internal fun ReceiveTab(
    state: WalletDemoUiState,
    requestDrafts: WalletRequestDrafts,
    onTxCodeChange: (String) -> Unit,
    onCopiesChange: (String, Int) -> Unit,
    onPreviewOffer: () -> Unit,
    onAcceptOffer: () -> Unit,
    onDeclineOffer: () -> Unit,
    onResumeDeferred: (String) -> Unit,
    onDone: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    fillViewport: Boolean = !LocalWalletSheet.current,
    feedback: (@Composable () -> Unit)? = null,
) {
    val preview = state.offerPreview
    if (preview != null) {
        ReviewScaffold(
            fillViewport = fillViewport,
            modifier = modifier.testTag(WalletUiTestTags.ReceiveTabContent),
            actions = {
                OfferReviewActions(
                    requiresIssuerAuthentication = preview.requiresIssuerAuthentication,
                    acceptEnabled = state.acceptOfferEnabled,
                    reviewEnabled = state.offerReviewEnabled,
                    onAccept = onAcceptOffer,
                    onDecline = onDeclineOffer,
                )
            },
        ) {
            feedback?.invoke()
            OfferReviewSection(
                preview = preview,
                acceptEnabled = state.acceptOfferEnabled,
                reviewEnabled = state.offerReviewEnabled,
                txCode = requestDrafts.txCode,
                onTxCodeChange = onTxCodeChange,
                copies = state.issuanceCopyCounts,
                onCopiesChange = onCopiesChange,
                onAccept = onAcceptOffer,
                onDecline = onDeclineOffer,
                showActions = false,
            )
        }
        return
    }

    if (state.issuanceReceipt != null || state.deferredCredentials.isNotEmpty()) {
        val pending = state.deferredCredentials.filter { state.issuanceReceipt?.pendingIds?.contains(it.id) ?: true }
        val missingDetails = state.missingReceivedCredentialIds().isNotEmpty() || state.issuanceReceipt?.detailsError != null
        val dismissal = rememberSuccessDismissal(state.receiveNavigationResetKey,
            enabled = state.receiveCompleted && !state.isBusy && state.issuanceReceiptComplete() && pending.isEmpty()
                && state.operation is id.walt.walletdemo.compose.logic.WalletOperationState.Succeeded
                && state.receivedCredentials().isNotEmpty(), onDone = onDone)
        ReviewScaffold(fillViewport = fillViewport,
            modifier = modifier.then(dismissal).testTag(WalletUiTestTags.ReceiveTabContent), actions = {
            WalletActions(WalletAction(stringResource(Res.string.issuance_done), onDone,
                enabled = !state.isBusy, testTag = "issuance-done"),
                secondary = if (pending.isNotEmpty() || missingDetails) {
                    WalletAction(stringResource(Res.string.issuance_refresh_status), onRefresh,
                        enabled = !state.isBusy, testTag = "issuance-refresh")
                } else null)
        }) {
            feedback?.invoke()
            IssuanceResultContent(state.issuanceReceipt, state.receivedCredentials(), pending, state.isBusy, onResumeDeferred,
                missingSavedCount = state.missingReceivedCredentialIds().size)
        }
        return
    }

    WalletRequestStatus(state, onRetry = onPreviewOffer,
        retryEnabled = !state.isBusy && requestDrafts.offerUrl.isNotBlank(),
        modifier = modifier.testTag(WalletUiTestTags.ReceiveTabContent), fillViewport = fillViewport)
}
