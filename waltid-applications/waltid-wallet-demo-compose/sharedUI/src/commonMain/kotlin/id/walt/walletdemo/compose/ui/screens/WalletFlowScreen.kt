package id.walt.walletdemo.compose.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.*
import id.walt.walletdemo.compose.ui.components.WalletScreenHeader
import id.walt.walletdemo.compose.ui.components.WalletReviewNavigationHost

/** Shared request page for scanned links, external links and pending issuance recovery. */
@Composable
internal fun WalletFlowScreen(
    controller: WalletDemoController,
    state: WalletDemoUiState,
    onClose: () -> Unit,
) {
    WalletReviewNavigationHost(
        requestKey = "${state.selectedTab}:${state.receiveNavigationResetKey}:${state.presentationNavigationResetKey}",
        offer = state.offerPreview,
        savedCredentials = state.receivedCredentials(),
        enabled = !state.isBusy,
        onClose = onClose.takeIf { state.canDismissExternalFlow },
    ) {
        Column(Modifier.fillMaxSize().testTag("wallet.external.flow")) {
            WalletScreenHeader(if (state.selectedTab == WalletDemoTab.Receive) "Receive credentials" else "Share credentials",
                titleTag = "wallet.external.title") {
                IconButton(onClick = onClose, enabled = state.canDismissExternalFlow,
                    modifier = Modifier.testTag("wallet.external.close")) { Icon(Icons.Default.Close, "Close request") }
            }
            if (state.externalFlow is WalletExternalFlow.UnavailableCallback) {
                Text("The original receiving session is no longer available. Check your wallet before starting again.",
                    Modifier.padding(20.dp).testTag("wallet.external.unavailable"))
            } else WalletFlowContent(controller, state, onDone = onClose,
                modifier = Modifier.weight(1f))
        }
    }
}
