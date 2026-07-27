package id.walt.walletdemo.compose.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalUriHandler

@Composable
internal fun OpenPresentationContinuationUrlEffect(
    url: String,
    onCompleted: () -> Unit,
    onFailed: (String) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(url) {
        runCatching { uriHandler.openUri(url) }
            .onSuccess { onCompleted() }
            .onFailure { onFailed(it.message ?: "No application can open the verifier response") }
    }
}

@Composable
internal expect fun PlatformFormPostEffect(
    html: String,
    onCompleted: () -> Unit,
    onFailed: (String) -> Unit,
)
