package id.walt.walletdemo.compose.ui

import androidx.compose.runtime.Composable

/** A modal's window focus is not a reliable app-activity signal on every renderer. */
@Composable
internal expect fun rememberScannerHostActive(): Boolean
