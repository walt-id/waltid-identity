package id.walt.walletdemo.compose.ui

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import platform.Foundation.NSError
import platform.Foundation.NSURLErrorCancelled
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKWebView
import platform.darwin.NSObject

@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
@Composable
internal actual fun PlatformFormPostEffect(
    html: String,
    onCompleted: () -> Unit,
    onFailed: (String) -> Unit,
) {
    val currentOnCompleted by rememberUpdatedState(onCompleted)
    val currentOnFailed by rememberUpdatedState(onFailed)
    val navigationDelegate = remember(html) {
        FormPostNavigationDelegate(
            onCompleted = { currentOnCompleted() },
            onFailed = { currentOnFailed(it) },
        )
    }
    UIKitView(
        factory = {
            WKWebView().apply {
                this.navigationDelegate = navigationDelegate
                loadHTMLString(html, baseURL = null)
            }
        },
        modifier = Modifier.size(1.dp).alpha(0f),
        onRelease = { webView ->
            webView.stopLoading()
            webView.navigationDelegate = null
        },
    )
}

private class FormPostNavigationDelegate(
    private val onCompleted: () -> Unit,
    private val onFailed: (String) -> Unit,
) : NSObject(), WKNavigationDelegateProtocol {
    private var submittedNavigation: WKNavigation? = null
    private var finished = false

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didStartProvisionalNavigation: WKNavigation?) {
        if (webView.URL?.absoluteString?.let { it != "about:blank" && !it.startsWith("data:") } == true) {
            submittedNavigation = didStartProvisionalNavigation
        }
    }

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
        if (submittedNavigation != null && submittedNavigation === didFinishNavigation && !finished) {
            finished = true
            onCompleted()
        }
    }

    @ObjCSignatureOverride
    override fun webView(
        webView: WKWebView,
        didFailProvisionalNavigation: WKNavigation?,
        withError: NSError,
    ) {
        if (withError.code != NSURLErrorCancelled) {
            fail(withError)
        }
    }

    @ObjCSignatureOverride
    override fun webView(
        webView: WKWebView,
        didFailNavigation: WKNavigation?,
        withError: NSError,
    ) = fail(withError)

    private fun fail(error: NSError) {
        if (!finished) {
            finished = true
            onFailed(error.localizedDescription)
        }
    }
}
