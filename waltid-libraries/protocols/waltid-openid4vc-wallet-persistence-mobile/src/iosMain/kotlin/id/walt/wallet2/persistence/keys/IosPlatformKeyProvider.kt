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
        val signumPolicy = requirements.authorizationPolicy.toSignumPolicy()
        if (!backend.supports(requirements.spec, requirements.usages, signumPolicy)) {
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
            else -> biometricAvailabilityFailure()
        }
        return failure?.let { KeyUseAuthorizationSupport.Unsupported(it) }
            ?: requirements.authorizationPolicy.supportedOnIos()
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

    override suspend fun restoreManagedKey(stored: StoredKey.Managed): PlatformManagedKeyRestoration {
        val policy = try {
            signumProvider.storedPolicy(stored).toWalletPolicy(stored)
        } catch (cause: Throwable) {
            throw cause.toKeyUseAuthorizationException(stored.id.value) ?: cause
        }
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

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    private fun biometricAvailabilityFailure(): KeyUseAuthorizationUnsupportedReason? = memScoped {
        val error = alloc<ObjCObjectVar<platform.Foundation.NSError?>>()
        val available = LAContext().canEvaluatePolicy(
            LAPolicyDeviceOwnerAuthenticationWithBiometrics,
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
        reuseEnforcement = if (this is KeyUseAuthorizationPolicy.BiometricTimedReuse) {
            KeyUseAuthorizationReuseEnforcement.ProviderProcess
        } else {
            null
        },
        timeoutValidation = if (this is KeyUseAuthorizationPolicy.BiometricTimedReuse) {
            KeyUseAuthorizationReuseTimeoutValidation.ProviderConfigurationOnly
        } else {
            null
        },
    )

private val isSimulator: Boolean by lazy {
    NSProcessInfo.processInfo.environment.keys.any { it == "SIMULATOR_UDID" || it == "SIMULATOR_DEVICE_NAME" }
}
