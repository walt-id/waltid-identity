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
import id.walt.walletdemo.compose.ui.components.WalletReviewNavigationHost

/** Wallet-owned external surface. Platform windows decide what can appear behind this sheet. */
@Composable
internal fun WalletExternalFlowScreen(
    controller: WalletDemoController,
    state: WalletDemoUiState,
    onClose: () -> Unit,
    fillViewport: Boolean = false,
    onOpenInApp: (() -> Unit)? = null,
) {
    val openAuthorization = rememberAuthorizationRequestOpener()
    LaunchedEffect(state.authorizationRequestUrl) {
        state.authorizationRequestUrl?.let { openAuthorization(it); controller.authorizationRequestOpened() }
    }
    WalletReviewNavigationHost(
        requestKey = "external:${state.receiveNavigationResetKey}:${state.presentationNavigationResetKey}",
        offer = state.offerPreview, sharingOptions = state.presentationPreview?.credentialOptions.orEmpty(),
        savedCredentials = state.receivedCredentials(),
        selectedCredentials = state.selectedPresentationCredentialOptions,
        selectedDisclosures = state.selectedPresentationDisclosureOptions,
        enabled = !state.isBusy, onToggleDisclosure = controller::togglePresentationDisclosure,
        onClose = onClose.takeIf { state.canDismissExternalFlow },
    ) {
    Column(Modifier.fillMaxWidth().then(if (fillViewport) Modifier.fillMaxHeight() else Modifier).testTag("wallet.external.flow")) {
        WalletScreenHeader(if (state.externalFlow?.tab == WalletDemoTab.Receive) "Receive credentials" else "Share credentials",
            titleTag = "wallet.external.title") {
            onOpenInApp?.let { open ->
                TextButton(onClick = open, modifier = Modifier.testTag("wallet.external.openInApp")) { Text("Open in app") }
            }
            IconButton(onClick = onClose, enabled = state.canDismissExternalFlow,
                modifier = Modifier.testTag("wallet.external.close")) { Icon(Icons.Default.Close, "Close request") }
        }
        if (state.externalFlow is WalletExternalFlow.UnavailableCallback) {
            Text("The original receiving session is no longer available. Check your wallet before starting again.",
                Modifier.padding(20.dp).testTag("wallet.external.unavailable"))
        } else WalletFlowContent(controller, state, onDone = onClose,
            fillViewport = fillViewport, modifier = Modifier.weight(1f, fill = fillViewport))
    }
    }
}
