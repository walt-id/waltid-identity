package id.walt.wallet2.persistence.keys

import id.walt.crypto.IosKey
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.KeyUseAuthorizationException
import id.walt.crypto.keys.KeyUseAuthorizationFailure
import id.walt.crypto.keys.KeyUseAuthorizationPolicy
import id.walt.crypto.keys.KeyUseAuthorizationPrompt
import id.walt.wallet2.persistence.stores.MobileWalletKeyRecord
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.Foundation.NSError
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAErrorBiometryNotAvailable
import platform.LocalAuthentication.LAErrorBiometryNotEnrolled
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics
import kotlin.uuid.Uuid

/** Keychain/Secure Enclave-backed mobile key provider. */
public class IosPlatformKeyProvider(
    private val authorizationPrompt: KeyUseAuthorizationPrompt = KeyUseAuthorizationPrompt(),
) : PlatformKeyProvider {
    @OptIn(ExperimentalForeignApi::class)
    override suspend fun preflight(request: PlatformKeyRequest): PlatformKeyPreflight {
        if (request.keyUseAuthorizationPolicy == KeyUseAuthorizationPolicy.None) {
            val supported = request.keyType in PlatformKeyProvider.DEFAULT_SUPPORTED_PLATFORM_KEY_TYPES ||
                request.keyType in PlatformKeyProvider.DEFAULT_SUPPORTED_SOFTWARE_KEY_TYPES
            return PlatformKeyPreflight(supported, KeyUseAuthorizationFailure.UnsupportedCombination.takeUnless { supported })
        }
        val failure = when {
            request.keyType != KeyType.secp256r1 -> KeyUseAuthorizationFailure.UnsupportedCombination
            isSimulator() -> KeyUseAuthorizationFailure.BiometricUnavailable
            else -> biometricAvailabilityFailure()
        }
        return PlatformKeyPreflight(failure == null, failure)
    }

    override suspend fun generate(request: PlatformKeyRequest): GeneratedPlatformKey {
        val preflight = preflight(request)
        if (!preflight.supported) {
            throw KeyUseAuthorizationException(
                failure = preflight.failure ?: KeyUseAuthorizationFailure.UnsupportedCombination,
                message = "iOS cannot enforce ${request.keyUseAuthorizationPolicy} for ${request.keyType}",
            )
        }
        val kid = request.keyId ?: Uuid.random().toString()
        val platformBacked = request.keyType in PlatformKeyProvider.DEFAULT_SUPPORTED_PLATFORM_KEY_TYPES
        val key = if (platformBacked) {
            IosKey.Platform.create(
                IosKey.Options(
                    kid = kid,
                    keyType = request.keyType,
                    inSecureElement = request.keyUseAuthorizationPolicy == KeyUseAuthorizationPolicy.BiometricCurrentSet,
                    keyUseAuthorizationPolicy = request.keyUseAuthorizationPolicy,
                    authorizationPrompt = authorizationPrompt,
                )
            )
        } else {
            IosKey.Software.create(IosKey.Options(kid = kid, keyType = request.keyType))
        }
        return GeneratedPlatformKey(
            key = key,
            record = MobileWalletKeyRecord(
                keyId = kid,
                keyType = request.keyType,
                keyUseAuthorizationPolicy = request.keyUseAuthorizationPolicy,
                isPlatformBacked = platformBacked,
            ),
        )
    }

    override suspend fun load(record: MobileWalletKeyRecord): Key? {
        val options = IosKey.Options(
            kid = record.keyId,
            keyType = record.keyType,
            inSecureElement = record.keyUseAuthorizationPolicy == KeyUseAuthorizationPolicy.BiometricCurrentSet,
            keyUseAuthorizationPolicy = record.keyUseAuthorizationPolicy,
            authorizationPrompt = authorizationPrompt,
        )
        return if (record.isPlatformBacked) IosKey.Platform.load(options) else null
    }

    override suspend fun delete(record: MobileWalletKeyRecord): Boolean = runCatching {
        if (record.isPlatformBacked) IosKey.Platform.delete(record.keyId)
    }.isSuccess

    override suspend fun loadSoftwareKey(keyId: String, keyType: KeyType, jwkMaterial: ByteArray): Key? = runCatching {
        IosKey.Software.load(IosKey.Options(kid = keyId, keyType = keyType), jwkMaterial)
    }.getOrNull()

    override suspend fun exportSoftwareKeyMaterial(key: Key): ByteArray {
        require(key is IosKey.Software) { "Can only export material from software keys" }
        return IosKey.Software.exportKeyMaterial(key)
    }

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    private fun biometricAvailabilityFailure(): KeyUseAuthorizationFailure? = memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
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

    private fun isSimulator(): Boolean = isIosSimulatorTarget()
}

internal expect fun isIosSimulatorTarget(): Boolean
