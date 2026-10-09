package id.walt.walletdemo.compose.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.ui.components.ReviewScaffold
import id.walt.walletdemo.compose.ui.components.WalletSymbol
import id.walt.walletdemo.compose.ui.components.WalletIcon
import id.walt.walletdemo.compose.ui.components.WalletScreenHeader

/** Preparing and recoverable failure surfaces share the provider review's host and pinned actions. */
@Composable
fun WalletProviderStatusScreen(
    title: String,
    message: String? = null,
    enabled: Boolean = true,
    onClose: () -> Unit,
    onDismiss: () -> Unit,
) {
    WalletReviewHost(dismissEnabled = enabled, onDismiss = onDismiss) {
        ReviewScaffold(fillViewport = false, header = {
            WalletScreenHeader(title) {
                IconButton(onClose, enabled = enabled) { WalletIcon(WalletSymbol.Decline, "Close request") }
            }
        }, feedback = message?.let { { Text(it, color = MaterialTheme.colorScheme.error) } }) {
            Column(Modifier.fillMaxWidth().testTag("wallet.provider.status"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (message == null) {
                    CircularProgressIndicator(Modifier.size(32.dp).align(Alignment.CenterHorizontally))
                }
            }
        }
    }
}
