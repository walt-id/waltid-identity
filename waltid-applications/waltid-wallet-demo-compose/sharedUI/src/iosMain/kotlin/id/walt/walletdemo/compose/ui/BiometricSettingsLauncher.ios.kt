package id.walt.walletdemo.compose.ui

import androidx.compose.runtime.Composable
import id.walt.walletdemo.compose.logic.DemoBiometricAvailability
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString

@Composable
internal actual fun rememberBiometricSettingsLauncher(availability: DemoBiometricAvailability): (() -> Unit)? =
    if (availability in setOf(DemoBiometricAvailability.NotEnrolled, DemoBiometricAvailability.DeviceCredentialNotSet,
            DemoBiometricAvailability.Unavailable)) ({
        NSURL.URLWithString(UIApplicationOpenSettingsURLString)?.let {
            UIApplication.sharedApplication.openURL(it, emptyMap<Any?, Any?>(), null)
        }
    }) else null
