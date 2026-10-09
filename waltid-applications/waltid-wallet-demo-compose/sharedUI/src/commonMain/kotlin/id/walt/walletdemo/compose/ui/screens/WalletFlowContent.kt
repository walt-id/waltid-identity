package id.walt.walletdemo.compose.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import id.walt.walletdemo.compose.logic.WalletDemoController
import id.walt.walletdemo.compose.logic.WalletDemoTab
import id.walt.walletdemo.compose.logic.WalletDemoUiState
import id.walt.walletdemo.compose.logic.WalletStatusKind
import id.walt.walletdemo.compose.logic.isStatusVisible
import id.walt.walletdemo.compose.logic.statusBanner
import id.walt.walletdemo.compose.ui.components.StatusCard

/** Online task content; the request owner retains drafts and consent through sheet navigation. */
@Composable
internal fun WalletFlowContent(
    controller: WalletDemoController,
    state: WalletDemoUiState,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    fillViewport: Boolean = true,
) {
    val banner = state.statusBanner().takeIf { state.isStatusVisible }
    val feedback: (@Composable () -> Unit)? = if (banner?.kind == WalletStatusKind.Busy || banner?.kind == WalletStatusKind.Error) {
        { StatusCard(state, controller::dismissStatus, controller::toggleStatusExpanded) }
    } else null
    when (state.selectedTab) {
        WalletDemoTab.Receive -> {
            ReceiveTab(
                state = state,
                requestDrafts = state.requestDrafts,
                onTxCodeChange = controller::updateTxCode,
                onCopiesChange = controller::updateIssuanceCopies,
                onPreviewOffer = controller::previewOffer,
                onAcceptOffer = controller::acceptOffer,
                onDeclineOffer = controller::declineOffer,
                onResumeDeferred = controller::resumeDeferredCredential,
                onDone = onDone,
                onRefresh = controller::refreshIssuanceStatus,
                modifier = modifier,
                fillViewport = fillViewport,
                feedback = feedback,
            )
        }
        WalletDemoTab.Present -> {
            PresentTab(
                state = state,
                onPreview = controller::previewPresentation,
                onToggleCredential = controller::togglePresentationCredential,
                onToggleDisclosure = controller::togglePresentationDisclosure,
                onSubmit = {
                    val current = controller.state.value
                    if (current.presentationReview == state.presentationReview &&
                        current.paymentReview == state.paymentReview &&
                        current.selectedPresentationCredentialOptions == state.selectedPresentationCredentialOptions &&
                        current.selectedPresentationDisclosureOptions == state.selectedPresentationDisclosureOptions) {
                        controller.submitPresentation()
                    }
                },
                onReject = controller::rejectPresentation,
                onCancel = {
                    if (state.externalFlow != null) onDone()
                    else { controller.cancelPresentationReview(); controller.selectTab(WalletDemoTab.Credentials) }
                },
                onDone = onDone,
                modifier = modifier,
                fillViewport = fillViewport,
                feedback = feedback,
            )
        }
        WalletDemoTab.Credentials -> Unit
    }
}
