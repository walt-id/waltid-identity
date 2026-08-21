package id.walt.walletdemo.compose.logic

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSError
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAErrorBiometryNotAvailable
import platform.LocalAuthentication.LAErrorBiometryNotEnrolled
import platform.LocalAuthentication.LAErrorUserCancel
import platform.LocalAuthentication.LAErrorUserFallback
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics
import kotlin.coroutines.resume

fun createIosDemoBiometricAuthenticator(): DemoBiometricAuthenticator = IosDemoBiometricAuthenticator()

private class IosDemoBiometricAuthenticator : DemoBiometricAuthenticator {
    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    override fun isAvailable(): Boolean = memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        LAContext().canEvaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, error.ptr)
    }

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    override suspend fun authenticate(reason: String): DemoBiometricResult {
        if (!isAvailable()) return DemoBiometricResult.Unavailable
        val context = LAContext()
        return suspendCancellableCoroutine { continuation ->
            context.evaluatePolicy(
                LAPolicyDeviceOwnerAuthenticationWithBiometrics,
                localizedReason = reason,
            ) { success, error ->
                if (!continuation.isActive) return@evaluatePolicy
                val result = when {
                    success -> DemoBiometricResult.Succeeded
                    error?.code == LAErrorUserCancel || error?.code == LAErrorUserFallback ->
                        DemoBiometricResult.Cancelled
                    error?.code == LAErrorBiometryNotAvailable || error?.code == LAErrorBiometryNotEnrolled ->
                        DemoBiometricResult.Unavailable
                    else -> DemoBiometricResult.Failed
                }
                continuation.resume(result)
            }
        }
    }
}
