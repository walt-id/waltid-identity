package id.walt.wallet2.persistence.keys

import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.ManagedKey
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.providers.GenerateManagedKeyRequest
import id.walt.crypto2.signum.IosSignumKeyBackend
import id.walt.crypto2.signum.SignumAuthenticationPolicy
import id.walt.crypto2.signum.SignumHardwarePolicy
import id.walt.crypto2.signum.SignumKeyNotFoundException
import id.walt.crypto2.signum.SignumKeyOptions
import id.walt.crypto2.signum.SignumKeyPolicy
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
            isSimulator -> PlatformKeyUnsupportedReason.BiometricUnavailable
            else -> biometricAvailabilityFailure()
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
            hardware = SignumHardwarePolicy.REQUIRED,
            authentication = SignumAuthenticationPolicy.BiometricCurrentSet(
                reason = prompt.reason,
                cancelText = prompt.cancelText,
            ),
        )
    }

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    private fun biometricAvailabilityFailure(): PlatformKeyUnsupportedReason? = memScoped {
        val error = alloc<ObjCObjectVar<platform.Foundation.NSError?>>()
        val available = LAContext().canEvaluatePolicy(
            LAPolicyDeviceOwnerAuthenticationWithBiometrics,
            error.ptr,
        )
        if (available) return@memScoped null
        when (error.value?.code) {
            LAErrorBiometryNotEnrolled -> PlatformKeyUnsupportedReason.BiometricNotEnrolled
            LAErrorBiometryNotAvailable -> PlatformKeyUnsupportedReason.BiometricUnavailable
            else -> PlatformKeyUnsupportedReason.BiometricUnavailable
        }
    }

    private fun KeyUseAuthorizationPolicy.toSignumPolicy(): SignumKeyPolicy = when (this) {
        KeyUseAuthorizationPolicy.None -> SignumKeyPolicy()
        KeyUseAuthorizationPolicy.BiometricCurrentSet -> SignumKeyPolicy(
            hardware = SignumHardwarePolicy.REQUIRED,
            authentication = SignumAuthenticationPolicy.BiometricCurrentSet(),
        )
    }
}

private fun SignumKeyPolicy.toWalletPolicy(): KeyUseAuthorizationPolicy = when (val authentication = authentication) {
    SignumAuthenticationPolicy.None -> KeyUseAuthorizationPolicy.None
    is SignumAuthenticationPolicy.BiometricCurrentSet -> if (hardware == SignumHardwarePolicy.REQUIRED) {
        KeyUseAuthorizationPolicy.BiometricCurrentSet
    } else {
        throw KeyUseAuthorizationException(
            KeyUseAuthorizationFailure.InvalidStoredKeyMetadata,
            "Stored Signum key uses an unsupported iOS authentication policy",
        )
    }
}

private val isSimulator: Boolean by lazy {
    NSProcessInfo.processInfo.environment.keys.any { it == "SIMULATOR_UDID" || it == "SIMULATOR_DEVICE_NAME" }
}
