package id.walt.walletdemo.compose.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import id.walt.walletdemo.compose.ui.components.SettingsNotice
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import id.walt.walletdemo.compose.logic.WalletDemoController
import id.walt.walletdemo.compose.logic.WalletDemoTab
import id.walt.walletdemo.compose.logic.WalletDemoUiState
import id.walt.walletdemo.compose.logic.WalletSessionState
import id.walt.walletdemo.compose.logic.WalletLinkKind
import id.walt.walletdemo.compose.logic.isBusy
import id.walt.walletdemo.compose.ui.SystemBackHandler
import id.walt.walletdemo.compose.ui.rememberAuthorizationRequestOpener

@Composable
internal fun WalletScreen(
    controller: WalletDemoController,
    state: WalletDemoUiState,
    onStartProximityPresentation: (() -> Unit)? = null,
    presentationContent: (@Composable () -> Unit)? = null,
    readerTrustSettingsContent: (@Composable () -> Unit)? = null,
    readerTrustPolicySummary: String? = null,
    onOpenSettings: () -> Unit = {},
    onResetWallet: () -> Unit = { controller.resetWallet() },
    onSignOut: (() -> Unit)? = null,
    resetWalletDescription: String? = null,
) {
    val setup = state.session as? WalletSessionState.IdentitySetup
    if (setup != null) {
        IdentitySetupScreen(setup.setup, state.warning, controller::chooseIdentity, controller::resumeSigningIdentity, controller::cancelIdentity, controller::refreshIdentityChoices, progress = state.identityProgress)
        return
    }
    if (state.session !is WalletSessionState.Ready) {
        WalletOpeningScreen((state.session as? WalletSessionState.Failed)?.message, controller::retryOpeningWallet)
        return
    }
    val openAuthorizationRequest = rememberAuthorizationRequestOpener()
    var showingSettings by rememberSaveable { mutableStateOf(false) }
    var showingScanner by rememberSaveable { mutableStateOf(false) }
    var detailsChrome by remember { mutableStateOf<CredentialDetailsChrome?>(null) }

    val returnHome = {
        when (state.selectedTab) {
            WalletDemoTab.Receive -> controller.startNewReceiveFlow()
            WalletDemoTab.Present -> controller.startNewPresentationFlow()
            WalletDemoTab.Credentials -> Unit
        }
        controller.selectTab(WalletDemoTab.Credentials)
    }
    // A flow's own detail/review back handler is composed later and takes precedence.
    SystemBackHandler(enabled = !showingSettings && !showingScanner && detailsChrome == null &&
        state.selectedTab != WalletDemoTab.Credentials && !state.isBusy,
        onBack = returnHome)

    LaunchedEffect(state.selectedTab) {
        if (state.selectedTab != WalletDemoTab.Credentials) showingScanner = false
    }

    LaunchedEffect(state.authorizationRequestUrl) {
        state.authorizationRequestUrl?.let { authorizationUrl ->
            openAuthorizationRequest(authorizationUrl)
            controller.authorizationRequestOpened()
        }
    }

    if (showingScanner) {
        WalletScanScreen(onBack = { showingScanner = false }, onOpen = { value, kind ->
            showingScanner = false
            when (kind) {
                WalletLinkKind.Offer -> {
                    controller.startNewPresentationFlow()
                    controller.startNewReceiveFlow()
                    controller.selectTab(WalletDemoTab.Receive)
                    controller.updateOfferUrl(value)
                    controller.previewOffer()
                }
                WalletLinkKind.Presentation -> {
                    controller.startNewReceiveFlow()
                    controller.startNewPresentationFlow()
                    controller.selectTab(WalletDemoTab.Present)
                    controller.updatePresentationRequestUrl(value)
                    controller.previewPresentation()
                }
                WalletLinkKind.AuthorizationCallback -> controller.handleDeepLink(value)
                else -> Unit
            }
        })
        return
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
            resetWalletDescription = resetWalletDescription,
            onRequestSigningProtectionChange = controller::requestSigningProtectionChange,
            onConfirmSigningProtectionChange = controller::confirmSigningProtectionChange,
            onCancelSigningProtectionChange = controller::cancelSigningProtectionChange,
            readerTrustSettingsContent = readerTrustSettingsContent,
            readerTrustPolicySummary = readerTrustPolicySummary,
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
                Column {
                    WalletHeader(
                        state = state,
                        onSettings = { onOpenSettings(); showingSettings = true },
                        onDismissStatus = controller::dismissStatus,
                        onToggleStatusExpanded = controller::toggleStatusExpanded,
                        onScan = ({ showingScanner = true }).takeIf { state.selectedTab == WalletDemoTab.Credentials },
                        onShareNearby = onStartProximityPresentation?.let { start ->
                            {
                                controller.startNewPresentationFlow()
                                controller.selectTab(WalletDemoTab.Present)
                                start()
                            }
                        }.takeIf { state.selectedTab == WalletDemoTab.Credentials },
                        onBack = returnHome.takeIf { state.selectedTab != WalletDemoTab.Credentials && !state.isBusy },
                        title = when (state.selectedTab) {
                            WalletDemoTab.Credentials -> null
                            WalletDemoTab.Receive -> "Receive credentials"
                            WalletDemoTab.Present -> "Share credentials"
                        },
                    )
                    state.sharingSettingsError?.let { SettingsNotice(it, error = true) }
                }
            }
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
                    onCopiesChange = controller::updateIssuanceCopies,
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
