package id.walt.walletdemo.compose.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
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
    externalBackground: WalletExternalBackground = WalletExternalBackground.Wallet,
) {
    val walletState by controller.state.collectAsState()
    val proximity by proximityController.state.collectAsState()
    val trustSettings by readerTrustSettingsController.state.collectAsState()
    val hostActions = rememberProximityHostActions()
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
        externalBackground = externalBackground,
        onStartProximityPresentation = (proximityController::start).takeUnless { trustSettings.loading },
        onOpenSettings = proximityController::dismiss,
        onResetWallet = { controller.resetWallet { proximityController.closeAndAwait() } },
        presentationContent = if (proximity.active) {
            {
                WalletDemoProximityScreen(
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
                    onCancel = proximityController::cancel,
                    onDismiss = proximityController::dismiss,
                    onRestart = proximityController::restart,
                    onShowEngagement = proximityController::showEngagement,
                    onContinueWithAvailableConnection = proximityController::continueWithAvailableConnection,
                    onApprovalModeChange = controller::setProximityApprovalMode,
                    onReviewRecentRequest = { proximityController.reviewRecentRequest() },
                )
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
