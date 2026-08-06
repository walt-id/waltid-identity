package id.walt.wallet2.persistence.keys

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.fragment.app.FragmentActivity
import id.walt.crypto2.keys.EcCurve
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

/**
 * Managed-key provider backed by Android KeyStore.
 */
public class AndroidPlatformKeyProvider(
    context: Context,
    private val interactionContextProvider: () -> FragmentActivity? = { null },
) : PlatformManagedKeyProvider {
    private val applicationContext = context.applicationContext
    private val backend = AndroidSignumKeyBackend(interactionContextProvider)
    private val signumProvider = SignumManagedKeyProvider(backend)

    override suspend fun preflight(requirements: PlatformKeyRequirements): PlatformKeyRequestSupport {
        val signumPolicy = requirements.authorizationPolicy.toSignumPolicy()
        if (!backend.supports(requirements.spec, requirements.usages, signumPolicy)) {
            return PlatformKeyRequestSupport.Unsupported(PlatformKeyUnsupportedReason.UnsupportedCombination)
        }
        if (requirements.authorizationPolicy == KeyUseAuthorizationPolicy.None) {
            return PlatformKeyRequestSupport.Supported
        }
        val failure = when {
            requirements.spec != KeySpec.Ec(EcCurve.P256) ||
                requirements.usages != setOf(KeyUsage.SIGN, KeyUsage.VERIFY) ->
                PlatformKeyUnsupportedReason.UnsupportedCombination
            !hasResumedActivity(interactionContextProvider) ->
                PlatformKeyUnsupportedReason.InteractionContextUnavailable
            else -> when (BiometricManager.from(applicationContext).canAuthenticate(BIOMETRIC_STRONG)) {
                BiometricManager.BIOMETRIC_SUCCESS -> null
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> PlatformKeyUnsupportedReason.BiometricNotEnrolled
                else -> PlatformKeyUnsupportedReason.BiometricUnavailable
            }
        }
        return failure?.let { PlatformKeyRequestSupport.Unsupported(it) }
            ?: PlatformKeyRequestSupport.Supported
    }

    override suspend fun generateManagedKey(request: PlatformKeyCreationRequest): ManagedKey = signumProvider.generate(
        GenerateManagedKeyRequest(
            id = request.id,
            spec = request.requirements.spec,
            usages = request.requirements.usages,
            providerOptions = SignumKeyOptions(policy = request.toSignumPolicy()).encode(),
        )
    )

    override suspend fun restoreManagedKey(stored: StoredKey.Managed): PlatformManagedKeyRestoration {
        val policy = signumProvider.inspect(stored).policy.toWalletPolicy()
        return try {
            PlatformManagedKeyRestoration.Restored(signumProvider.restore(stored), policy)
        } catch (_: SignumKeyNotFoundException) {
            PlatformManagedKeyRestoration.Missing(policy)
        }
    }

    override suspend fun deleteManagedKey(stored: StoredKey.Managed) {
        signumProvider.delete(stored, expectedAlias = stored.id.value)
    }

    private fun PlatformKeyCreationRequest.toSignumPolicy(): SignumKeyPolicy = when (requirements.authorizationPolicy) {
        KeyUseAuthorizationPolicy.None -> SignumKeyPolicy()
        KeyUseAuthorizationPolicy.BiometricCurrentSet -> SignumKeyPolicy(
            authentication = SignumAuthenticationPolicy.BiometricCurrentSet(
                reason = prompt.reason,
                cancelText = prompt.cancelText,
            ),
        )
    }

    private fun KeyUseAuthorizationPolicy.toSignumPolicy(): SignumKeyPolicy = when (this) {
        KeyUseAuthorizationPolicy.None -> SignumKeyPolicy()
        KeyUseAuthorizationPolicy.BiometricCurrentSet -> SignumKeyPolicy(
            authentication = SignumAuthenticationPolicy.BiometricCurrentSet(),
        )
    }
}

private fun hasResumedActivity(provider: () -> FragmentActivity?): Boolean {
    val activity = provider() ?: return false
    return !activity.isFinishing && !activity.isDestroyed && !activity.isChangingConfigurations &&
        activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
}

private fun SignumKeyPolicy.toWalletPolicy(): KeyUseAuthorizationPolicy = when (val authentication = authentication) {
    SignumAuthenticationPolicy.None -> KeyUseAuthorizationPolicy.None
    is SignumAuthenticationPolicy.BiometricCurrentSet -> {
        KeyUseAuthorizationPolicy.BiometricCurrentSet
    }
}
