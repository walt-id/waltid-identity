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
import id.walt.crypto2.signum.isBiometricCurrentSet
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
    private val signumProvider = SignumManagedKeyProvider(IosSignumKeyBackend())

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun preflight(request: PlatformKeyRequest): PlatformKeyPreflight {
        if (request.authorizationPolicy == KeyUseAuthorizationPolicy.None) {
            return PlatformKeyPreflight(true)
        }
        val failure = when {
            request.spec != KeySpec.Ec(EcCurve.P256) ||
                request.usages != setOf(KeyUsage.SIGN, KeyUsage.VERIFY) ->
                KeyUseAuthorizationFailure.UnsupportedCombination
            isSimulator -> KeyUseAuthorizationFailure.BiometricUnavailable
            else -> biometricAvailabilityFailure()
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
            hardware = SignumHardwarePolicy.REQUIRED,
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

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    private fun biometricAvailabilityFailure(): KeyUseAuthorizationFailure? = memScoped {
        val error = alloc<ObjCObjectVar<platform.Foundation.NSError?>>()
        val available = LAContext().canEvaluatePolicy(
            LAPolicyDeviceOwnerAuthenticationWithBiometrics,
            error.ptr,
        )
        if (available) return@memScoped null
        when (error.value?.code) {
            LAErrorBiometryNotEnrolled -> KeyUseAuthorizationFailure.BiometricNotEnrolled
            LAErrorBiometryNotAvailable -> KeyUseAuthorizationFailure.BiometricUnavailable
            else -> KeyUseAuthorizationFailure.BiometricUnavailable
        }
    }
}

private fun SignumKeyPolicy.toWalletPolicy(): KeyUseAuthorizationPolicy = when (val authentication = authentication) {
    SignumAuthenticationPolicy.None -> KeyUseAuthorizationPolicy.None
    is SignumAuthenticationPolicy.UserPresence -> if (
        authentication.isBiometricCurrentSet() && hardware == SignumHardwarePolicy.REQUIRED
    ) {
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
