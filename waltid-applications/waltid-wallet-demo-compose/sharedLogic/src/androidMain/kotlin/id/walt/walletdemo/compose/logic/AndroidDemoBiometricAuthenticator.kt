package id.walt.walletdemo.compose.logic

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

private const val BIOMETRIC_AUTHENTICATORS = BIOMETRIC_STRONG or BIOMETRIC_WEAK

fun createAndroidDemoBiometricAuthenticator(
    activityProvider: () -> FragmentActivity?,
): DemoBiometricAuthenticator = AndroidDemoBiometricAuthenticator(activityProvider)

private class AndroidDemoBiometricAuthenticator(
    private val activityProvider: () -> FragmentActivity?,
) : DemoBiometricAuthenticator {
    override fun isAvailable(): Boolean {
        val activity = activityProvider() ?: return false
        return BiometricManager.from(activity).canAuthenticate(BIOMETRIC_AUTHENTICATORS) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    override suspend fun authenticate(reason: String): DemoBiometricResult =
        withContext(Dispatchers.Main.immediate) {
            val activity = activityProvider() ?: return@withContext DemoBiometricResult.Unavailable
            val ready = activity.lifecycle.currentStateFlow.first {
                it.isAtLeast(Lifecycle.State.RESUMED) || it == Lifecycle.State.DESTROYED
            }
            if (ready == Lifecycle.State.DESTROYED) return@withContext DemoBiometricResult.Unavailable
            if (!isAvailable()) return@withContext DemoBiometricResult.Unavailable

            suspendCancellableCoroutine { continuation ->
                val prompt = BiometricPrompt(
                    activity,
                    activity.mainExecutor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            if (continuation.isActive) {
                                continuation.resume(DemoBiometricResult.Succeeded)
                            }
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            if (continuation.isActive) {
                                continuation.resume(when (errorCode) {
                                    BiometricPrompt.ERROR_CANCELED, BiometricPrompt.ERROR_USER_CANCELED,
                                    BiometricPrompt.ERROR_NEGATIVE_BUTTON -> DemoBiometricResult.Cancelled
                                    BiometricPrompt.ERROR_LOCKOUT, BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> DemoBiometricResult.LockedOut
                                    BiometricPrompt.ERROR_NO_BIOMETRICS, BiometricPrompt.ERROR_HW_NOT_PRESENT,
                                    BiometricPrompt.ERROR_HW_UNAVAILABLE -> DemoBiometricResult.Unavailable
                                    else -> DemoBiometricResult.Failed
                                })
                            }
                        }
                    },
                )
                continuation.invokeOnCancellation { prompt.cancelAuthentication() }
                prompt.authenticate(
                    BiometricPrompt.PromptInfo.Builder()
                        .setAllowedAuthenticators(BIOMETRIC_AUTHENTICATORS)
                        .setTitle("Unlock walt.id Wallet")
                        .setSubtitle(reason)
                        .setNegativeButtonText("Use PIN")
                        .build(),
                )
            }
        }
}
