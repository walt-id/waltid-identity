package id.walt.walletdemo.compose.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import id.walt.walletdemo.compose.logic.WalletAuthState
import id.walt.walletdemo.compose.logic.WalletDemoController
import id.walt.walletdemo.compose.logic.WalletDemoPresentationContinuation
import id.walt.walletdemo.compose.logic.isBusy
import id.walt.walletdemo.compose.logic.WalletSessionState
import id.walt.walletdemo.compose.logic.canDismissExternalFlow
import id.walt.walletdemo.compose.ui.screens.WalletExternalFlowScreen
import id.walt.walletdemo.compose.ui.screens.PinScreen
import id.walt.walletdemo.compose.ui.screens.BiometricSetupScreen
import id.walt.walletdemo.compose.ui.screens.PinStorageUnavailableScreen
import id.walt.walletdemo.compose.ui.screens.WalletScreen

@Composable
fun WalletDemoApp(
    controller: WalletDemoController,
    branding: WalletDemoBranding = WalletDemoBranding(),
    onStartProximityPresentation: (() -> Unit)? = null,
    onSignOut: (() -> Unit)? = null,
    resetWalletDescription: String? = null,
    allowWalletReset: Boolean = true,
    allowCredentialDelete: Boolean = true,
    serverSettingsContent: (@Composable () -> Unit)? = null,
) = WalletDemoAppHost(
    controller = controller,
    branding = branding,
    onStartProximityPresentation = onStartProximityPresentation,
    onSignOut = onSignOut,
    resetWalletDescription = resetWalletDescription,
    allowWalletReset = allowWalletReset,
    allowCredentialDelete = allowCredentialDelete,
    serverSettingsContent = serverSettingsContent,
)

/** Wallet shell with an internal slot for transport-specific presentation journey content. */
@Composable
internal fun WalletDemoAppHost(
    controller: WalletDemoController,
    branding: WalletDemoBranding = WalletDemoBranding(),
    onStartProximityPresentation: (() -> Unit)? = null,
    presentationContent: (@Composable () -> Unit)? = null,
    onExternalFlowClosed: () -> Unit = {},
    externalBackground: WalletExternalBackground = WalletExternalBackground.Wallet,
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
    val state by controller.state.collectAsState()
    LaunchedEffect(state.externalFlow, state.auth, state.session, state.isBusy) { controller.prepareExternalFlow() }
    val closeExternalFlow = { if (controller.closeExternalFlow()) onExternalFlowClosed() }
    PresentationContinuationEffect(
        continuation = state.pendingPresentationContinuation?.continuation,
        onCompleted = controller::completePresentationContinuation,
        onFailed = controller::failPresentationContinuation,
    )

    WalletDemoTheme(branding) {
        val appContent: @Composable () -> Unit = {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .exportTestTagsForPlatformAutomation(),
                color = MaterialTheme.colorScheme.background,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                        ),
                ) {
                    when (val auth = state.auth) {
                        is WalletAuthState.BiometricSetup -> BiometricSetupScreen(
                            auth, state.isAuthenticating, state.biometricUnlockAvailable,
                            controller::retryBiometricSetup, controller::continueWithoutBiometrics,
                        )
                        is WalletAuthState.PinEntry -> Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .safeDrawingPadding(),
                        ) {
                            PinScreen(
                                controller = controller,
                                auth = auth,
                                isBusy = state.isBusy,
                                biometricAvailable = state.biometricUnlockAvailable,
                            )
                        }
                        is WalletAuthState.StorageUnavailable -> Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .safeDrawingPadding(),
                        ) {
                            PinStorageUnavailableScreen(
                                controller = controller,
                                message = auth.message,
                            )
                        }
                        WalletAuthState.Unlocked -> WalletScreen(
                            controller = controller,
                            state = state,
                            onStartProximityPresentation = onStartProximityPresentation,
                            presentationContent = presentationContent,
                            readerTrustSettingsContent = readerTrustSettingsContent,
                            readerTrustPolicySummary = readerTrustPolicySummary,
                            onOpenSettings = onOpenSettings,
                            onResetWallet = onResetWallet,
                            onSignOut = onSignOut,
                            resetWalletDescription = resetWalletDescription,
                            allowWalletReset = allowWalletReset,
                            allowCredentialDelete = allowCredentialDelete,
                            serverSettingsContent = serverSettingsContent,
                        )
                    }
