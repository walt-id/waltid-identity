package id.walt.crypto2.signum

import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidAuthorizationPromptTest {
    @Test
    fun deviceCredentialPromptsOmitTheNegativeButton() {
        for (biometric in listOf(false, true)) {
            val policy = SignumAuthenticationPolicy.UserPresence(
                biometric = biometric, deviceCredential = true, cancelText = "Stop",
            )
            val prompt = prompt(policy)
            assertEquals((if (biometric) BIOMETRIC_STRONG else 0) or DEVICE_CREDENTIAL, prompt.allowedAuthenticators)
            assertEquals("", prompt.negativeButtonText.toString())
        }
    }

    @Test
    fun biometricOnlyPromptPreservesTheCancellationLabel() {
        val prompt = prompt(SignumAuthenticationPolicy.UserPresence(deviceCredential = false, cancelText = "Stop"))
        assertEquals(BIOMETRIC_STRONG, prompt.allowedAuthenticators)
        assertEquals("Stop", prompt.negativeButtonText.toString())
    }

    private fun prompt(policy: SignumAuthenticationPolicy.UserPresence) = BiometricPrompt.PromptInfo.Builder()
        .setTitle(policy.prompt)
        .setAllowedAuthenticators(policy.androidPromptAuthenticators)
        .setNegativeButtonText(policy.androidPromptCancelText)
        .build()
}
