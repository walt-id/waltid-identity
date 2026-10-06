package id.walt.walletdemo.compose.ui.screens

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import id.walt.walletdemo.compose.logic.*
import id.walt.walletdemo.compose.ui.WalletUiTestTags
import id.walt.walletdemo.compose.ui.components.*

/** Preparation and terminal states retain the external request without exposing an editable URL. */
@Composable
internal fun ExternalFlowStatus(
    state: WalletDemoUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    fillViewport: Boolean = true,
) {
    ReviewScaffold(modifier, fillViewport, actions = if (state.operation is WalletOperationState.Failed) {
        { WalletActions(WalletAction("Try again", onRetry, testTag = "wallet.external.retry")) }
    } else null) {
        if (state.isBusy || state.externalFlow is WalletExternalFlow.Pending) CircularProgressIndicator()
        Text(state.statusText.ifBlank { "Preparing request…" }, Modifier.testTag(WalletUiTestTags.Status))

    }
}
