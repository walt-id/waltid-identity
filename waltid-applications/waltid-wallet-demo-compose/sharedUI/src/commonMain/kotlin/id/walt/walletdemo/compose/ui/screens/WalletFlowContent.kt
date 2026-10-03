package id.walt.walletdemo.compose.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import id.walt.walletdemo.compose.logic.WalletDemoController
import id.walt.walletdemo.compose.logic.WalletDemoTab
import id.walt.walletdemo.compose.logic.WalletDemoUiState

/** The in-app route and external host bind exactly the same content and current consent. */
@Composable
internal fun WalletFlowContent(
    controller: WalletDemoController,
    state: WalletDemoUiState,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    fillViewport: Boolean = true,
    onStartProximityPresentation: (() -> Unit)? = null,
    presentationContent: (@Composable () -> Unit)? = null,
) {
    when (state.selectedTab) {
        WalletDemoTab.Receive -> {
            ReceiveTab(
                state = state,
                requestDrafts = state.requestDrafts,
                onOfferUrlChange = controller::updateOfferUrl,
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
            )
        }
        WalletDemoTab.Present -> {
            PresentTab(
                state = state,
                requestDrafts = state.requestDrafts,
                onPresentationRequestUrlChange = controller::updatePresentationRequestUrl,
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
                onCancel = controller::cancelPresentationReview,
                onStartProximityPresentation = onStartProximityPresentation,
                presentationContent = presentationContent,
                modifier = modifier,
                fillViewport = fillViewport,
            )
        }
        WalletDemoTab.Credentials -> Unit
    }
}
