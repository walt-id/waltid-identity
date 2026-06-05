package id.walt.wallet2.persistence.keys

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

    override fun isHardwareBacked(keyType: KeyType): Boolean = keyType in supportedHardwareKeyTypes

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun generateKey(keyType: KeyType, keyId: String?): Key {
        val kid = keyId ?: Uuid.random().toString()
        val options = IosKey.Options(
            kid = kid,
            keyType = keyType,
            inSecureElement = useSecureElement && keyType == KeyType.secp256r1,
        )
        return if (isHardwareBacked(keyType)) {
            IosKey.Hardware.create(options)
        } else {
            IosKey.Software.create(options)
        }
    }

    override suspend fun loadKey(keyId: String, keyType: KeyType): Key? = runCatching {
        val options = IosKey.Options(
            kid = keyId,
            keyType = keyType,
            inSecureElement = useSecureElement && keyType == KeyType.secp256r1,
        )
        IosKey.Hardware.load(options)
    }.getOrNull()

    override suspend fun loadSoftwareKey(keyId: String, keyType: KeyType, jwkMaterial: ByteArray): Key? = runCatching {
        IosKey.Software.load(IosKey.Options(kid = keyId, keyType = keyType), jwkMaterial)
    }.getOrNull()

    override suspend fun exportSoftwareKeyMaterial(key: Key): ByteArray {
        require(key is IosKey.Software) { "Can only export material from Software keys" }
        return IosKey.Software.exportKeyMaterial(key)
    }

    override suspend fun deleteKey(keyId: String, keyType: KeyType): Boolean = runCatching {
        if (isHardwareBacked(keyType)) {
            IosKey.Hardware.delete(keyId)
        }
    }.isSuccess
}
