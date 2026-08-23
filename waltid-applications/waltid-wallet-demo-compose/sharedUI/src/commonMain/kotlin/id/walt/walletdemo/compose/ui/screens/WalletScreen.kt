package id.walt.walletdemo.compose.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import id.walt.walletdemo.compose.logic.WalletDemoController
import id.walt.walletdemo.compose.logic.WalletDemoReviewRoute
import id.walt.walletdemo.compose.logic.WalletDemoTab
import id.walt.walletdemo.compose.logic.WalletDemoUiState
import id.walt.walletdemo.compose.logic.WalletOperationState
import id.walt.walletdemo.compose.logic.WalletSessionState
import id.walt.walletdemo.compose.logic.isBusy
import id.walt.walletdemo.compose.logic.receiveUrlEntryEnabled
import id.walt.walletdemo.compose.logic.presentationUrlEntryEnabled
import id.walt.walletdemo.compose.logic.receivedCredentials
import id.walt.walletdemo.compose.logic.toCredentialDetails
import id.walt.walletdemo.compose.ui.WalletRoute
import id.walt.walletdemo.compose.ui.WalletUiTestTags
import id.walt.walletdemo.compose.ui.exportTestTagsForPlatformAutomation

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
    val snackbarHostState = remember { SnackbarHostState() }
    val credentialsRoute = credentialsBackStack.lastOrNull()
    var credentialTechnicalTitle by remember { mutableStateOf<String?>(null) }
    var credentialTechnicalBackSignal by remember { mutableStateOf(0) }
    var receiveTechnicalTitle by remember { mutableStateOf<String?>(null) }
    var receiveTechnicalBackSignal by remember { mutableStateOf(0) }
    var presentTechnicalTitle by remember { mutableStateOf<String?>(null) }
    var presentTechnicalBackSignal by remember { mutableStateOf(0) }
    val credentialsDestinationTitle = credentialTechnicalTitle ?: when (credentialsRoute) {
        is WalletRoute.Settings -> "Settings"
        is WalletRoute.CredentialDetails -> "Credential details"
        else -> null
    }
    val successMessage = (state.operation as? WalletOperationState.Succeeded)?.message

    LaunchedEffect(successMessage) {
        successMessage?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

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
    LaunchedEffect(credentialsRoute) {
        if (credentialsRoute !is WalletRoute.CredentialDetails) credentialTechnicalTitle = null
    }

    Scaffold(
        modifier = if (scanVisible || state.selectedTab != WalletDemoTab.Credentials) {
            Modifier.clearAndSetSemantics { }
        } else {
            Modifier
        },
        topBar = {
            WalletHeader(
                state = state,
                scanEnabled = ready != null,
                destinationTitle = credentialsDestinationTitle,
                backTestTag = when {
                    credentialTechnicalTitle != null -> WalletUiTestTags.ReviewTechnicalDetailsBack
                    credentialsRoute is WalletRoute.Settings -> WalletUiTestTags.SettingsBack
                    else -> WalletUiTestTags.DetailsBack
                },
                onBack = {
                    if (credentialTechnicalTitle != null) {
                        credentialTechnicalBackSignal += 1
                    } else {
                        credentialsBackStack.removeLastOrNull()
                    }
                },
                onScan = { scanVisible = true },
                onSettings = { credentialsBackStack.pushSettings() },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
            detailsTechnicalBackSignal = credentialTechnicalBackSignal,
            onDetailsReviewRouteChanged = { route, island ->
                credentialTechnicalTitle = when (route) {
                    WalletDemoReviewRoute.Summary -> null
                    is WalletDemoReviewRoute.TechnicalDetails -> island?.title
                }
            },
            settings = {
                ready?.let { session ->
                    WalletSettingsScreen(
                        state = state,
                        session = session,
                        resetEnabled = !state.isBusy,
                        onLock = controller::lock,
                        onReset = controller::resetWallet,
                        onDismissStatus = controller::dismissOperationStatus,
                    )
                }
            },
            root = {
                CredentialsTab(
                    state = state,
                    credentials = credentials,
                    onCredentialClick = { detailsId -> credentialsBackStack.pushDetails(detailsId) },
                    onScan = { scanVisible = true },
                    scanEnabled = ready != null,
                    onDismissStatus = controller::dismissOperationStatus,
                )
            },
        )
    }

    if (scanVisible) {
        ModalBottomSheet(
            onDismissRequest = { scanVisible = false },
            sheetState = scanSheetState,
            containerColor = MaterialTheme.colorScheme.background,
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
                containerColor = MaterialTheme.colorScheme.background,
            ) {
                val receiveRoute = receiveBackStack.lastOrNull()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.94f)
                        .exportTestTagsForPlatformAutomation()
                        .testTag(WalletUiTestTags.InteractionSheet),
                ) {
                    WalletSheetHeader(
                        title = receiveTechnicalTitle ?: if (receiveRoute is WalletRoute.CredentialDetails) {
                            "Credential details"
                        } else if (state.offerPreview != null && !state.receiveCompleted) {
                            "Add credential"
                        } else {
                            "Receive"
                        },
                        onBack = when {
                            receiveTechnicalTitle != null -> ({ receiveTechnicalBackSignal += 1 })
                            receiveRoute is WalletRoute.CredentialDetails -> ({ receiveBackStack.removeLastOrNull() })
                            else -> null
                        },
                        onClose = controller::dismissInteraction,
                        backTestTag = if (receiveTechnicalTitle != null) {
                            WalletUiTestTags.ReviewTechnicalDetailsBack
                        } else {
                            WalletUiTestTags.DetailsBack
                        },
                    )
                    WalletTabNavDisplay(
                        backStack = receiveBackStack,
                        details = receivedDetails,
                        modifier = Modifier.weight(1f),
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
                                onDismissStatus = controller::dismissOperationStatus,
                                showsInput = state.receiveUrlEntryEnabled,
                                technicalBackSignal = receiveTechnicalBackSignal,
                                onReviewRouteChanged = { route, island ->
                                    receiveTechnicalTitle = when (route) {
                                        WalletDemoReviewRoute.Summary -> null
                                        is WalletDemoReviewRoute.TechnicalDetails -> island?.title
                                    }
                                },
                            )
                        },
                    )
                }
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
                containerColor = MaterialTheme.colorScheme.background,
            ) {
                val presentRoute = presentBackStack.lastOrNull()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.94f)
                        .exportTestTagsForPlatformAutomation()
                        .testTag(WalletUiTestTags.InteractionSheet),
                ) {
                    WalletSheetHeader(
                        title = presentTechnicalTitle ?: if (presentRoute is WalletRoute.CredentialDetails) {
                            "Credential details"
                        } else {
                            "Share information"
                        },
                        onBack = when {
                            presentTechnicalTitle != null -> ({ presentTechnicalBackSignal += 1 })
                            presentRoute is WalletRoute.CredentialDetails -> ({ presentBackStack.removeLastOrNull() })
                            else -> null
                        },
                        onClose = controller::dismissInteraction,
                        backTestTag = if (presentTechnicalTitle != null) {
                            WalletUiTestTags.ReviewTechnicalDetailsBack
                        } else {
                            WalletUiTestTags.DetailsBack
                        },
                    )
                    WalletTabNavDisplay(
                        backStack = presentBackStack,
                        details = presentationDetails.ifEmpty { credentials.map { it.toCredentialDetails() } },
                        modifier = Modifier.weight(1f),
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
                                onDismissStatus = controller::dismissOperationStatus,
                                showsInput = state.presentationUrlEntryEnabled,
                                technicalBackSignal = presentTechnicalBackSignal,
                                onReviewRouteChanged = { route, island ->
                                    presentTechnicalTitle = when (route) {
                                        WalletDemoReviewRoute.Summary -> null
                                        is WalletDemoReviewRoute.TechnicalDetails -> island?.title
                                    }
                                },
                            )
                        },
                    )
                }
            }
        }
    }
}
