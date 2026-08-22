package id.walt.walletdemo.compose.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import id.walt.walletdemo.compose.logic.WalletDemoController
import id.walt.walletdemo.compose.logic.WalletDemoTab
import id.walt.walletdemo.compose.logic.WalletDemoUiState
import id.walt.walletdemo.compose.logic.WalletSessionState

@Composable
internal fun WalletScreen(controller: WalletDemoController, state: WalletDemoUiState) {
    val ready = state.session as? WalletSessionState.Ready
    val credentials = ready?.credentials.orEmpty()
    val uriHandler = LocalUriHandler.current
    var showingSettings by remember { mutableStateOf(false) }

    LaunchedEffect(state.authorizationRequestUrl) {
        state.authorizationRequestUrl?.let { authorizationUrl ->
            uriHandler.openUri(authorizationUrl)
            controller.authorizationRequestOpened()
        }
    }

    if (showingSettings) {
        SettingsScreen(
            ready = ready,
            onBack = { showingSettings = false },
            onLock = controller::lock,
            onResetWallet = controller::resetWallet,
        )
        return
    }

    Scaffold(
        topBar = {
            WalletHeader(
                state = state,
                onSettings = { showingSettings = true },
                onDismissStatus = controller::dismissStatus,
                onToggleStatusExpanded = controller::toggleStatusExpanded,
            )
        },
        bottomBar = {
            WalletBottomBar(
                selectedTab = state.selectedTab,
                onSelectedTab = controller::selectTab,
            )
        },
    ) { contentPadding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)

        when (state.selectedTab) {
            WalletDemoTab.Credentials -> CredentialsTab(
                credentials = credentials,
                modifier = modifier,
            )
            WalletDemoTab.Receive -> {
                ReceiveTab(
                    state = state,
                    requestDrafts = state.requestDrafts,
                    onOfferUrlChange = controller::updateOfferUrl,
                    onTxCodeChange = controller::updateTxCode,
                    onPreviewOffer = controller::previewOffer,
                    onAcceptOffer = controller::acceptOffer,
                    onDeclineOffer = controller::declineOffer,
                    onResumeDeferred = controller::resumeDeferredCredential,
                    modifier = modifier,
                )
            }
            WalletDemoTab.Present -> {
                PresentTab(
                    state = state,
                    requestDrafts = state.requestDrafts,
                    onPresentationRequestUrlChange = controller::updatePresentationRequestUrl,
                    onPreview = controller::previewPresentation,
                    onStartNew = controller::startNewPresentationFlow,
                    onToggleCredential = controller::togglePresentationCredential,
                    onToggleDisclosure = controller::togglePresentationDisclosure,
                    onSubmit = controller::submitPresentation,
                    onReject = controller::rejectPresentation,
                    onCancel = controller::cancelPresentationReview,
                    modifier = modifier,
                )
            }
        }
    }
}
