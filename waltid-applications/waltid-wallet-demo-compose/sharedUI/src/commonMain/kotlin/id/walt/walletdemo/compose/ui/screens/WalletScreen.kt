package id.walt.walletdemo.compose.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import id.walt.walletdemo.compose.logic.WalletDemoController
import id.walt.walletdemo.compose.logic.WalletDemoTab
import id.walt.walletdemo.compose.logic.WalletDemoUiState
import id.walt.walletdemo.compose.logic.WalletSessionState
import id.walt.walletdemo.compose.logic.receiveUrlEntryEnabled
import id.walt.walletdemo.compose.logic.presentationUrlEntryEnabled
import id.walt.walletdemo.compose.logic.receivedCredentials
import id.walt.walletdemo.compose.logic.toCredentialDetails
import id.walt.walletdemo.compose.ui.WalletRoute
import id.walt.walletdemo.compose.ui.WalletUiTestTags

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WalletScreen(controller: WalletDemoController, state: WalletDemoUiState) {
    val ready = state.session as? WalletSessionState.Ready
    val credentials = ready?.credentials.orEmpty()
    val credentialsBackStack = remember { mutableStateListOf<WalletRoute>(WalletRoute.Root) }
    val receiveBackStack = remember { mutableStateListOf<WalletRoute>(WalletRoute.Root) }
    val presentBackStack = remember { mutableStateListOf<WalletRoute>(WalletRoute.Root) }
    val uriHandler = LocalUriHandler.current
    var scanVisible by remember { mutableStateOf(false) }
    val scanSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val interactionSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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

    Scaffold(
        modifier = if (scanVisible || state.selectedTab != WalletDemoTab.Credentials) {
            Modifier.clearAndSetSemantics { }
        } else {
            Modifier
        },
        topBar = {
            WalletHeader(
                did = ready?.did,
                state = state,
                scanEnabled = ready != null,
                onScan = { scanVisible = true },
                onLock = controller::lock,
            )
        },
    ) { contentPadding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)

        WalletTabNavDisplay(
            backStack = credentialsBackStack,
            details = credentials.map { it.toCredentialDetails() },
            modifier = modifier,
            storedCredentialActions = true,
            onDeleteCredential = controller::deleteCredential,
            root = {
                CredentialsTab(
                    credentials = credentials,
                    onCredentialClick = { detailsId -> credentialsBackStack.pushDetails(detailsId) },
                    onScan = { scanVisible = true },
                    scanEnabled = ready != null,
                )
            },
        )
    }

    if (scanVisible) {
        ModalBottomSheet(
            onDismissRequest = { scanVisible = false },
            sheetState = scanSheetState,
        ) {
            UnifiedScanSheet(
                onSubmit = controller::submitInteractionInput,
                onAccepted = {
                    scanVisible = false
                    controller.resolveCurrentInteraction()
                },
                onDismiss = { scanVisible = false },
            )
        }
    }

    when (state.selectedTab) {
        WalletDemoTab.Credentials -> Unit
        WalletDemoTab.Receive -> {
            val receivedDetails = state.receivedCredentials().map { it.toCredentialDetails() }
            ModalBottomSheet(
                onDismissRequest = controller::dismissInteraction,
                sheetState = interactionSheetState,
            ) {
                WalletTabNavDisplay(
                    backStack = receiveBackStack,
                    details = receivedDetails,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.94f)
                        .testTag(WalletUiTestTags.InteractionSheet),
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
                            showsInput = state.receiveUrlEntryEnabled,
                        )
                    },
                )
            }
        }
        WalletDemoTab.Present -> {
            val presentationDetails = state.presentationPreview
                ?.credentialOptions
                .orEmpty()
                .map { it.toCredentialDetails() }
            ModalBottomSheet(
                onDismissRequest = controller::dismissInteraction,
                sheetState = interactionSheetState,
            ) {
                WalletTabNavDisplay(
                    backStack = presentBackStack,
                    details = presentationDetails.ifEmpty { credentials.map { it.toCredentialDetails() } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.94f)
                        .testTag(WalletUiTestTags.InteractionSheet),
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
                            showsInput = state.presentationUrlEntryEnabled,
                        )
                    },
                )
            }
        }
    }
}
