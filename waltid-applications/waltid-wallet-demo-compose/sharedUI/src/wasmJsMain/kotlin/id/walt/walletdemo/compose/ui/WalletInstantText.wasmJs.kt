package id.walt.walletdemo.compose.ui

import androidx.compose.runtime.*
import id.walt.walletdemo.compose.logic.WalletDateTime
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.events.Event
import kotlin.time.Instant

@Composable
internal actual fun rememberWalletTimeFormatter(): (Instant) -> String {
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val refresh: (Event) -> Unit = { revision++ }
        window.addEventListener("focus", refresh)
        window.addEventListener("languagechange", refresh)
        document.addEventListener("visibilitychange", refresh)
        onDispose {
            window.removeEventListener("focus", refresh)
            window.removeEventListener("languagechange", refresh)
            document.removeEventListener("visibilitychange", refresh)
        }
    }
    return remember(revision) { { instant: Instant -> WalletDateTime.format(instant) } }
}
