package id.walt.walletdemo.compose.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import id.walt.walletdemo.compose.logic.WalletDemoController
import id.walt.walletdemo.compose.logic.WalletDemoUiState
import id.walt.walletdemo.compose.logic.WalletInteractionKind
import id.walt.walletdemo.compose.logic.WalletSessionState
import id.walt.walletdemo.compose.logic.toCredentialDetails
import id.walt.walletdemo.compose.ui.WalletRoute
import id.walt.walletdemo.compose.ui.components.WalletInteractionSheet

@Composable
internal fun WalletScreen(controller: WalletDemoController, state: WalletDemoUiState) {
    val ready = state.session as? WalletSessionState.Ready
    val credentials = ready?.credentials.orEmpty()
    val backStack = remember { mutableStateListOf<WalletRoute>(WalletRoute.Root) }
    val details = (
        credentials.map { it.toCredentialDetails() } +
            state.presentationPreview?.credentialOptions.orEmpty().map { it.toCredentialDetails() }
        ).distinctBy { it.summary.id }

    Scaffold(
        topBar = {
            WalletHeader(
                did = ready?.did,
                state = state,
                onLock = controller::lock,
            )
        },
    ) { contentPadding ->
        WalletTabNavDisplay(
            backStack = backStack,
            details = details,
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            root = {
                WalletHomeScreen(
                    credentials = credentials,
                    onReceive = controller::startReceiveCapture,
                    onPresent = controller::startPresentCapture,
                    onCredentialClick = { backStack.pushDetails(it) },
                )
            },
        )
    }

    WalletInteractionSheet(
        controller = controller,
        state = state,
        onCredentialClick = { backStack.pushDetails(it) },
    )

    state.replacementRequest?.let { replacement ->
        AlertDialog(
            onDismissRequest = controller::keepCurrentRequest,
            title = { Text("Replace current request?") },
            text = {
                Text(
                    if (replacement.kind == WalletInteractionKind.Receive) {
                        "A new credential offer arrived. Replacing the current interaction will cancel it locally."
                    } else {
                        "A new presentation request arrived. Replacing the current interaction will cancel it locally."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = controller::replaceCurrentRequest) { Text("Replace") }
            },
            dismissButton = {
                TextButton(onClick = controller::keepCurrentRequest) { Text("Keep current") }
            },
        )
    }
}
