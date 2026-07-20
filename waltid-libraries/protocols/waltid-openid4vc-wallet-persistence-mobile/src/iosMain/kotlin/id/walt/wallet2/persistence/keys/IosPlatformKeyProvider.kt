package id.walt.wallet2.persistence.keys

import id.walt.crypto.IosKey
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.KeyUseAuthorizationException
import id.walt.crypto.keys.KeyUseAuthorizationFailure
import id.walt.crypto.keys.KeyUseAuthorizationPolicy
import id.walt.crypto.keys.KeyUseAuthorizationPrompt
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.BetaInteropApi
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

/**
 * [PlatformKeyProvider] implementation backed by iOS Keychain and Secure Enclave.
 *
 * @param useSecureElement When `true`, P-256 keys are created in Secure Enclave where available.
 */
public class IosPlatformKeyProvider(
    private val useSecureElement: Boolean = true,
    private val authorizationPrompt: KeyUseAuthorizationPrompt = KeyUseAuthorizationPrompt(),
) : PlatformKeyProvider {

    /**
     * iOS platform-backed key types supported by this provider.
     */
    override val supportedPlatformKeyTypes: Set<KeyType> =
        PlatformKeyProvider.DEFAULT_SUPPORTED_PLATFORM_KEY_TYPES

    /**
     * Generates an iOS platform-backed key for supported types, otherwise a software key.
     */
    override suspend fun generateKey(keyType: KeyType, keyId: String?): Key {
        return generateKey(PlatformKeyGenerationRequest(keyType = keyType, keyId = keyId))
    }

    override suspend fun generateKey(request: PlatformKeyGenerationRequest): Key {
        val capability = capability(request.keyType, request.keyUseAuthorizationPolicy)
        if (!capability.supported) {
            throw KeyUseAuthorizationException(
                failure = capability.failure ?: KeyUseAuthorizationFailure.UnsupportedCombination,
                message = "iOS cannot enforce ${request.keyUseAuthorizationPolicy} for ${request.keyType}",
            )
        }

        val kid = request.keyId ?: Uuid.random().toString()
        val options = IosKey.Options(
            kid = kid,
            keyType = request.keyType,
            inSecureElement = useSecureElement && request.keyType == KeyType.secp256r1,
            keyUseAuthorizationPolicy = request.keyUseAuthorizationPolicy,
            authorizationPrompt = authorizationPrompt,
        )
        return if (isPlatformBacked(request.keyType)) {
            IosKey.Platform.create(options)
        } else {
            IosKey.Software.create(options)
        }
    }

    /**
     * Loads an iOS key by identifier and expected key type.
     */
    override suspend fun loadKey(keyId: String, keyType: KeyType): Key? =
        loadKey(keyId, keyType, KeyUseAuthorizationPolicy.None)

    override suspend fun loadKey(
        keyId: String,
        keyType: KeyType,
        keyUseAuthorizationPolicy: KeyUseAuthorizationPolicy,
    ): Key? {
        val options = IosKey.Options(
            kid = keyId,
            keyType = keyType,
            inSecureElement = useSecureElement && keyType == KeyType.secp256r1,
            keyUseAuthorizationPolicy = keyUseAuthorizationPolicy,
            authorizationPrompt = authorizationPrompt,
        )
        return if (keyUseAuthorizationPolicy == KeyUseAuthorizationPolicy.None) {
            runCatching { IosKey.Platform.load(options) }.getOrNull()
        } else {
            IosKey.Platform.load(options)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun capability(
        keyType: KeyType,
        keyUseAuthorizationPolicy: KeyUseAuthorizationPolicy,
    ): PlatformKeyCapability {
        if (keyUseAuthorizationPolicy == KeyUseAuthorizationPolicy.None) {
            val supported = keyType in supportedPlatformKeyTypes ||
                keyType in PlatformKeyProvider.DEFAULT_SUPPORTED_SOFTWARE_KEY_TYPES
            return PlatformKeyCapability(
                platform = PlatformKeyPlatform.iOS,
                keyType = keyType,
                keyUseAuthorizationPolicy = keyUseAuthorizationPolicy,
                supported = supported,
                platformBackingAvailable = isPlatformBacked(keyType),
                secureHardwareRequired = false,
                secureHardwareAvailable = null,
                failure = KeyUseAuthorizationFailure.UnsupportedCombination.takeUnless { supported },
            )
        }

        val secureHardwareAvailable = !isSimulator()
        val failure = when {
            keyType != KeyType.secp256r1 || !useSecureElement ->
                KeyUseAuthorizationFailure.UnsupportedCombination
            !secureHardwareAvailable -> KeyUseAuthorizationFailure.BiometricUnavailable
            else -> biometricAvailabilityFailure()
        }

        return PlatformKeyCapability(
            platform = PlatformKeyPlatform.iOS,
            keyType = keyType,
            keyUseAuthorizationPolicy = keyUseAuthorizationPolicy,
            supported = failure == null,
            platformBackingAvailable = true,
            secureHardwareRequired = true,
            secureHardwareAvailable = secureHardwareAvailable,
            failure = failure,
        )
    }

    /**
     * Loads an iOS software key from serialized JWK material.
     */
    override suspend fun loadSoftwareKey(keyId: String, keyType: KeyType, jwkMaterial: ByteArray): Key? = runCatching {
        IosKey.Software.load(IosKey.Options(kid = keyId, keyType = keyType), jwkMaterial)
    }.getOrNull()

    /**
     * Exports serialized JWK material from an iOS software key.
     */
    override suspend fun exportSoftwareKeyMaterial(key: Key): ByteArray {
        require(key is IosKey.Software) { "Can only export material from Software keys" }
        return IosKey.Software.exportKeyMaterial(key)
    }

    /**
     * Deletes an iOS key by identifier and expected key type.
     */
    override suspend fun deleteKey(keyId: String, keyType: KeyType): Boolean = runCatching {
        if (isPlatformBacked(keyType)) {
            IosKey.Platform.delete(keyId)
        }
    }.isSuccess

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
