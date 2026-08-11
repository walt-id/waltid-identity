package id.walt.walletdemo.compose.ui

import android.annotation.SuppressLint
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun IssuerAuthorizationWebView(
    authorizationUrl: String,
    redirectUri: String,
    onRedirect: (callbackUri: String) -> Unit,
    modifier: Modifier,
) {
    val handled = remember(authorizationUrl, redirectUri) { booleanArrayOf(false) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                clearCache(true)
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): Boolean {
                        val url = request?.url?.toString() ?: return false
                        return consumeRedirectIfMatched(url)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                        return url != null && consumeRedirectIfMatched(url)
                    }

                    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                        if (url != null) consumeRedirectIfMatched(url)
                    }

                    private fun consumeRedirectIfMatched(url: String): Boolean {
                        if (handled[0] || !matchesAuthorizationRedirect(url, redirectUri)) return false
                        handled[0] = true
                        onRedirect(url)
                        return true
                    }
                }
            }
        },
        update = { webView ->
            if (webView.url != authorizationUrl && !handled[0]) {
                webView.loadUrl(authorizationUrl)
            }
        },
    )
}

internal fun matchesAuthorizationRedirect(url: String, redirectUri: String): Boolean {
    if (url.startsWith(redirectUri)) return true
    val redirect = Uri.parse(redirectUri)
    val incoming = Uri.parse(url)
    val sameScheme = redirect.scheme != null &&
        redirect.scheme.equals(incoming.scheme, ignoreCase = true)
    if (!sameScheme) return false
    // Custom-scheme redirects like openid://?code=... often have no host/path.
    val redirectHasAuthority = !redirect.host.isNullOrEmpty() || !redirect.path.isNullOrEmpty()
    return !redirectHasAuthority || url.startsWith(redirectUri)
}
