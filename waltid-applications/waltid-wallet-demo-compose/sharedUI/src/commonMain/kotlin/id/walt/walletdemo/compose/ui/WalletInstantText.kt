package id.walt.walletdemo.compose.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.WalletDateTime
import id.walt.walletdemo.compose.logic.WalletDateTimeText
import kotlin.time.Instant

internal val LocalWalletTimeFormatter = compositionLocalOf<(Instant) -> WalletDateTimeText> { { WalletDateTime.format(it) } }

/** Format at render time; one settings observer per wallet host refreshes all visible timestamps. */
@Composable
internal fun WalletInstantText(instant: Instant, modifier: Modifier = Modifier) {
    val text = LocalWalletTimeFormatter.current(instant)
    Column(modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text.date, style = MaterialTheme.typography.bodyLarge)
        Text(text.supportingText, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal expect fun rememberWalletTimeFormatter(): (Instant) -> WalletDateTimeText
