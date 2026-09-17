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
import id.walt.crypto2.keys.HardwarePreference

import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeychainAccessibility
import id.walt.crypto2.keys.PlatformKeyConfiguration
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.ManagedKey
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.providers.GenerateManagedKeyRequest
import id.walt.crypto2.signum.IosSignumKeyBackend
import id.walt.crypto2.signum.SignumKeyNotFoundException
import id.walt.crypto2.signum.SignumKeyInvalidatedException
import id.walt.crypto2.signum.SignumKeyOptions
import id.walt.crypto2.signum.SignumKeyPolicy
import id.walt.crypto2.signum.SignumKeyPolicyMismatchException
import id.walt.crypto2.signum.SignumManagedKeyProvider
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSProcessInfo

/** Managed-key provider backed by iOS Keychain and Secure Enclave. */
public class IosPlatformKeyProvider : PlatformManagedKeyProvider {
    private val backend = IosSignumKeyBackend()
    private val signumProvider = SignumManagedKeyProvider(backend)

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun preflight(requirements: WalletKeyRequirements): KeyUseAuthorizationSupport {
        val signumPolicy = requirements.nativePolicy()
        if ((isSimulator && signumPolicy.hardware == id.walt.crypto2.keys.HardwarePreference.REQUIRED) ||
            !backend.supports(requirements.spec, requirements.usages, signumPolicy)) {
            return KeyUseAuthorizationSupport.Unsupported(KeyUseAuthorizationUnsupportedReason.UnsupportedCombination)
        }
        val passcodeBound = (requirements.platform as? PlatformKeyConfiguration.IosKeychain)?.accessibility ==
            KeychainAccessibility.WHEN_PASSCODE_SET_DEVICE_ONLY
        if (requirements.authorizationPolicy is KeyUseAuthorizationPolicy.None) {
            if (passcodeBound) iosAuthorizationAvailabilityFailure(KeyUseAuthorizationPolicy.DeviceCredential())?.let {
                return KeyUseAuthorizationSupport.Unsupported(it)
            }
            return requirements.authorizationPolicy.supportedOnIos()
        }
        val failure = when {
            requirements.spec != KeySpec.Ec(EcCurve.P256) ||
                requirements.usages != setOf(KeyUsage.SIGN, KeyUsage.VERIFY) ->
                KeyUseAuthorizationUnsupportedReason.UnsupportedCombination
            isSimulator -> KeyUseAuthorizationUnsupportedReason.BiometricUnavailable
            else -> iosAuthorizationAvailabilityFailure(requirements.authorizationPolicy)
        }
        return failure?.let { KeyUseAuthorizationSupport.Unsupported(it) }
            ?: requirements.authorizationPolicy.supportedOnIos()
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
        ).withIosAuthorizationMapping(request.requirements.authorizationPolicy)
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
        ), material).withIosAuthorizationMapping(request.requirements.authorizationPolicy)
    } catch (cause: Throwable) {
        throw cause.toKeyUseAuthorizationException(request.id.value, KeyUseAuthorizationFailure.UnsupportedCombination) ?: cause
    }

    override suspend fun keyFacts(stored: StoredKey.Managed): PlatformKeyFacts = try {
        signumProvider.restoreSignumKey(stored).let { it.toWalletKeyFacts(id.walt.crypto2.keys.KeyAuthorizationEvidence.CREATION_RECORD) }
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
                signumProvider.restore(stored).withIosAuthorizationMapping(policy),
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

    private fun WalletKeyRequirements.nativePolicy(prompt: KeyUseAuthorizationPrompt = KeyUseAuthorizationPrompt()): SignumKeyPolicy =
        toSignumPolicy(prompt)

    private fun WalletKeyCreationRequest.toSignumPolicy(): SignumKeyPolicy =
        requirements.nativePolicy(prompt)

    private fun ManagedKey.withIosAuthorizationMapping(policy: KeyUseAuthorizationPolicy): ManagedKey =
        withWalletAuthorizationMapping { iosAuthorizationAvailabilityFailure(policy)?.toAuthorizationFailure() }

}

private fun KeyUseAuthorizationPolicy.supportedOnIos(): KeyUseAuthorizationSupport.Supported =
    KeyUseAuthorizationSupport.Supported(
        effectivePolicy = this,
        reuseEnforcement = if (this.reuseSeconds > 0) {
            KeyUseAuthorizationReuseEnforcement.ProviderProcess
        } else {
            null
        },
        timeoutValidation = if (this.reuseSeconds > 0) {
            KeyUseAuthorizationReuseTimeoutValidation.ProviderConfigurationOnly
        } else {
            null
        },
    )

private val isSimulator: Boolean by lazy {
    NSProcessInfo.processInfo.environment.keys.any { it == "SIMULATOR_UDID" || it == "SIMULATOR_DEVICE_NAME" }
}
