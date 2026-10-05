package id.walt.walletdemo.compose.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

internal data class WalletAction(
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val testTag: String? = null,
    val icon: WalletSymbol? = null,
)

/** Actions stay trailing and wrap at large text sizes instead of truncating consent labels. */
@Composable
internal fun WalletActionBar(
    primary: WalletAction,
    secondary: WalletAction? = null,
    modifier: Modifier = Modifier,
) {
    WalletFooter(modifier, actions = { WalletActions(primary, secondary) })
}
