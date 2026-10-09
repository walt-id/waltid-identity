package id.walt.walletdemo.compose.ui

import androidx.compose.runtime.Composable
import id.walt.walletdemo.compose.logic.DemoBiometricAvailability

/** Opens only a supported system settings surface; never resets permission or enrollment. */
@Composable
internal expect fun rememberBiometricSettingsLauncher(availability: DemoBiometricAvailability): (() -> Unit)?
