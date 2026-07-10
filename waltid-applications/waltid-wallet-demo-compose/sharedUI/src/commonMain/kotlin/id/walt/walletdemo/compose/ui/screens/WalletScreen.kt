package id.walt.walletdemo.compose.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
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

    LaunchedEffect(state.receiveNavigationResetKey) {
        receiveBackStack.resetToRoot()
    }
    LaunchedEffect(state.presentationNavigationResetKey) {
        presentBackStack.resetToRoot()
    }

    Scaffold(
        topBar = {
            WalletHeader(
                did = ready?.did,
                state = state,
                onLock = controller::lock,
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
                        onCredentialClick = { credentialId -> credentialsBackStack.pushDetails(credentialId) },
                    )
                },
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
                            onReceive = controller::receive,
                            onStartNew = controller::startNewReceiveFlow,
                            onCredentialClick = { credentialId -> receiveBackStack.pushDetails(credentialId) },
                        )
                    },
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
                            onCredentialClick = { credentialId -> presentBackStack.pushDetails(credentialId) },
                            onSubmit = controller::submitPresentation,
                            onCancel = controller::cancelPresentationReview,
                        )
                    },
                )
            }
        }
    }
}
