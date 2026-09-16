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
import id.walt.walletdemo.compose.logic.WalletDemoController
import id.walt.walletdemo.compose.logic.WalletDemoTab
import id.walt.walletdemo.compose.logic.WalletDemoUiState
import id.walt.walletdemo.compose.logic.WalletSessionState
import id.walt.walletdemo.compose.ui.rememberAuthorizationRequestOpener

@Composable
internal fun WalletScreen(
    controller: WalletDemoController,
    state: WalletDemoUiState,
    onStartProximityPresentation: (() -> Unit)? = null,
    presentationContent: (@Composable () -> Unit)? = null,
    sharingSettingsContent: (@Composable () -> Unit)? = null,
    onOpenSettings: () -> Unit = {},
    onResetWallet: () -> Unit = { controller.resetWallet() },
    onSignOut: (() -> Unit)? = null,
) {
    val setup = state.session as? WalletSessionState.IdentitySetup
    if (setup != null) {
        IdentitySetupScreen(setup.setup, state.warning, controller::chooseIdentity, controller::resumeIdentity, controller::cancelIdentity, controller::refreshIdentityChoices, refreshing = state.identityBusy)
        return
    }
    val openAuthorizationRequest = rememberAuthorizationRequestOpener()
    var showingSettings by remember { mutableStateOf(false) }
    var detailsChrome by remember { mutableStateOf<CredentialDetailsChrome?>(null) }

    LaunchedEffect(state.authorizationRequestUrl) {
        state.authorizationRequestUrl?.let { authorizationUrl ->
            openAuthorizationRequest(authorizationUrl)
            controller.authorizationRequestOpened()
        }
    }

    if (showingSettings) {
        val ready = state.session as? WalletSessionState.Ready
        LaunchedEffect(ready?.did, ready?.keyId) {
            if (ready != null) controller.refreshIdentityDetails()
        }
        SettingsScreen(
            state = state,
            onShowDcApiPresentationPreviewChange = controller::setShowDcApiPresentationPreview,
            onProximityTransportProfileChange = onStartProximityPresentation?.let {
                controller::setProximityTransportProfile
            },
            onBack = { showingSettings = false },
            onIdentityAction = controller::performIdentityAction,
            onRefreshIdentityDetails = controller::refreshIdentityDetails,
            onLock = controller::lock,
            onResetWallet = onResetWallet,
            onSignOut = onSignOut,
            onRequestSigningProtectionChange = controller::requestSigningProtectionChange,
            onConfirmSigningProtectionChange = controller::confirmSigningProtectionChange,
            onCancelSigningProtectionChange = controller::cancelSigningProtectionChange,
            sharingSettingsContent = sharingSettingsContent,
            onProximityApprovalModeChange = onStartProximityPresentation?.let { controller::setProximityApprovalMode },
        )
        return
    }

    Scaffold(
        topBar = {
            val chrome = detailsChrome
            if (chrome != null) {
                CredentialDetailsTopBar(chrome)
            } else {
                WalletHeader(
                    state = state,
                    onSettings = { onOpenSettings(); showingSettings = true },
                    onDismissStatus = controller::dismissStatus,
                    onToggleStatusExpanded = controller::toggleStatusExpanded,
                )
            }
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
                session = state.session,
                onDeleteCredential = controller::deleteCredential,
                onDetailsChromeChange = { detailsChrome = it },
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
                    onToggleCredential = controller::togglePresentationCredential,
                    onToggleDisclosure = controller::togglePresentationDisclosure,
                    onSubmit = controller::submitPresentation,
                    onReject = controller::rejectPresentation,
                    onCancel = controller::cancelPresentationReview,
                    onStartProximityPresentation = onStartProximityPresentation,
                    presentationContent = presentationContent,
                    modifier = modifier,
                )
            }
        }
    }
}
