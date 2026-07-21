package id.walt.walletdemo.compose.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import id.walt.walletdemo.compose.logic.WalletAuthState
import id.walt.walletdemo.compose.logic.WalletDemoController
import id.walt.walletdemo.compose.logic.WalletDemoPresentationContinuation
import id.walt.walletdemo.compose.logic.isBusy
import id.walt.walletdemo.compose.ui.screens.PinScreen
import id.walt.walletdemo.compose.ui.screens.PinStorageUnavailableScreen
import id.walt.walletdemo.compose.ui.screens.WalletScreen

@Composable
fun WalletDemoApp(controller: WalletDemoController) {
    val state by controller.state.collectAsState()
    PresentationContinuationEffect(
        continuation = state.pendingPresentationContinuation?.continuation,
        onCompleted = controller::completePresentationContinuation,
        onFailed = controller::failPresentationContinuation,
    )

    MaterialTheme {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .exportTestTagsForPlatformAutomation(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding(),
            ) {
                when (val auth = state.auth) {
                    is WalletAuthState.PinEntry -> PinScreen(
                        controller = controller,
                        auth = auth,
                        isBusy = state.isBusy,
                    )
                    is WalletAuthState.StorageUnavailable -> PinStorageUnavailableScreen(
                        controller = controller,
                        message = auth.message,
                    )
                    WalletAuthState.Unlocked -> WalletScreen(controller, state)
                }
            }
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
