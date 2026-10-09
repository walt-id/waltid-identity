package id.walt.walletdemo.compose.ui

import androidx.compose.runtime.Composable
import id.walt.walletdemo.compose.logic.DemoBiometricAvailability

@Composable
internal actual fun rememberBiometricSettingsLauncher(availability: DemoBiometricAvailability): (() -> Unit)? = null
