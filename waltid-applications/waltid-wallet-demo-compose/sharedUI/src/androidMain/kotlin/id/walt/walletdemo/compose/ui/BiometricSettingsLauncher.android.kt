package id.walt.walletdemo.compose.ui

import android.content.Intent
import android.provider.Settings
import android.hardware.biometrics.BiometricManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import id.walt.walletdemo.compose.logic.DemoBiometricAvailability

@Composable
internal actual fun rememberBiometricSettingsLauncher(availability: DemoBiometricAvailability): (() -> Unit)? {
    val context = LocalContext.current
    if (availability !in setOf(DemoBiometricAvailability.NotEnrolled, DemoBiometricAvailability.DeviceCredentialNotSet,
            DemoBiometricAvailability.Unavailable)) return null
    return {
        val intent = Intent(Settings.ACTION_BIOMETRIC_ENROLL).putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        // Some OEMs do not expose the enrollment activity. Security settings is the supported fallback.
        val destination = if (intent.resolveActivity(context.packageManager) != null) intent else Intent(Settings.ACTION_SECURITY_SETTINGS)
        context.startActivity(destination.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
