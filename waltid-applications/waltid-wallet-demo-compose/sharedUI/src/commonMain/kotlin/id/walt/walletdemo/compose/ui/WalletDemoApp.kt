package id.walt.walletdemo.compose.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import id.walt.walletdemo.compose.logic.WalletAuthState
import id.walt.walletdemo.compose.logic.WalletDemoController
import id.walt.walletdemo.compose.logic.isBusy
import id.walt.walletdemo.compose.ui.screens.PinScreen
import id.walt.walletdemo.compose.ui.screens.ReceiveCredential
import id.walt.walletdemo.compose.ui.screens.WalletScreen
private enum class WalletRoute { Main, Receive }

@Composable
fun WalletDemoApp(controller: WalletDemoController) {
    val state by controller.state.collectAsState()

    var route by remember { mutableStateOf(WalletRoute.Main) }

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
                    is WalletAuthState.Setup,
                    is WalletAuthState.Login,
                    -> PinScreen(
                        controller = controller,
                        auth = auth,
                        isBusy = state.isBusy,
                    )
                    WalletAuthState.Unlocked -> when (route) {
                        WalletRoute.Main -> WalletScreen(
                            controller = controller,
                            state = state,
                            onReceiveClick = { route = WalletRoute.Receive },
                        )
                        WalletRoute.Receive -> ReceiveCredential(
                            controller, state,
                            onBack = { route = WalletRoute.Main }
                        )
                    }
                }
            }
        }
    }
}
