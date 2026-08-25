package id.walt.walletdemo.compose.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import id.walt.walletdemo.compose.logic.receivedCredentials
import id.walt.walletdemo.compose.logic.toCredentialDetails
import id.walt.walletdemo.compose.ui.WalletRoute

@Composable
internal fun WalletScreen(controller: WalletDemoController, state: WalletDemoUiState) {
    val ready = state.session as? WalletSessionState.Ready
    val credentials = ready?.credentials.orEmpty()
    val credentialsBackStack = remember { mutableStateListOf<WalletRoute>(WalletRoute.Root) }
    val receiveBackStack = remember { mutableStateListOf<WalletRoute>(WalletRoute.Root) }
    val presentBackStack = remember { mutableStateListOf<WalletRoute>(WalletRoute.Root) }
    val uriHandler = LocalUriHandler.current
    var showingSettings by remember { mutableStateOf(false) }

    LaunchedEffect(state.authorizationRequestUrl) {
        state.authorizationRequestUrl?.let { authorizationUrl ->
            uriHandler.openUri(authorizationUrl)
            controller.authorizationRequestOpened()
        }
    }

    LaunchedEffect(state.receiveNavigationResetKey) {
        receiveBackStack.resetToRoot()
    }
    LaunchedEffect(state.presentationNavigationResetKey) {
        presentBackStack.resetToRoot()
    }

    if (showingSettings) {
        SettingsScreen(
            state = state,
            onBack = { showingSettings = false },
            onLock = controller::lock,
            onResetWallet = controller::resetWallet,
            onRequestSigningProtectionChange = controller::requestSigningProtectionChange,
            onConfirmSigningProtectionChange = controller::confirmSigningProtectionChange,
            onCancelSigningProtectionChange = controller::cancelSigningProtectionChange,
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
            WalletDemoTab.Credentials -> WalletTabNavDisplay(
                backStack = credentialsBackStack,
                details = credentials.map { it.toCredentialDetails() },
                modifier = modifier,
                root = {
                    CredentialsTab(
                        credentials = credentials,
                        onCredentialClick = { detailsId -> credentialsBackStack.pushDetails(detailsId) },
                    )
                },
                onDeleteCredential = controller::deleteCredential,
            )
            WalletDemoTab.Receive -> {
                val receivedDetails = state.receivedCredentials()
                    .map { it.toCredentialDetails() }

                WalletTabNavDisplay(
                    backStack = receiveBackStack,
                    details = receivedDetails,
                    modifier = modifier,
                    root = {
                        ReceiveTab(
                            state = state,
                            requestDrafts = state.requestDrafts,
                            onOfferUrlChange = controller::updateOfferUrl,
                            onTxCodeChange = controller::updateTxCode,
                            onPreviewOffer = controller::previewOffer,
                            onAcceptOffer = controller::acceptOffer,
                            onDeclineOffer = controller::declineOffer,
                            onStartNew = controller::startNewReceiveFlow,
                            onResumeDeferred = controller::resumeDeferredCredential,
                            onCredentialClick = { detailsId -> receiveBackStack.pushDetails(detailsId) },
                        )
                    },
                    onDeleteCredential = controller::deleteCredential,
                )
            }
            WalletDemoTab.Present -> {
                val presentationDetails = state.presentationPreview
                    ?.credentialOptions
                    .orEmpty()
                    .map { it.toCredentialDetails() }

                WalletTabNavDisplay(
                    backStack = presentBackStack,
                    details = presentationDetails.ifEmpty { credentials.map { it.toCredentialDetails() } },
                    modifier = modifier,
                    root = {
                        PresentTab(
                            state = state,
                            requestDrafts = state.requestDrafts,
                            onPresentationRequestUrlChange = controller::updatePresentationRequestUrl,
                            onPreview = controller::previewPresentation,
                            onStartNew = controller::startNewPresentationFlow,
                            onToggleCredential = controller::togglePresentationCredential,
                            onToggleDisclosure = controller::togglePresentationDisclosure,
                            onCredentialClick = { detailsId -> presentBackStack.pushDetails(detailsId) },
                            onSubmit = controller::submitPresentation,
                            onReject = controller::rejectPresentation,
                            onCancel = controller::cancelPresentationReview,
                        )
                    },
                    onDeleteCredential = controller::deleteCredential,
                )
            }
        }
    }
}
