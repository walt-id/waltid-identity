package id.walt.walletdemo.compose.logic

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSError
import platform.Foundation.NSThread
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAErrorAppCancel
import platform.LocalAuthentication.LAErrorSystemCancel
import platform.LocalAuthentication.LAErrorUserCancel
import platform.LocalAuthentication.LAErrorUserFallback
import platform.LocalAuthentication.LAErrorBiometryLockout
import platform.LocalAuthentication.LAErrorBiometryNotAvailable
import platform.LocalAuthentication.LAErrorBiometryNotEnrolled
import platform.LocalAuthentication.LAErrorDomain
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_sync
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics
import kotlin.coroutines.resume

fun createIosDemoBiometricAuthenticator(): DemoBiometricAuthenticator = IosDemoBiometricAuthenticator()

private class IosDemoBiometricAuthenticator : DemoBiometricAuthenticator {
    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    override fun isAvailable(): Boolean = onMainThread { evaluateAvailability() }

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    override suspend fun authenticate(reason: String): DemoBiometricResult = withContext(Dispatchers.Main) {
        if (!evaluateAvailability()) return@withContext DemoBiometricResult.Unavailable
        val context = LAContext()
        return@withContext suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { context.invalidate() }
            context.evaluatePolicy(
                LAPolicyDeviceOwnerAuthenticationWithBiometrics,
                localizedReason = reason,
            ) { success, error ->
                if (!continuation.isActive) return@evaluatePolicy
                continuation.resume(
                    if (success) DemoBiometricResult.Succeeded else when (error?.takeIf { it.domain == LAErrorDomain }?.code) {
                        LAErrorUserCancel, LAErrorSystemCancel, LAErrorAppCancel, LAErrorUserFallback -> DemoBiometricResult.Cancelled
                        LAErrorBiometryLockout -> DemoBiometricResult.LockedOut
                        LAErrorBiometryNotAvailable, LAErrorBiometryNotEnrolled -> DemoBiometricResult.Unavailable
                        else -> DemoBiometricResult.Failed
                    },
                )
            }
        }
    }

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    private fun evaluateAvailability(): Boolean = memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        LAContext().canEvaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, error.ptr)
    }
}

private inline fun <T> onMainThread(crossinline block: () -> T): T {
    if (NSThread.currentThread.isMainThread) return block()
    var result: T? = null
    dispatch_sync(dispatch_get_main_queue()) {
        result = block()
    }
    return result!!
}
