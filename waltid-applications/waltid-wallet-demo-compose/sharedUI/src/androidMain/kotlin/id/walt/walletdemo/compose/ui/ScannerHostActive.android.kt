package id.walt.walletdemo.compose.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalWindowInfo

@Composable
internal actual fun rememberScannerHostActive(): Boolean = LocalWindowInfo.current.isWindowFocused
