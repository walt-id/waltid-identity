package id.walt.walletdemo.compose.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.*
import id.walt.walletdemo.compose.ui.rememberAuthorizationRequestOpener
import id.walt.walletdemo.compose.ui.components.WalletScreenHeader

/** Wallet-owned external surface. Platform windows decide what can appear behind this sheet. */
@Composable
internal fun WalletExternalFlowScreen(
    controller: WalletDemoController,
    state: WalletDemoUiState,
    onClose: () -> Unit,
) {
    val openAuthorization = rememberAuthorizationRequestOpener()
    LaunchedEffect(state.authorizationRequestUrl) {
        state.authorizationRequestUrl?.let { openAuthorization(it); controller.authorizationRequestOpened() }
    }
    Column(Modifier.fillMaxWidth().testTag("wallet.external.flow")) {
        WalletScreenHeader(if (state.externalFlow?.tab == WalletDemoTab.Receive) "Receive credentials" else "Share credentials",
            titleTag = "wallet.external.title") {
            IconButton(onClick = onClose, enabled = state.canDismissExternalFlow,
                modifier = Modifier.testTag("wallet.external.close")) { Icon(Icons.Default.Close, "Close request") }
        }
        if (state.externalFlow is WalletExternalFlow.UnavailableCallback) {
            Text("The original receiving session is no longer available. Check your wallet before starting again.",
                Modifier.padding(20.dp).testTag("wallet.external.unavailable"))
        } else WalletFlowContent(controller, state, onDone = onClose,
            fillViewport = false, modifier = Modifier.weight(1f, fill = false))
    }
}
