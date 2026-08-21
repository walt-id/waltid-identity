package id.walt.walletdemo.compose.logic

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
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

    override suspend fun authenticate(reason: String): DemoBiometricResult {
        val activity = activityProvider() ?: return DemoBiometricResult.Unavailable
        if (!isAvailable()) return DemoBiometricResult.Unavailable

        return suspendCancellableCoroutine { continuation ->
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
                        if (!continuation.isActive) return
                        val result = when (errorCode) {
                            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                            BiometricPrompt.ERROR_USER_CANCELED,
                            BiometricPrompt.ERROR_CANCELED,
                            -> DemoBiometricResult.Cancelled
                            else -> DemoBiometricResult.Failed
                        }
                        continuation.resume(result)
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
