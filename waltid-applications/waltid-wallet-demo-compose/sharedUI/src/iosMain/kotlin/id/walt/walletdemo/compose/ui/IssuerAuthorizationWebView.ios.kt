package id.walt.walletdemo.compose.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun IssuerAuthorizationWebView(
    authorizationUrl: String,
    redirectUri: String,
    onRedirect: (callbackUri: String) -> Unit,
    modifier: Modifier,
) {
    // CREATE_CREDENTIAL in-tray auth is Android-only for now.
}
