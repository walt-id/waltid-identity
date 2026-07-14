package id.walt.walletdemo.compose.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import id.walt.walletdemo.compose.logic.CredentialDetails
import id.walt.walletdemo.compose.ui.WalletRoute

@Composable
internal fun WalletTabNavDisplay(
    backStack: SnapshotStateList<WalletRoute>,
    details: List<CredentialDetails>,
    modifier: Modifier,
    root: @Composable () -> Unit,
) {
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        entryProvider = entryProvider {
            entry<WalletRoute.Root> {
                root()
            }
            entry<WalletRoute.CredentialDetails> { route ->
                val selectedDetails = details.firstOrNull { it.summary.id == route.detailsId }
                if (selectedDetails == null) {
                    root()
                } else {
                    CredentialDetailsScreen(
                        details = selectedDetails,
                        onBack = { backStack.removeLastOrNull() },
                    )
                }
            }
        },
    )
}

internal fun SnapshotStateList<WalletRoute>.pushDetails(detailsId: String) {
    removeAll { it is WalletRoute.CredentialDetails }
    add(WalletRoute.CredentialDetails(detailsId))
}

internal fun SnapshotStateList<WalletRoute>.resetToRoot() {
    clear()
    add(WalletRoute.Root)
}
