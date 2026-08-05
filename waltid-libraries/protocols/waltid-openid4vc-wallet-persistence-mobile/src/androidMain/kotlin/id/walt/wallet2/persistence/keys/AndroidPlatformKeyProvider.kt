package id.walt.wallet2.persistence.keys

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.fragment.app.FragmentActivity
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.ManagedKey
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.providers.GenerateManagedKeyRequest
import id.walt.crypto2.signum.AndroidSignumKeyBackend
import id.walt.crypto2.signum.SignumAuthenticationPolicy
import id.walt.crypto2.signum.SignumKeyPolicy
import id.walt.crypto2.signum.SignumKeyOptions
import id.walt.crypto2.signum.SignumKeyNotFoundException
import id.walt.crypto2.signum.SignumManagedKeyProvider
import id.walt.crypto2.signum.isBiometricCurrentSet

/**
 * Managed-key provider backed by Android KeyStore.
 */
public class AndroidPlatformKeyProvider(
    context: Context,
    private val interactionContextProvider: () -> FragmentActivity? = { null },
) : PlatformManagedKeyProvider {
    private val applicationContext = context.applicationContext
    private val signumProvider = SignumManagedKeyProvider(AndroidSignumKeyBackend(interactionContextProvider))

    override suspend fun preflight(request: PlatformKeyRequest): PlatformKeyPreflight {
        if (request.authorizationPolicy == KeyUseAuthorizationPolicy.None) {
            return PlatformKeyPreflight(true)
        }
        val failure = when {
            request.spec != KeySpec.Ec(id.walt.crypto2.keys.EcCurve.P256) ||
                request.usages != setOf(KeyUsage.SIGN, KeyUsage.VERIFY) ->
                KeyUseAuthorizationFailure.UnsupportedCombination
            !hasResumedActivity(interactionContextProvider) ->
                KeyUseAuthorizationFailure.InteractionContextUnavailable
            else -> when (BiometricManager.from(applicationContext).canAuthenticate(BIOMETRIC_STRONG)) {
                BiometricManager.BIOMETRIC_SUCCESS -> null
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> KeyUseAuthorizationFailure.BiometricNotEnrolled
                else -> KeyUseAuthorizationFailure.BiometricUnavailable
            }
        }
        return PlatformKeyPreflight(failure == null, failure)
    }

    override suspend fun generateManagedKey(request: PlatformKeyRequest): ManagedKey = signumProvider.generate(
        GenerateManagedKeyRequest(
            id = request.id,
            spec = request.spec,
            usages = request.usages,
            providerOptions = SignumKeyOptions(policy = request.toSignumPolicy()).encode(),
        )
    )

    override suspend fun restoreManagedKey(stored: StoredKey.Managed): ManagedKey? = try {
        signumProvider.restore(stored)
    } catch (_: SignumKeyNotFoundException) {
        null
    }

    override suspend fun deleteManagedKey(stored: StoredKey.Managed) {
        signumProvider.delete(stored, expectedAlias = stored.id.value)
    }

    override fun inspectManagedKey(stored: StoredKey.Managed): PlatformManagedKeyInfo {
        val info = signumProvider.inspect(stored)
        return PlatformManagedKeyInfo(
            authorizationPolicy = info.policy.toWalletPolicy(),
        )
    }

    private fun PlatformKeyRequest.toSignumPolicy(): SignumKeyPolicy = when (authorizationPolicy) {
        KeyUseAuthorizationPolicy.None -> SignumKeyPolicy()
        KeyUseAuthorizationPolicy.BiometricCurrentSet -> SignumKeyPolicy(
            authentication = SignumAuthenticationPolicy.UserPresence(
                biometric = true,
                allowNewBiometrics = false,
                deviceCredential = false,
                timeoutSeconds = 0,
                prompt = prompt.message,
                cancelText = prompt.cancelText,
            ),
        )
    }
}

private fun hasResumedActivity(provider: () -> FragmentActivity?): Boolean {
    val activity = provider() ?: return false
    return !activity.isFinishing && !activity.isDestroyed && !activity.isChangingConfigurations &&
        activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
}

private fun SignumKeyPolicy.toWalletPolicy(): KeyUseAuthorizationPolicy =
    KeyUseAuthorizationPolicy.BiometricCurrentSet.takeIf { authentication.isBiometricCurrentSet() }
        ?: KeyUseAuthorizationPolicy.None
