package id.walt.walletdemo.compose.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal expect fun PlatformQrScanner(
    modifier: Modifier,
    onCodeScanned: (String) -> Unit,
)
