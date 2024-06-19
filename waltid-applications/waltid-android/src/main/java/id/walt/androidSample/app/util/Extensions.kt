package id.walt.androidSample.app.util

import android.annotation.SuppressLint
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.fragment.app.FragmentActivity
import id.walt.androidSample.R

@SuppressLint("UnnecessaryComposedModifier")
fun Modifier.clickableWithoutRipple(
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit
) = composed(
    factory = {
        this.then(
            Modifier.clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onClick() }
            )
        )
    }
)

fun authenticateWithBiometric(
    context: FragmentActivity,
    onAuthenticated: () -> Unit,
    onFailure: (msg: String?) -> Unit,
    isBiometricsAvailable: Int,
) {

    when (isBiometricsAvailable) {
        BiometricManager.BIOMETRIC_SUCCESS -> {
            val executor = context.mainExecutor
            val biometricPrompt = BiometricPrompt(
                context,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        onAuthenticated()
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        onFailure(null)
                    }
                }
            )
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .setTitle(context.getString(R.string.title_biometric_authentication))
                .setSubtitle(context.getString(R.string.subtitle_biometric_authentication))
                .setNegativeButtonText(context.getString(R.string.cancel))
                .build()

            biometricPrompt.authenticate(promptInfo)
        }

        else -> onFailure(context.getString(R.string.biometric_error_general))
    }
}