package id.walt.walletdemo.compose.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.WalletDemoController
import id.walt.walletdemo.compose.ui.LocalWalletDemoBranding
import id.walt.walletdemo.compose.ui.components.*

@Composable
internal fun PinStorageUnavailableScreen(controller: WalletDemoController, message: String) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(LocalWalletDemoBranding.current.appTitle, style = MaterialTheme.typography.headlineSmall)
        Text("PIN storage unavailable", style = MaterialTheme.typography.headlineMedium)
        SettingsNotice("$message. The wallet remains locked.", error = true)
        WalletActions(primary = WalletAction("Retry", controller::retryPinStorage,
            testTag = "wallet.pinStorageRetryButton", icon = WalletSymbol.Retry))
    }
}
