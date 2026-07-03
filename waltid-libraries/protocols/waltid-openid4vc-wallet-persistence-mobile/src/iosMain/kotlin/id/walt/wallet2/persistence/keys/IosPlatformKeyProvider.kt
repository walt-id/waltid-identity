package id.walt.wallet2.persistence.keys

import id.walt.crypto.IosKey
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import kotlin.uuid.Uuid

/**
 * [PlatformKeyProvider] implementation backed by iOS Keychain and Secure Enclave.
 *
 * @param useSecureElement When `true`, P-256 keys are created in Secure Enclave where available.
 */
class IosPlatformKeyProvider(
    private val useSecureElement: Boolean = true,
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
        val kid = keyId ?: Uuid.random().toString()
        val options = IosKey.Options(
            kid = kid,
            keyType = keyType,
            inSecureElement = useSecureElement && keyType == KeyType.secp256r1,
        )
        return if (isPlatformBacked(keyType)) {
            IosKey.Platform.create(options)
        } else {
            IosKey.Software.create(options)
        }
    }

    /**
     * Loads an iOS key by identifier and expected key type.
     */
    override suspend fun loadKey(keyId: String, keyType: KeyType): Key? = runCatching {
        val options = IosKey.Options(
            kid = keyId,
            keyType = keyType,
            inSecureElement = useSecureElement && keyType == KeyType.secp256r1,
        )
        IosKey.Platform.load(options)
    }.getOrNull()

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
}
