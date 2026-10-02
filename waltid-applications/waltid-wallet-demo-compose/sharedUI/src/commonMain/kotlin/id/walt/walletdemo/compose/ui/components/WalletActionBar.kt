package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        WalletActions(primary, secondary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
    }
}
