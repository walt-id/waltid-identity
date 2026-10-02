package id.walt.walletdemo.compose.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.ui.components.ReviewScaffold
import id.walt.walletdemo.compose.ui.components.WalletAction
import id.walt.walletdemo.compose.ui.components.WalletActions
import id.walt.walletdemo.compose.ui.components.WalletSymbol

/** Preparing and recoverable failure surfaces share the provider review's host and pinned actions. */
@Composable
fun WalletProviderStatusScreen(
    title: String,
    message: String? = null,
    enabled: Boolean = true,
    onClose: () -> Unit,
    onDismiss: () -> Unit,
) {
    WalletReviewHost(WalletReviewPresentation.Sheet, dismissEnabled = enabled, onDismiss = onDismiss) { fill ->
        ReviewScaffold(fillViewport = fill, actions = {
            WalletActions(WalletAction(if (message == null) "Cancel" else "Close", onClose, enabled = enabled, icon = WalletSymbol.Decline))
        }) {
            Column(Modifier.fillMaxWidth().testTag("wallet.provider.status"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                if (message == null) {
                    CircularProgressIndicator(Modifier.size(32.dp).align(Alignment.CenterHorizontally))
                } else Text(message, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
