package id.walt.walletdemo.compose.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import id.walt.walletdemo.compose.logic.WalletDateTime
import id.walt.walletdemo.compose.logic.WalletDateTimeText
import kotlin.time.Instant

internal val LocalWalletTimeFormatter = compositionLocalOf<(Instant) -> WalletDateTimeText> { { WalletDateTime.format(it) } }

/** Format at render time; one settings observer per wallet host refreshes all visible timestamps. */
@Composable
internal fun WalletInstantText(instant: Instant, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.bodyLarge) {
    Text(LocalWalletTimeFormatter.current(instant).fullText, modifier = modifier,
        style = style)
}

@Composable
internal expect fun rememberWalletTimeFormatter(): (Instant) -> WalletDateTimeText
