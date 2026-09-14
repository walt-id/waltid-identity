package id.walt.wallet2.persistence.keys

import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.ManagedKey
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.providers.GenerateManagedKeyRequest
import id.walt.crypto2.signum.IosSignumKeyBackend
import id.walt.crypto2.signum.SignumKeyNotFoundException
import id.walt.crypto2.signum.SignumKeyOptions
import id.walt.crypto2.signum.SignumKeyPolicy
import id.walt.crypto2.signum.SignumKeyPolicyMismatchException
import id.walt.crypto2.signum.SignumManagedKeyProvider
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.Foundation.NSProcessInfo
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAErrorBiometryNotAvailable
import platform.LocalAuthentication.LAErrorBiometryNotEnrolled
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics

/** Managed-key provider backed by iOS Keychain and Secure Enclave. */
public class IosPlatformKeyProvider : PlatformManagedKeyProvider {
    private val backend = IosSignumKeyBackend()
    private val signumProvider = SignumManagedKeyProvider(backend)

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun preflight(requirements: WalletKeyRequirements): KeyUseAuthorizationSupport {
        val signumPolicy = requirements.nativePolicy()
        if ((isSimulator && signumPolicy.hardware == id.walt.crypto2.signum.SignumHardwarePolicy.REQUIRED) ||
            !backend.supports(requirements.spec, requirements.usages, signumPolicy)) {
            return KeyUseAuthorizationSupport.Unsupported(KeyUseAuthorizationUnsupportedReason.UnsupportedCombination)
        }
        if (requirements.authorizationPolicy is KeyUseAuthorizationPolicy.None) {
            return requirements.authorizationPolicy.supportedOnIos()
        }
        val failure = when {
            requirements.spec != KeySpec.Ec(EcCurve.P256) ||
                requirements.usages != setOf(KeyUsage.SIGN, KeyUsage.VERIFY) ->
                KeyUseAuthorizationUnsupportedReason.UnsupportedCombination
            isSimulator -> KeyUseAuthorizationUnsupportedReason.BiometricUnavailable
            else -> biometricAvailabilityFailure(requirements.authorizationPolicy)
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

    override fun supportsPrivateKeyImport(requirements: WalletKeyRequirements): Boolean =
        backend.supportsImport(requirements.spec, requirements.usages, requirements.nativePolicy())

    override suspend fun importManagedKey(request: WalletKeyCreationRequest,
        material: id.walt.crypto2.keys.EncodedKey.Jwk): ManagedKey =
        signumProvider.importPrivateKey(GenerateManagedKeyRequest(
            id = request.id, metadata = mapOf("wallet.nativeAlias" to request.nativeAlias), spec = request.requirements.spec, usages = request.requirements.usages,
            providerOptions = SignumKeyOptions(alias = request.nativeAlias, policy = request.toSignumPolicy()).encode(),
        ), material).withWalletAuthorizationMapping(request.requirements.authorizationPolicy)

    override suspend fun keyFacts(stored: StoredKey.Managed): PlatformKeyFacts =
        signumProvider.restoreSignumKey(stored).let { PlatformKeyFacts(it.origin, it.securityLevel, it.protectionLevel, it.attestation) }

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
        toSignumPolicy(prompt).let { policy ->
            if (spec == KeySpec.Ec(EcCurve.P256) && platform == id.walt.crypto2.signum.SignumPlatformPolicy.Default)
                policy.copy(platform = id.walt.crypto2.signum.SignumPlatformPolicy.IosKeychain()) else policy
        }

    private fun WalletKeyCreationRequest.toSignumPolicy(): SignumKeyPolicy =
        requirements.nativePolicy(prompt)

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    private fun biometricAvailabilityFailure(policy: KeyUseAuthorizationPolicy): KeyUseAuthorizationUnsupportedReason? = memScoped {
        val error = alloc<ObjCObjectVar<platform.Foundation.NSError?>>()
        val available = LAContext().canEvaluatePolicy(
            if (policy is KeyUseAuthorizationPolicy.DeviceCredential || policy is KeyUseAuthorizationPolicy.BiometricOrDeviceCredential)
                platform.LocalAuthentication.LAPolicyDeviceOwnerAuthentication else LAPolicyDeviceOwnerAuthenticationWithBiometrics,
            error.ptr,
        )
        if (available) return@memScoped null
        when (error.value?.code) {
            LAErrorBiometryNotEnrolled -> KeyUseAuthorizationUnsupportedReason.BiometricNotEnrolled
            LAErrorBiometryNotAvailable -> KeyUseAuthorizationUnsupportedReason.BiometricUnavailable
            else -> KeyUseAuthorizationUnsupportedReason.BiometricUnavailable
        }
    }

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
