package id.walt.walletdemo.compose.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Platform WebView that loads an OpenID4VCI authorization URL and reports the redirect callback.
 *
 * Used by CREATE_CREDENTIAL fulfillment so Keycloak / issuer sign-in stays inside the system tray
 * sheet instead of launching an external browser.
 */
@Composable
expect fun IssuerAuthorizationWebView(
    authorizationUrl: String,
    redirectUri: String,
    onRedirect: (callbackUri: String) -> Unit,
    modifier: Modifier = Modifier,
)
