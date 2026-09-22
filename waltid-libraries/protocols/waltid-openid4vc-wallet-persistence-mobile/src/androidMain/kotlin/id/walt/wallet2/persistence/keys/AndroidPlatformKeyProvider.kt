package id.walt.wallet2.persistence.keys

import id.walt.crypto2.keys.KeyUseAuthorizationPolicy
import id.walt.crypto2.keys.KeyUseAuthorizationFailure
import id.walt.crypto2.keys.KeyUseAuthorizationSupport
import id.walt.crypto2.keys.KeyUseAuthorizationReuseEnforcement
import id.walt.crypto2.keys.KeyUseAuthorizationReuseTimeoutValidation
import id.walt.crypto2.keys.KeyUseAuthorizationUnsupportedReason
import id.walt.crypto2.keys.PlatformKeyFacts
import id.walt.crypto2.keys.reuseSeconds
import id.walt.crypto2.keys.toAuthorizationFailure
import id.walt.crypto2.keys.PlatformKeyConfiguration
import id.walt.crypto2.keys.HardwarePreference
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.uuid.Uuid
import android.app.KeyguardManager
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
import id.walt.crypto2.signum.SignumKeyInvalidatedException
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
    private val backend = AndroidSignumKeyBackend(applicationContext, interactionContextProvider)
    private val signumProvider = SignumManagedKeyProvider(backend)
    private val capabilityMutex = Mutex()
    private val hardwareCapabilities = mutableMapOf<id.walt.crypto2.keys.HardwarePreference, Boolean>()

    // Feature declarations are not reliable evidence of a P-256 key's actual execution tier.
    // Probe an owned, unauthenticated alias once per backing preference and always remove it.
    private suspend fun supportsHardware(policy: SignumKeyPolicy): Boolean = capabilityMutex.withLock {
        val backing = (policy.platform as? id.walt.crypto2.keys.PlatformKeyConfiguration.AndroidKeystore)?.strongBox
            ?: id.walt.crypto2.keys.HardwarePreference.DISCOURAGED
        hardwareCapabilities[backing]?.let { return@withLock it }
        val alias = "wallet_capability_${Uuid.random()}"
        val probe = SignumKeyPolicy(hardware = id.walt.crypto2.keys.HardwarePreference.REQUIRED,
            platform = id.walt.crypto2.keys.PlatformKeyConfiguration.AndroidKeystore(strongBox = backing))
        val supported = try {
            backend.create(alias, KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY), probe)
            true
        } catch (_: SignumKeyPolicyMismatchException) { false }
        finally { withContext(NonCancellable) { backend.delete(alias, probe) } }
        hardwareCapabilities[backing] = supported
        supported
    }


    override suspend fun preflight(requirements: WalletKeyRequirements): KeyUseAuthorizationSupport {
        val signumPolicy = requirements.nativePolicy()
        val settings = signumPolicy.platform as? id.walt.crypto2.keys.PlatformKeyConfiguration.AndroidKeystore
        if ((settings?.strongBox == id.walt.crypto2.keys.HardwarePreference.REQUIRED &&
                !applicationContext.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_STRONGBOX_KEYSTORE)) ||
            !backend.supports(requirements.spec, requirements.usages, signumPolicy)) {
            return KeyUseAuthorizationSupport.Unsupported(KeyUseAuthorizationUnsupportedReason.UnsupportedCombination)
        }
        if (signumPolicy.hardware == id.walt.crypto2.keys.HardwarePreference.REQUIRED &&
            !supportsHardware(signumPolicy)) {
            return KeyUseAuthorizationSupport.Unsupported(KeyUseAuthorizationUnsupportedReason.UnsupportedCombination)
        }
        if (requirements.authorizationPolicy is KeyUseAuthorizationPolicy.None) {
            return requirements.authorizationPolicy.supportedOnAndroid()
        }
        val failure = when {
            requirements.spec != KeySpec.Ec(EcCurve.P256) ||
                requirements.usages != setOf(KeyUsage.SIGN, KeyUsage.VERIFY) ->
                KeyUseAuthorizationUnsupportedReason.UnsupportedCombination
            else -> authorizationAvailabilityFailure(requirements.authorizationPolicy)
        }
        return failure?.let { KeyUseAuthorizationSupport.Unsupported(it) }
            ?: requirements.authorizationPolicy.supportedOnAndroid()
    }

    override suspend fun generateManagedKey(request: WalletKeyCreationRequest): ManagedKey = try {
        signumProvider.generate(
            GenerateManagedKeyRequest(
                id = request.id,
                metadata = mapOf("wallet.nativeAlias" to request.nativeAlias),
                spec = request.requirements.spec,
                usages = request.requirements.usages,
                providerOptions = SignumKeyOptions(alias = request.nativeAlias, policy = request.toSignumPolicy()).encode(),
            )
        ).withAndroidAuthorizationMapping(request.requirements.authorizationPolicy)
    } catch (cause: Throwable) {
        throw cause.toKeyUseAuthorizationException(
            protectedKeyId = request.id.value,
            policyMismatchFailure = KeyUseAuthorizationFailure.UnsupportedCombination,
        ) ?: cause
    }

    override fun supportsPrivateKeyImport(requirements: WalletKeyRequirements): Boolean =
        backend.supportsImport(requirements.spec, requirements.usages, requirements.nativePolicy())

    override suspend fun importManagedKey(request: WalletKeyCreationRequest,
        material: id.walt.crypto2.keys.EncodedKey.Jwk): ManagedKey = try {
        signumProvider.importPrivateKey(GenerateManagedKeyRequest(
            id = request.id, metadata = mapOf("wallet.nativeAlias" to request.nativeAlias), spec = request.requirements.spec, usages = request.requirements.usages,
            providerOptions = SignumKeyOptions(alias = request.nativeAlias, policy = request.toSignumPolicy()).encode(),
        ), material).withAndroidAuthorizationMapping(request.requirements.authorizationPolicy)
    } catch (cause: Throwable) {
        throw cause.toKeyUseAuthorizationException(request.id.value, KeyUseAuthorizationFailure.UnsupportedCombination) ?: cause
    }

    override suspend fun keyFacts(stored: StoredKey.Managed): PlatformKeyFacts = try {
        signumProvider.restoreSignumKey(stored).let { it.toWalletKeyFacts(id.walt.crypto2.keys.KeyAuthorizationEvidence.NATIVE_ATTRIBUTES) }
    } catch (cause: Throwable) {
        throw cause.toKeyUseAuthorizationException(stored.id.value) ?: cause
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
                signumProvider.restore(stored).withAndroidAuthorizationMapping(policy),
                policy,
            )
        } catch (_: SignumKeyInvalidatedException) {
            PlatformManagedKeyRestoration.Invalidated(policy)
        } catch (_: SignumKeyNotFoundException) {
            PlatformManagedKeyRestoration.Missing(policy)
        } catch (cause: Throwable) {
            throw cause.toKeyUseAuthorizationException(stored.id.value) ?: cause
        }
    }

    override suspend fun deleteUncommittedKey(request: WalletKeyCreationRequest, imported: Boolean) {
        if (imported) backend.deleteImportedKey(request.nativeAlias, request.toSignumPolicy())
        else backend.delete(request.nativeAlias, request.toSignumPolicy())
    }

    override suspend fun deleteManagedKey(stored: StoredKey.Managed) {
        try {
            signumProvider.delete(stored, expectedAlias = stored.metadata["wallet.nativeAlias"] ?: stored.id.value)
        } catch (cause: Throwable) {
            throw cause.toKeyUseAuthorizationException(stored.id.value) ?: cause
        }
    }

    private fun authenticationAvailability(policy: KeyUseAuthorizationPolicy): Int =
        if (policy is KeyUseAuthorizationPolicy.None) BiometricManager.BIOMETRIC_SUCCESS
        else BiometricManager.from(applicationContext).canAuthenticate(when (policy) {
            is KeyUseAuthorizationPolicy.DeviceCredential -> BiometricManager.Authenticators.DEVICE_CREDENTIAL
            is KeyUseAuthorizationPolicy.BiometricOrDeviceCredential -> BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            else -> BIOMETRIC_STRONG
        })

    private fun authorizationAvailabilityFailure(policy: KeyUseAuthorizationPolicy): KeyUseAuthorizationUnsupportedReason? {
        if (policy is KeyUseAuthorizationPolicy.None) return null
        if (!applicationContext.getSystemService(KeyguardManager::class.java).isDeviceSecure) {
            return KeyUseAuthorizationUnsupportedReason.DeviceCredentialNotSet
        }
        return when (authenticationAvailability(policy)) {
            BiometricManager.BIOMETRIC_SUCCESS -> null
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> KeyUseAuthorizationUnsupportedReason.BiometricNotEnrolled
            else -> KeyUseAuthorizationUnsupportedReason.BiometricUnavailable
        }
    }

    private fun ManagedKey.withAndroidAuthorizationMapping(policy: KeyUseAuthorizationPolicy): ManagedKey =
        withWalletAuthorizationMapping { authorizationAvailabilityFailure(policy)?.toAuthorizationFailure() }

    private fun WalletKeyRequirements.nativePolicy(prompt: KeyUseAuthorizationPrompt = KeyUseAuthorizationPrompt()): SignumKeyPolicy =
        toSignumPolicy(prompt).let { policy ->
            if (spec != KeySpec.Ec(EcCurve.P256)) return@let policy
            val settings = when (val platform = policy.platform) {
                id.walt.crypto2.keys.PlatformKeyConfiguration.Default -> id.walt.crypto2.keys.PlatformKeyConfiguration.AndroidKeystore()
                is id.walt.crypto2.keys.PlatformKeyConfiguration.AndroidKeystore -> platform
                else -> return@let policy
            }
            // API 31 import wraps absent StrongBox in a generic KeyStoreException. Use the public
            // capability flag before import rather than interpreting exception text or weakening hardware.
            val effective = if (settings.strongBox == id.walt.crypto2.keys.HardwarePreference.PREFERRED &&
                !applicationContext.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_STRONGBOX_KEYSTORE))
                settings.copy(strongBox = id.walt.crypto2.keys.HardwarePreference.DISCOURAGED) else settings
            policy.copy(platform = effective)
        }

    private fun WalletKeyCreationRequest.toSignumPolicy(): SignumKeyPolicy =
        requirements.nativePolicy(prompt)
}

private fun KeyUseAuthorizationPolicy.supportedOnAndroid(): KeyUseAuthorizationSupport.Supported =
    KeyUseAuthorizationSupport.Supported(
        effectivePolicy = this,
        reuseEnforcement = if (this.reuseSeconds > 0) {
            KeyUseAuthorizationReuseEnforcement.PlatformKeyStore
        } else {
            null
        },
        timeoutValidation = if (this.reuseSeconds > 0) {
            KeyUseAuthorizationReuseTimeoutValidation.IndependentReadback
        } else {
            null
        },
    )
