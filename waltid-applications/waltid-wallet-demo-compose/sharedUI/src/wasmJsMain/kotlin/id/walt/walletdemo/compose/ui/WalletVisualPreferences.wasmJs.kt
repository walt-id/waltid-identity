@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package id.walt.walletdemo.compose.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.browser.window
import org.w3c.dom.events.Event

@Composable
internal actual fun rememberWalletVisualPreferences(): WalletVisualPreferences {
    val motion = remember { window.matchMedia("(prefers-reduced-motion: reduce)") }
    val transparency = remember { window.matchMedia("(prefers-reduced-transparency: reduce)") }
    val contrast = remember { window.matchMedia("(prefers-contrast: more)") }
    fun current() = WalletVisualPreferences(motion.matches, transparency.matches || contrast.matches)
    var preferences by remember { mutableStateOf(current()) }
    DisposableEffect(motion, transparency, contrast) {
        val listener: (Event) -> Unit = { preferences = current() }
        val queries = listOf(motion, transparency, contrast)
        queries.forEach { it.addEventListener("change", listener) }
        onDispose { queries.forEach { it.removeEventListener("change", listener) } }
    }
    return preferences
}
