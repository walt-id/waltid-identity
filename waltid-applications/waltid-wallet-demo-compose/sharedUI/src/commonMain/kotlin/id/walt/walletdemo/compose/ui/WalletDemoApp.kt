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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import id.walt.walletdemo.compose.logic.WalletAuthState
import id.walt.walletdemo.compose.logic.WalletDemoController
import id.walt.walletdemo.compose.logic.WalletDemoPresentationContinuation
import id.walt.walletdemo.compose.logic.isBusy
import id.walt.walletdemo.compose.ui.screens.PinScreen
import id.walt.walletdemo.compose.ui.screens.PinStorageUnavailableScreen
import id.walt.walletdemo.compose.ui.screens.WalletScreen

@Composable
fun WalletDemoApp(
    controller: WalletDemoController,
    branding: WalletDemoBranding = WalletDemoBranding(),
    onStartProximityPresentation: (() -> Unit)? = null,
) = WalletDemoAppHost(
    controller = controller,
    branding = branding,
    onStartProximityPresentation = onStartProximityPresentation,
)

/** Wallet shell with an internal slot for transport-specific presentation journey content. */
@Composable
internal fun WalletDemoAppHost(
    controller: WalletDemoController,
    branding: WalletDemoBranding = WalletDemoBranding(),
    onStartProximityPresentation: (() -> Unit)? = null,
    presentationContent: (@Composable () -> Unit)? = null,
    sharingSettingsContent: (@Composable () -> Unit)? = null,
) {
    val state by controller.state.collectAsState()
    PresentationContinuationEffect(
        continuation = state.pendingPresentationContinuation?.continuation,
        onCompleted = controller::completePresentationContinuation,
        onFailed = controller::failPresentationContinuation,
    )

    WalletDemoTheme(branding) {
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
                            signingProtectionMode = state.signingProtectionMode,
                            selectedSigningProtection = state.selectedSigningProtection,
                            biometricSigningAvailability = state.biometricSigningAvailability,
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
                        sharingSettingsContent = sharingSettingsContent,
                    )
                }
            }
        }
        state.signingProtectionWarning?.let { warning ->
            AlertDialog(
                onDismissRequest = controller::dismissSigningProtectionWarning,
                title = { Text("Biometric signing unavailable") },
                text = {
                    Text(
                        warning,
                        modifier = Modifier.testTag(WalletUiTestTags.SigningProtectionWarning),
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = controller::dismissSigningProtectionWarning,
                        modifier = Modifier.testTag(WalletUiTestTags.SigningProtectionWarningDismiss),
                    ) {
                        Text("OK")
                    }
                },
            )
        }
    }
}

@Composable
private fun PresentationContinuationEffect(
    continuation: WalletDemoPresentationContinuation?,
    onCompleted: () -> Unit,
    onFailed: (String) -> Unit,
) {
    when (continuation) {
        is WalletDemoPresentationContinuation.Url -> OpenPresentationContinuationUrlEffect(
            url = continuation.value,
            onCompleted = onCompleted,
            onFailed = onFailed,
        )
        is WalletDemoPresentationContinuation.FormPostHtml -> PlatformFormPostEffect(
            html = continuation.value,
            onCompleted = onCompleted,
            onFailed = onFailed,
        )
        null -> Unit
    }
}
