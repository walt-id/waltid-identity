package id.walt.walletdemo.compose.ui

import androidx.compose.runtime.Composable

/** A launch result only; credential validation, consent and exchange belong to the system. */
internal enum class FidoQrHandoffResult { Opened, InvalidCode, Unavailable, Blocked }

@Composable
internal expect fun rememberFidoQrHandoff(): ((String) -> FidoQrHandoffResult)?
