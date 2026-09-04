package id.walt.walletdemo.compose.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler

@Composable
internal actual fun rememberAuthorizationRequestOpener(): (String) -> Unit {
    val uriHandler = LocalUriHandler.current
    return remember(uriHandler) {
        { url: String -> uriHandler.openUri(url) }
    }
}
