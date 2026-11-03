package id.walt.walletdemo.compose.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import id.walt.walletdemo.compose.logic.WalletDateTime
import kotlin.time.Instant

internal val LocalWalletTimeFormatter = compositionLocalOf<(Instant) -> String> { { WalletDateTime.format(it) } }

/** Format at render time; one settings observer per wallet host refreshes all visible timestamps. */
@Composable
internal fun walletInstantText(instant: Instant): String = LocalWalletTimeFormatter.current(instant)

@Composable
internal expect fun rememberWalletTimeFormatter(): (Instant) -> String
