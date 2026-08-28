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
import id.walt.crypto2.signum.SignumKeyPolicy
import id.walt.crypto2.signum.SignumKeyOptions
import id.walt.crypto2.signum.SignumKeyNotFoundException
import id.walt.crypto2.signum.SignumKeyPolicyMismatchException
import id.walt.crypto2.signum.SignumManagedKeyProvider

/**
 * Managed-key provider backed by Android KeyStore.
 *
 * Protected Signum operations resolve a current resumed [FragmentActivity] through
 * [interactionContextProvider] at operation time; the provider does not retain an activity.
 */
public class AndroidPlatformKeyProvider(
    context: Context,
    private val interactionContextProvider: () -> FragmentActivity? = { null },
) : PlatformManagedKeyProvider {
    private val applicationContext = context.applicationContext
    private val backend = AndroidSignumKeyBackend(interactionContextProvider)
    private val signumProvider = SignumManagedKeyProvider(backend)

    override suspend fun preflight(requirements: WalletKeyRequirements): KeyUseAuthorizationSupport {
        val signumPolicy = requirements.authorizationPolicy.toSignumPolicy()
        if (!backend.supports(requirements.spec, requirements.usages, signumPolicy)) {
            return KeyUseAuthorizationSupport.Unsupported(KeyUseAuthorizationUnsupportedReason.UnsupportedCombination)
        }
        if (requirements.authorizationPolicy is KeyUseAuthorizationPolicy.None) {
            return requirements.authorizationPolicy.supportedOnAndroid()
        }
        val failure = when {
            requirements.spec != KeySpec.Ec(EcCurve.P256) ||
                requirements.usages != setOf(KeyUsage.SIGN, KeyUsage.VERIFY) ->
                KeyUseAuthorizationUnsupportedReason.UnsupportedCombination
            else -> when (BiometricManager.from(applicationContext).canAuthenticate(BIOMETRIC_STRONG)) {
                BiometricManager.BIOMETRIC_SUCCESS -> null
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> KeyUseAuthorizationUnsupportedReason.BiometricNotEnrolled
                else -> KeyUseAuthorizationUnsupportedReason.BiometricUnavailable
            }
        }
        return failure?.let { KeyUseAuthorizationSupport.Unsupported(it) }
            ?: requirements.authorizationPolicy.supportedOnAndroid()
    }

    override suspend fun generateManagedKey(request: WalletKeyCreationRequest): ManagedKey = try {
        signumProvider.generate(
            GenerateManagedKeyRequest(
                id = request.id,
                spec = request.requirements.spec,
                usages = request.requirements.usages,
                providerOptions = SignumKeyOptions(policy = request.toSignumPolicy()).encode(),
            )
        ).withWalletAuthorizationMapping(request.requirements.authorizationPolicy)
    } catch (cause: Throwable) {
        if (
            request.requirements.authorizationPolicy is KeyUseAuthorizationPolicy.None &&
            cause is SignumKeyPolicyMismatchException
        ) {
            throw cause
        }
        throw cause.toKeyUseAuthorizationException(
            protectedKeyId = request.id.value,
            policyMismatchFailure = KeyUseAuthorizationFailure.UnsupportedCombination,
        ) ?: cause
    }

    override fun keyUseAuthorizationPolicy(stored: StoredKey.Managed): KeyUseAuthorizationPolicy = try {
        signumProvider.storedPolicy(stored).toWalletPolicy(stored)
    } catch (cause: Throwable) {
        throw cause.toKeyUseAuthorizationException(stored.id.value) ?: cause
    }

    override suspend fun restoreManagedKey(stored: StoredKey.Managed): PlatformManagedKeyRestoration {
        val policy = keyUseAuthorizationPolicy(stored)
        return try {
            PlatformManagedKeyRestoration.Restored(
                signumProvider.restore(stored).withWalletAuthorizationMapping(policy),
                policy,
            )
        } catch (_: SignumKeyNotFoundException) {
            PlatformManagedKeyRestoration.Missing(policy)
        } catch (cause: Throwable) {
            throw cause.toKeyUseAuthorizationException(stored.id.value) ?: cause
        }
    }

    override suspend fun deleteManagedKey(stored: StoredKey.Managed) {
        try {
            signumProvider.delete(stored, expectedAlias = stored.id.value)
        } catch (cause: Throwable) {
            throw cause.toKeyUseAuthorizationException(stored.id.value) ?: cause
        }
    }

    private fun WalletKeyCreationRequest.toSignumPolicy(): SignumKeyPolicy =
        requirements.authorizationPolicy.toSignumPolicy(prompt)
}

private fun KeyUseAuthorizationPolicy.supportedOnAndroid(): KeyUseAuthorizationSupport.Supported =
    KeyUseAuthorizationSupport.Supported(
        effectivePolicy = this,
        reuseEnforcement = if (this is KeyUseAuthorizationPolicy.BiometricTimedReuse) {
            KeyUseAuthorizationReuseEnforcement.PlatformKeyStore
        } else {
            null
        },
        timeoutValidation = if (this is KeyUseAuthorizationPolicy.BiometricTimedReuse) {
            KeyUseAuthorizationReuseTimeoutValidation.IndependentReadback
        } else {
            null
        },
    )
