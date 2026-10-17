package id.walt.walletdemo.compose.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.unit.dp
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import id.walt.walletdemo.compose.logic.DemoBiometricAvailability
import id.walt.walletdemo.compose.logic.recoveryAvailability
import id.walt.walletdemo.compose.logic.WalletDemoController
import id.walt.walletdemo.compose.logic.WalletDemoTab
import id.walt.walletdemo.compose.logic.WalletDemoUiState
import id.walt.walletdemo.compose.logic.WalletSessionState
import id.walt.walletdemo.compose.logic.canDismissExternalFlow
import id.walt.walletdemo.compose.ui.WalletReviewHost
import id.walt.walletdemo.compose.logic.isStatusVisible
import id.walt.walletdemo.compose.ui.components.StatusCard
import id.walt.walletdemo.compose.ui.components.WalletFooter
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
        IdentitySetupScreen(setup.setup, state.warning, controller::chooseIdentity, controller::resumeSigningIdentity, controller::cancelIdentity, controller::refreshIdentityChoices, progress = state.identityProgress,
            biometricAvailability = state.biometricSigningAvailability?.recoveryAvailability(state.access.biometricAvailability)
                ?: DemoBiometricAvailability.Unavailable, biometricKind = state.access.biometricKind)
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

    val returnHome: () -> Unit = {
        showingScanner = false
        if (state.externalFlow != null) controller.closeExternalFlow()
        else {
            controller.startNewReceiveFlow()
            controller.startNewPresentationFlow()
            controller.selectTab(WalletDemoTab.Credentials)
        }
    }
    val showingFlow = state.selectedTab != WalletDemoTab.Credentials && presentationContent == null
    LaunchedEffect(state.externalFlow) {
        if (state.externalFlow != null) showingSettings = false
    }

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
            resetWalletDescription = resetWalletDescription,
            onRequestSigningProtectionChange = controller::requestSigningProtectionChange,
            onConfirmSigningProtectionChange = controller::confirmSigningProtectionChange,
            onCancelSigningProtectionChange = controller::cancelSigningProtectionChange,
            readerTrustSettingsContent = readerTrustSettingsContent,
            readerTrustPolicySummary = readerTrustPolicySummary,
            onProximityApprovalModeChange = onStartProximityPresentation?.let { controller::setProximityApprovalMode },
            allowWalletReset = allowWalletReset,
            serverSettingsContent = serverSettingsContent,
            walletAccessContent = { WalletAccessSettingsScreen(controller, state.access) },
            onCancelPinChange = controller::cancelPinChange,
        )
        return
    }

    // Home remains mounted beneath scanner, review, result and nearby sheets.
    val collectionState = state.copy(selectedTab = WalletDemoTab.Credentials)
    Scaffold(
        modifier = if (showingScanner || showingFlow || presentationContent != null) Modifier.clearAndSetSemantics {} else Modifier,
        topBar = {
            val chrome = detailsChrome
            if (chrome != null) {
                CredentialDetailsTopBar(chrome)
            } else {
                Column {
                    WalletHeader(
                        state = collectionState,
                        onSettings = { onOpenSettings(); showingSettings = true },
                        onScan = { showingScanner = true },
                        onShareNearby = onStartProximityPresentation?.let { start ->
                            {
                                controller.startNewPresentationFlow()
                                controller.selectTab(WalletDemoTab.Present)
                                start()
                            }
                        },
                    )
                    state.sharingSettingsError?.let { SettingsNotice(it, error = true) }
                }
            }
        },
        bottomBar = {
            if (!showingScanner && !showingFlow && presentationContent == null && detailsChrome == null && state.isStatusVisible) {
                WalletFooter(feedback = {
                    StatusCard(state, controller::dismissStatus, controller::toggleStatusExpanded)
                })
            }
        },
    ) { contentPadding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .consumeWindowInsets(contentPadding)

        Column(modifier) {
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
    }
    presentationContent?.invoke()
    if (showingScanner || showingFlow) WalletReviewHost(
        dismissEnabled = !showingFlow || state.canDismissExternalFlow,
        onDismiss = returnHome,
    ) {
        if (showingFlow) WalletFlowScreen(controller, state, onClose = returnHome)
        else WalletScanScreen(onBack = returnHome, onOpen = { value, kind ->
            if (showingScanner) {
                controller.openResolvedLink(value, kind)
                showingScanner = false
            }
        })
    }
}
