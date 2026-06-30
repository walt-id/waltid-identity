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
     * iOS hardware-backed key types supported by this provider.
     */
    override val supportedHardwareKeyTypes: Set<KeyType> = setOf(KeyType.secp256r1)

    /**
     * iOS Keychain/Secure Enclave storage is treated as available for supported iOS wallet builds.
     */
    override val isHardwareBackingAvailable: Boolean = true

    /**
     * Generates an iOS key using Keychain or Secure Enclave according to [useSecureElement].
     */
    override suspend fun generateKey(keyType: KeyType, keyId: String?): Key {
        val kid = keyId ?: Uuid.random().toString()
        val options = IosKey.Options(
            kid = kid,
            keyType = keyType,
            inSecureElement = useSecureElement && keyType == KeyType.secp256r1,
        )
        return IosKey.create(options)
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
        IosKey.load(options)
    }.getOrNull()

    /**
     * Deletes an iOS key by identifier and expected key type.
     */
    override suspend fun deleteKey(keyId: String, keyType: KeyType): Boolean = runCatching {
        IosKey.delete(keyId, keyType)
    }.isSuccess
}
