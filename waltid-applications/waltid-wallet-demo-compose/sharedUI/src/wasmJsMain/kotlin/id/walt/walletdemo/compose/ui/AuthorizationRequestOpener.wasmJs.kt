package id.walt.walletdemo.compose.ui

import androidx.compose.runtime.Composable
import kotlinx.browser.window

@Composable
internal actual fun rememberAuthorizationRequestOpener(): (String) -> Unit = { url ->
    window.location.assign(url)
}
