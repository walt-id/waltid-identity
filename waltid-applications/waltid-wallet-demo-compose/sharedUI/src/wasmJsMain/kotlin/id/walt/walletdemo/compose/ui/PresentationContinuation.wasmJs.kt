package id.walt.walletdemo.compose.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
internal actual fun PlatformFormPostEffect(
    html: String,
    onCompleted: () -> Unit,
    onFailed: (String) -> Unit,
) {
    LaunchedEffect(html) {
        onFailed("form_post continuation delivery is only available in the mobile demos")
    }
}
