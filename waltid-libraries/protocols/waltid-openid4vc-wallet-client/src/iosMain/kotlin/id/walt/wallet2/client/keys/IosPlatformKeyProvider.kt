package id.walt.wallet2.client.keys

import id.walt.crypto.IosKey
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class IosPlatformKeyProvider(
    private val useSecureElement: Boolean = true,
) : PlatformKeyProvider {

    override val supportedHardwareKeyTypes: Set<KeyType> = setOf(KeyType.secp256r1)

    override val isHardwareBackingAvailable: Boolean = true

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun generateKey(keyType: KeyType, keyId: String?): Key {
        val kid = keyId ?: Uuid.random().toString()
        val options = IosKey.Options(
            kid = kid,
            keyType = keyType,
            inSecureElement = useSecureElement && keyType == KeyType.secp256r1,
        )
        return IosKey.create(options)
    }

    override suspend fun loadKey(keyId: String, keyType: KeyType): Key? = runCatching {
        val options = IosKey.Options(
            kid = keyId,
            keyType = keyType,
            inSecureElement = useSecureElement && keyType == KeyType.secp256r1,
        )
        IosKey.load(options)
    }.getOrNull()

    override suspend fun deleteKey(keyId: String, keyType: KeyType): Boolean = runCatching {
        IosKey.delete(keyId, keyType)
    }.isSuccess
}
