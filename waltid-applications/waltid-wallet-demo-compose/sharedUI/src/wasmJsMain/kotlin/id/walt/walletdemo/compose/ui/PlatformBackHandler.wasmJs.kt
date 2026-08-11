package id.walt.walletdemo.compose.ui

import androidx.compose.runtime.Composable

/** The web demo has no platform back gesture wired into the composition. */
@Composable
internal actual fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit
