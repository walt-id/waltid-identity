package id.walt.walletdemo.compose.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.ui.WalletUiTestTags
import id.walt.walletdemo.compose.ui.components.WalletAction
import id.walt.walletdemo.compose.ui.components.WalletActions

@Composable
internal fun WalletOpeningScreen(failure: String?, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally) {
        if (failure == null) {
            CircularProgressIndicator(Modifier.testTag(WalletUiTestTags.CredentialsLoading))
            Text("Opening wallet…", style = MaterialTheme.typography.bodyLarge)
        } else {
            Text("Could not open your wallet", style = MaterialTheme.typography.titleLarge)
            Text(failure, Modifier.testTag(WalletUiTestTags.Status), style = MaterialTheme.typography.bodyMedium)
            WalletActions(primary = WalletAction("Retry opening wallet", onRetry, testTag = "wallet.openingRetry"))
        }
    }
}
