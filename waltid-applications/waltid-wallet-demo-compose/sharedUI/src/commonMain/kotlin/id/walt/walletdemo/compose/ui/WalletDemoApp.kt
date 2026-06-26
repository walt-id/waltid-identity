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
import id.walt.walletdemo.compose.logic.WalletDemoController
import id.walt.walletdemo.compose.ui.screens.PinScreen
import id.walt.walletdemo.compose.ui.screens.WalletScreen

@Composable
fun WalletDemoApp(controller: WalletDemoController) {
    val state by controller.state.collectAsState()

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
                if (state.isUnlocked) {
                    WalletScreen(controller, state)
                } else {
                    PinScreen(controller, state)
                }
            }
        }
    }
}
