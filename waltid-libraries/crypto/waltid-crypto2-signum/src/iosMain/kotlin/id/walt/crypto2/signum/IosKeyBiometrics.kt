@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package id.walt.crypto2.signum

import kotlinx.cinterop.*
import platform.Foundation.NSError
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics

/**
 * Do not query a biometric-only Keychain entry while biometrics are unavailable. On affected iOS
 * versions, that query can leave an otherwise enrollment-tolerant entry persistently unavailable.
 * This non-interactive check is not authorization; native access control still governs key use.
 */
internal fun requireIosKeyBiometrics(
    alias: String,
    policy: SignumKeyPolicy,
    availabilityFailure: () -> Throwable? = ::iosBiometricAvailabilityFailure,
) {
    val authentication = policy.authentication as? SignumAuthenticationPolicy.UserPresence ?: return
    if (!authentication.biometric || authentication.deviceCredential) return
    availabilityFailure()?.let { throw SignumKeyUnavailableException(alias, it) }
}

private fun iosBiometricAvailabilityFailure(): Throwable? {
    val context = LAContext()
    return try {
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            error.value = null
            if (!context.canEvaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, error.ptr)) {
                error.value?.toKeychainException() ?: IllegalStateException("Biometric authentication is unavailable")
            } else null
        }
    } finally { context.invalidate() }
}
