@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package id.walt.wallet2.persistence.keys

import kotlinx.cinterop.*
import platform.Foundation.NSError
import platform.LocalAuthentication.*

/** Non-interactive availability only; native key access control still authorizes every operation. */
internal fun iosAuthorizationAvailabilityFailure(policy: KeyUseAuthorizationPolicy): KeyUseAuthorizationUnsupportedReason? {
    if (policy == KeyUseAuthorizationPolicy.None) return null
    val context = LAContext()
    return try {
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            error.value = null
            val nativePolicy = when (policy) {
                is KeyUseAuthorizationPolicy.DeviceCredential, is KeyUseAuthorizationPolicy.BiometricOrDeviceCredential ->
                    LAPolicyDeviceOwnerAuthentication
                else -> LAPolicyDeviceOwnerAuthenticationWithBiometrics
            }
            if (context.canEvaluatePolicy(nativePolicy, error.ptr)) null
            else iosAuthorizationUnavailableReason(error.value?.domain, error.value?.code)
        }
    } finally { context.invalidate() }
}

internal fun iosAuthorizationUnavailableReason(domain: String?, code: Long?): KeyUseAuthorizationUnsupportedReason =
    if (domain != LAErrorDomain) KeyUseAuthorizationUnsupportedReason.BiometricUnavailable
    else when (code) {
        LAErrorPasscodeNotSet -> KeyUseAuthorizationUnsupportedReason.DeviceCredentialNotSet
        LAErrorBiometryNotEnrolled -> KeyUseAuthorizationUnsupportedReason.BiometricNotEnrolled
        else -> KeyUseAuthorizationUnsupportedReason.BiometricUnavailable
    }
