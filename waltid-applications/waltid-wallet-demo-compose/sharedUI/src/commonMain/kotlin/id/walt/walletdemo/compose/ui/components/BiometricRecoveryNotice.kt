package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.DemoBiometricAvailability
import id.walt.walletdemo.compose.logic.DemoBiometricKind
import id.walt.walletdemo.compose.ui.rememberBiometricSettingsLauncher
import id.walt.walletdemo.compose.ui.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun BiometricRecoveryNotice(availability: DemoBiometricAvailability, kind: DemoBiometricKind,
    modifier: Modifier = Modifier, showSettingsAction: Boolean = true) {
    val openSettings = rememberBiometricSettingsLauncher(availability)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(biometricRecoveryText(availability, kind), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (showSettingsAction && openSettings != null) TextButton(openSettings,
            Modifier.testTag("wallet.biometricOpenSettings")) { Text(stringResource(Res.string.biometric_open_settings)) }
    }
}

@Composable
internal fun biometricRecoveryText(availability: DemoBiometricAvailability, kind: DemoBiometricKind): String =
    stringResource(when (availability) {
        DemoBiometricAvailability.Available -> Res.string.pin_biometric_ready
        DemoBiometricAvailability.NotEnrolled -> Res.string.biometric_not_enrolled
        DemoBiometricAvailability.DeviceCredentialNotSet -> Res.string.biometric_no_device_passcode
        DemoBiometricAvailability.LockedOut -> Res.string.biometric_device_locked_out
        DemoBiometricAvailability.Unsupported -> Res.string.biometric_unsupported
        DemoBiometricAvailability.Unavailable -> if (kind == DemoBiometricKind.FaceId)
            Res.string.biometric_face_id_unavailable else Res.string.biometric_recovery_unavailable
    })
