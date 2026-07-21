package id.walt.walletdemo.compose.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal actual fun PlatformFormPostEffect(
    html: String,
    onCompleted: () -> Unit,
    onFailed: (String) -> Unit,
) {
    val currentOnCompleted by rememberUpdatedState(onCompleted)
    val currentOnFailed by rememberUpdatedState(onFailed)
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                webViewClient = FormPostWebViewClient(
                    onCompleted = { currentOnCompleted() },
                    onFailed = { currentOnFailed(it) },
                )
                loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
        },
        modifier = Modifier.size(1.dp).alpha(0f),
        onRelease = { webView ->
            webView.stopLoading()
            webView.webViewClient = WebViewClient()
            webView.destroy()
        },
    )
}

internal class FormPostWebViewClient(
    private val onCompleted: () -> Unit,
    private val onFailed: (String) -> Unit,
) : WebViewClient() {
    private var submittedUrl: String? = null
    private var finished = false

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        if (!url.isNullOrBlank() && url != "about:blank" && !url.startsWith("data:")) {
            submittedUrl = url
        }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        if (submittedUrl == url && !finished) {
            finished = true
            onCompleted()
        }
    }

    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
        if (request?.isForMainFrame == true && !finished) {
            finished = true
            onFailed(error?.description?.toString() ?: "Could not submit the verifier response")
        }
    }
}
