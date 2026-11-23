package id.walt.crypto2.signum

import android.security.keystore.UserNotAuthenticatedException
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureCodec
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.Signer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.util.concurrent.atomic.AtomicBoolean

internal suspend fun authenticateFreshBiometrics(
    activity: FragmentActivity,
    policy: SignumAuthenticationPolicy.UserPresence,
): TimeMark =
    withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine<TimeMark> { continuation ->
            val terminal = AtomicBoolean(false)
            val executor = ContextCompat.getMainExecutor(activity)
            val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (!terminal.compareAndSet(false, true)) return
                    val outcome = if (result.authenticationType == BiometricPrompt.AUTHENTICATION_RESULT_TYPE_BIOMETRIC) {
                        Result.success(TimeSource.Monotonic.markNow())
                    } else {
                        Result.failure(SignumAuthorizationException("Strong biometric authentication was not established"))
                    }
                    continuation.resumeWith(outcome)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (!terminal.compareAndSet(false, true)) return
                    val failure = SignumAuthorizationException("Biometric authorization failed (code $errorCode)")
                    val outcome = when (errorCode) {
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_CANCELED -> SignumUserCancelledException(failure)
                        else -> failure
                    }
                    continuation.resumeWith(Result.failure(outcome))
                }
            })
            continuation.invokeOnCancellation {
                terminal.set(true)
                executor.execute { prompt.cancelAuthentication() }
            }
            if (continuation.isActive) {
                prompt.authenticate(
                    BiometricPrompt.PromptInfo.Builder()
                        .setTitle(policy.prompt)
                        .setNegativeButtonText(policy.cancelText)
                        .setAllowedAuthenticators(BIOMETRIC_STRONG)
                        .build(),
                )
            }
        }
    }

/** Signum retries authentication automatically. This native operation deliberately cannot prompt. */
internal fun androidNonInteractiveSigner(alias: String, publicKey: EncodedKey.SpkiDer): Signer = Signer { data, algorithm ->
    require(algorithm == SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256)) { "Fresh biometric signing requires ES256" }
    withContext(Dispatchers.IO) {
        try {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val actualPublicKey = store.getCertificate(alias)?.publicKey?.encoded ?: throw SignumKeyNotFoundException(alias)
            if (!actualPublicKey.contentEquals(publicKey.data.toByteArray())) {
                throw SignumKeyPolicyMismatchException(alias, "native key changed during authorization")
            }
            val privateKey = store.getKey(alias, null) as? PrivateKey ?: throw SignumKeyNotFoundException(alias)
            val signature = Signature.getInstance("SHA256withECDSA").run {
                initSign(privateKey)
                update(data)
                sign()
            }
            EcdsaSignatureCodec.derToP1363(signature, 32)
        } catch (cause: UserNotAuthenticatedException) {
            throw SignumAuthorizationException("Native biometric authorization has expired", cause)
        } catch (cause: Throwable) {
            throw cause.mapSignumFailure(alias)
        }
    }
}
