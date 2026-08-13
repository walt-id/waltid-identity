package id.walt.walletdemo.compose.ui

import androidx.compose.runtime.Composable

/** iOS has no system back gesture to intercept; in-screen navigation supplies its own control. */
@Composable
internal actual fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit
