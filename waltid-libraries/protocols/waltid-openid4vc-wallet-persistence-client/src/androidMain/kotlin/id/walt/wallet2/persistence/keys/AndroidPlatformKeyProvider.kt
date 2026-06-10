package id.walt.wallet2.persistence.keys

import id.walt.crypto.AndroidKey
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class AndroidPlatformKeyProvider : PlatformKeyProvider {

    override val supportedHardwareKeyTypes: Set<KeyType> =
        setOf(KeyType.secp256r1, KeyType.RSA)

    override val isHardwareBackingAvailable: Boolean = true

    override fun isHardwareBacked(keyType: KeyType): Boolean = keyType in supportedHardwareKeyTypes

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun generateKey(keyType: KeyType, keyId: String?): Key {
        val alias = keyId ?: "wallet_key_${Uuid.random()}"
        val options = AndroidKey.Options(kid = alias, keyType = keyType)
        return if (isHardwareBacked(keyType)) {
            AndroidKey.Hardware.create(options)
        } else {
            AndroidKey.Software.create(options)
        }
    }

    override suspend fun loadKey(keyId: String, keyType: KeyType): Key? = runCatching {
        AndroidKey.Hardware.load(AndroidKey.Options(kid = keyId, keyType = keyType))
    }.getOrNull()

    override suspend fun loadSoftwareKey(keyId: String, keyType: KeyType, jwkMaterial: ByteArray): Key? = runCatching {
        AndroidKey.Software.load(AndroidKey.Options(kid = keyId, keyType = keyType), jwkMaterial)
    }.getOrNull()

    override suspend fun exportSoftwareKeyMaterial(key: Key): ByteArray {
        require(key is AndroidKey.Software) { "Can only export material from Software keys" }
        return AndroidKey.Software.exportKeyMaterial(key)
    }

    override suspend fun deleteKey(keyId: String, keyType: KeyType): Boolean = runCatching {
        if (isHardwareBacked(keyType)) {
            AndroidKey.Hardware.delete(keyId)
        }
    }.isSuccess
}
