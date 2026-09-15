package id.walt.crypto2.signum

import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL

internal val SignumAuthenticationPolicy.UserPresence.androidPromptAuthenticators: Int
    get() = (if (biometric) BIOMETRIC_STRONG else 0) or (if (deviceCredential) DEVICE_CREDENTIAL else 0)

// Stable Signum always sets a negative button. AndroidX treats an empty label as absent,
// as required when the system supplies the device-credential action instead.
internal val SignumAuthenticationPolicy.UserPresence.androidPromptCancelText: String
    get() = if (deviceCredential) "" else cancelText
