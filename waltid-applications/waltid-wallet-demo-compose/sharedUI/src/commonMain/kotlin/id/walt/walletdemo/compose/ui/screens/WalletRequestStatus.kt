package id.walt.walletdemo.compose.ui.screens

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import id.walt.walletdemo.compose.logic.*
import id.walt.walletdemo.compose.ui.WalletUiTestTags
import id.walt.walletdemo.compose.ui.components.*

/** A scanned or external request stays in its own journey while preparing or recovering. */
@Composable
internal fun WalletRequestStatus(
    state: WalletDemoUiState,
    onRetry: () -> Unit,
    retryEnabled: Boolean,
    modifier: Modifier = Modifier,
    fillViewport: Boolean = true,
) {
    val failure = state.operation as? WalletOperationState.Failed
    ReviewScaffold(modifier, fillViewport, actions = if (failure != null && retryEnabled) {
        { WalletActions(WalletAction("Try again", onRetry, testTag = "wallet.external.retry")) }
    } else null) {
        if (failure == null) CircularProgressIndicator()
        Text(failure?.message ?: if (state.isBusy) state.statusText else "Preparing request…",
            Modifier.testTag(WalletUiTestTags.Status))
    }
}
