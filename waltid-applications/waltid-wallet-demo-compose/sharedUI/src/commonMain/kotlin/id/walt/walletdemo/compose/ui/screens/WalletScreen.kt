package id.walt.walletdemo.compose.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.ui.components.WalletReviewNavigationHost
import id.walt.walletdemo.compose.ui.components.WalletSection
import id.walt.walletdemo.compose.ui.components.WalletNavigationRow
import id.walt.walletdemo.compose.ui.components.WalletIcon
import id.walt.walletdemo.compose.ui.components.WalletSymbol
import id.walt.walletdemo.compose.ui.resources.*
import org.jetbrains.compose.resources.stringResource
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
import id.walt.walletdemo.compose.logic.receivedCredentials
import id.walt.walletdemo.compose.logic.isStatusVisible
import id.walt.walletdemo.compose.logic.statusBanner
import id.walt.walletdemo.compose.logic.WalletStatusKind
import id.walt.walletdemo.compose.ui.components.StatusCard
import id.walt.walletdemo.compose.ui.components.WalletFooter
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
    allowWalletReset: Boolean = true,
    allowCredentialDelete: Boolean = true,
    serverSettingsContent: (@Composable () -> Unit)? = null,
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
        state.selectedTab != WalletDemoTab.Credentials && presentationContent == null && !state.isBusy,
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

    val scanner: @Composable () -> Unit = {
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
            allowWalletReset = allowWalletReset,
            serverSettingsContent = serverSettingsContent,
        )
        return
    }

    if (state.selectedTab != WalletDemoTab.Credentials && presentationContent == null) {
        WalletReviewNavigationHost(
            requestKey = "${state.selectedTab}:${state.receiveNavigationResetKey}:${state.presentationNavigationResetKey}",
            offer = state.offerPreview,
            savedCredentials = state.receivedCredentials(),
            sharingOptions = state.presentationPreview?.credentialOptions.orEmpty(),
            selectedCredentials = state.selectedPresentationCredentialOptions,
            selectedDisclosures = state.selectedPresentationDisclosureOptions,
            enabled = !state.isBusy,
            onToggleDisclosure = controller::togglePresentationDisclosure,
            onClose = returnHome.takeIf { !state.isBusy },
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(Modifier.fillMaxSize()) {
                WalletHeader(state, onSettings = null, onClose = returnHome.takeIf { !state.isBusy },
                    title = if (state.selectedTab == WalletDemoTab.Receive) "Receive credentials" else if (state.presentationCompleted) "Sharing result" else "Share credentials")
                WalletFlowContent(controller, state, onDone = returnHome, modifier = Modifier.weight(1f),
                    onStartProximityPresentation = onStartProximityPresentation, presentationContent = presentationContent)
            }
        }
        return
    }

    // A nearby task is modal over Home; its lifecycle state never exposes the old online entry page.
    val collectionState = if (presentationContent != null) state.copy(selectedTab = WalletDemoTab.Credentials) else state
    Scaffold(
        topBar = {
            val chrome = detailsChrome
            if (chrome != null) {
                CredentialDetailsTopBar(chrome)
            } else {
                Column {
                    WalletHeader(
                        state = collectionState,
                        onSettings = { onOpenSettings(); showingSettings = true },
                        onScan = ({ showingScanner = true }).takeIf { collectionState.selectedTab == WalletDemoTab.Credentials },
                        onShareNearby = onStartProximityPresentation?.let { start ->
                            {
                                controller.startNewPresentationFlow()
                                controller.selectTab(WalletDemoTab.Present)
                                start()
                            }
                        }.takeIf { collectionState.selectedTab == WalletDemoTab.Credentials },
                        onBack = returnHome.takeIf { collectionState.selectedTab != WalletDemoTab.Credentials && !state.isBusy },
                        title = when (collectionState.selectedTab) {
                            WalletDemoTab.Credentials -> null
                            WalletDemoTab.Receive -> "Receive credentials"
                            WalletDemoTab.Present -> "Share credentials"
                        },
                    )
                    state.sharingSettingsError?.let { SettingsNotice(it, error = true) }
                }
            }
        },
        bottomBar = {
            if (collectionState.selectedTab == WalletDemoTab.Credentials && detailsChrome == null && state.isStatusVisible) {
                WalletFooter(modifier = Modifier.navigationBarsPadding(), feedback = {
                    StatusCard(state, controller::dismissStatus, controller::toggleStatusExpanded)
                })
            }
        },
    ) { contentPadding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .consumeWindowInsets(contentPadding)

        when (collectionState.selectedTab) {
            WalletDemoTab.Credentials -> Column(modifier) {
                if (state.deferredCredentials.isNotEmpty() && detailsChrome == null) WalletSection(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    WalletNavigationRow(stringResource(Res.string.issuance_pending_count, state.deferredCredentials.size),
                        onClick = { controller.startNewReceiveFlow(); controller.selectTab(WalletDemoTab.Receive) },
                        icon = { WalletIcon(WalletSymbol.Receive, null) })
                }
                CredentialsTab(
                session = state.session,
                onDeleteCredential = if (allowCredentialDelete) controller::deleteCredential else null,
                onDetailsChromeChange = { detailsChrome = it },
                modifier = Modifier.weight(1f),
                )
            }
            WalletDemoTab.Receive, WalletDemoTab.Present -> WalletFlowContent(
                controller, state, onDone = { controller.selectTab(WalletDemoTab.Credentials) },
                modifier = modifier, onStartProximityPresentation = onStartProximityPresentation,
                presentationContent = presentationContent,
            )
        }
    }
    presentationContent?.invoke()
    if (showingScanner) id.walt.walletdemo.compose.ui.WalletReviewHost(
        id.walt.walletdemo.compose.ui.WalletReviewPresentation.Sheet, true, onDismiss = { showingScanner = false },
    ) { scanner() }

}
