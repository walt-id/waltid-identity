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
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_sync
import platform.LocalAuthentication.LAErrorBiometryNotAvailable
import platform.LocalAuthentication.LAErrorBiometryNotEnrolled
import platform.LocalAuthentication.LAErrorUserCancel
import platform.LocalAuthentication.LAErrorUserFallback
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
