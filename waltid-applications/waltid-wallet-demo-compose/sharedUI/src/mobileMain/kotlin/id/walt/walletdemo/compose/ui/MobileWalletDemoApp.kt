package id.walt.walletdemo.compose.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import id.walt.walletdemo.compose.logic.CredentialDetails
import id.walt.walletdemo.compose.logic.DemoReaderTrustSettingsController
import id.walt.walletdemo.compose.logic.WalletAuthState
import id.walt.walletdemo.compose.logic.WalletDemoController
import id.walt.walletdemo.compose.logic.WalletDemoProximityController
import id.walt.walletdemo.compose.logic.WalletDemoTab
import id.walt.walletdemo.compose.logic.WalletSessionState
import id.walt.walletdemo.compose.logic.toCredentialDetails
import id.walt.walletdemo.compose.ui.resources.*
import id.walt.walletdemo.compose.ui.components.*
import id.walt.walletdemo.compose.ui.screens.SettingsDestination
import id.walt.walletdemo.compose.ui.screens.SettingsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

/** Mobile-only host that adds the shared proximity journey to the regular Compose demo. */
@Composable
fun MobileWalletDemoApp(
    controller: WalletDemoController,
    proximityController: WalletDemoProximityController,
    readerTrustSettingsController: DemoReaderTrustSettingsController,
    branding: WalletDemoBranding = WalletDemoBranding(),
    onExternalFlowClosed: () -> Unit = {},
    onOpenExternalInApp: () -> Unit = {},
    externalBackground: WalletExternalBackground = WalletExternalBackground.Wallet,
) {
    val walletState by controller.state.collectAsState()
    val proximity by proximityController.state.collectAsState()
    val trustSettings by readerTrustSettingsController.state.collectAsState()
    val hostActions = rememberProximityHostActions()
    var showingConnectionOptions by rememberSaveable { mutableStateOf(false) }
    var hadNearbyTask by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(proximity.active) {
        if (proximity.active) hadNearbyTask = true
        else if (hadNearbyTask) {
            showingConnectionOptions = false
            hadNearbyTask = false
            if (walletState.externalFlow == null && walletState.presentationReview == null && !walletState.presentationCompleted) {
                controller.startNewPresentationFlow()
                controller.selectTab(WalletDemoTab.Credentials)
            }
        }
    }
    LaunchedEffect(proximity.canChangeConnectionOptions) {
        if (!proximity.canChangeConnectionOptions) showingConnectionOptions = false
    }
    val credentials = (walletState.session as? WalletSessionState.Ready)
        ?.credentials
        .orEmpty()
    val credentialDetailsById by produceState<Map<String, CredentialDetails>>(emptyMap(), credentials, proximity.active) {
        value = emptyMap()
        if (proximity.active) {
            value = withContext(Dispatchers.Default) {
                credentials.associate { credential -> credential.id to credential.toCredentialDetails() }
            }
        }
    }
    val qrVisible = walletState.selectedTab == WalletDemoTab.Present &&
        proximity.qrVisible
    val latestWalletState by rememberUpdatedState(walletState)
    val latestProximity by rememberUpdatedState(proximity)
    val latestDetails by rememberUpdatedState(credentialDetailsById)

    ProximityPlatformSessionEffect(
        active = proximity.active && (!proximity.isTerminal || proximity.preparingApproval),
        qrVisible = qrVisible,
        nfcReviewVisible = proximity.review != null,
        onInterrupted = proximityController::handleLifecycleInterruption,
    )
    LaunchedEffect(walletState.auth, proximity.active) {
        if (proximity.active && walletState.auth !is WalletAuthState.Unlocked) proximityController.dismiss()
    }
    LaunchedEffect(walletState.selectedTab, proximity.active) {
        if (proximity.active && walletState.selectedTab != WalletDemoTab.Present) proximityController.cancel()
    }
    LaunchedEffect(walletState.proximityApprovalMode, walletState.proximityTransportProfile) {
        proximityController.refreshPreferences()
    }
    WalletDemoAppHost(
        controller = controller,
        branding = branding,
        onExternalFlowClosed = onExternalFlowClosed,
        onOpenExternalInApp = onOpenExternalInApp,
        externalBackground = externalBackground,
        onStartProximityPresentation = (proximityController::start).takeUnless { trustSettings.loading },
        onResetWallet = { controller.resetWallet { proximityController.closeAndAwait() } },
        presentationContent = if (proximity.active) {
            {
                WalletReviewHost(WalletReviewPresentation.Sheet, proximity.canClose, proximityController::requestClose) {
                    val reduceMotion = LocalWalletVisualPreferences.current.reduceMotion
                    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
                    val pages = listOf("nearby") + if (showingConnectionOptions) listOf("options") else emptyList()
                    NavDisplay(pages, Modifier.heightIn(max = if (proximity.review != null || proximity.qrVisible || showingConnectionOptions) 720.dp else 520.dp),
                        onBack = { showingConnectionOptions = false }, entryDecorators = emptyList(),
                        transitionSpec = { walletNavigationMotion(true, reduceMotion, rtl) },
                        popTransitionSpec = { walletNavigationMotion(false, reduceMotion, rtl) },
                        predictivePopTransitionSpec = { _ -> walletNavigationMotion(false, reduceMotion, rtl) },
                    ) { page ->
                        NavEntry(page) {
                            val walletState = latestWalletState
                            val proximity = latestProximity
                            val credentialDetailsById = latestDetails
                            if (page == "options") SettingsScreen(
                                state = walletState,
                                initialDestination = SettingsDestination.Connection,
                                onBack = { showingConnectionOptions = false },
                                onShowDcApiPresentationPreviewChange = controller::setShowDcApiPresentationPreview,
                                onProximityTransportProfileChange = controller::setProximityTransportProfile,
                                onIdentityAction = controller::performIdentityAction,
                                onRefreshIdentityDetails = controller::refreshIdentityDetails,
                                onLock = controller::lock,
                                onResetWallet = { controller.resetWallet { proximityController.closeAndAwait() } },
                                onRequestSigningProtectionChange = controller::requestSigningProtectionChange,
                                onConfirmSigningProtectionChange = controller::confirmSigningProtectionChange,
                                onCancelSigningProtectionChange = controller::cancelSigningProtectionChange,
                            ) else WalletReviewNavigationHost(
                                requestKey = proximity.review?.reviewId?.toString() ?: "nearby",
                                reviewCredentialDetails = credentialDetailsById,
                                reviewClaimStatus = { id, item ->
                                    proximityDisclosureStatus(item, proximity.selections.filter { it.credentialId == id })
                                },
                                onClose = proximityController::requestClose.takeIf { proximity.canClose },
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                Column(Modifier.fillMaxSize()) {
                                    WalletScreenHeader("Share nearby", leading = {
                                        IconButton(proximityController::requestClose, enabled = proximity.canClose,
                                            modifier = Modifier.testTag(WalletUiTestTags.ProximityCancel)) {
                                            WalletIcon(WalletSymbol.Decline, "Close nearby sharing")
                                        }
                                    })
                                    Box(Modifier.weight(1f)) {
                                        WalletDemoProximityScreen(
                                            headerOwnsClose = true,
                                            state = proximity.copy(approvalMode = walletState.proximityApprovalMode),
                                            credentialDetailsById = credentialDetailsById,
                                            hostActions = hostActions.executor,
                                            hostActionForDisplay = hostActions::displayedAction,
                                            onSelectCredential = proximityController::selectCredential,
                                            onToggleElement = proximityController::toggleElement,
                                            onContinueAfterResponseChange = proximityController::setContinueAfterResponse,
                                            onApprove = { proximityController.approve() },
                                            onDecline = proximityController::decline,
                                            onRetry = proximityController::retryPrerequisites,
                                            onRemediate = proximityController::remediate,
                                            onCancel = proximityController::requestClose,
                                            onDismiss = proximityController::requestClose,
                                            onRestart = proximityController::restart,
                                            onShowEngagement = proximityController::showEngagement,
                                            onContinueWithAvailableConnection = proximityController::continueWithAvailableConnection,
                                            onApprovalModeChange = controller::setProximityApprovalMode,
                                            onConnectionOptions = ({ showingConnectionOptions = true }).takeIf {
                                                proximity.canChangeConnectionOptions || proximity.refreshingEngagement
                                            },
                                            onReviewRecentRequest = { proximityController.reviewRecentRequest() },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else null,
        readerTrustPolicySummary = stringResource(if (trustSettings.settings.readerPolicy == id.walt.wallet2.mobile.ProximityReaderPolicy.RequireTrusted)
            Res.string.reader_trust_require_a_trusted_reader else Res.string.reader_trust_allow_anonymous_or_untrusted_readers),
        readerTrustSettingsContent = {
            DemoReaderTrustSettings(
                controller = readerTrustSettingsController,
            )
        },
    )
}
