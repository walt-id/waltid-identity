package id.walt.wallet2.persistence.keys

import id.walt.crypto.AndroidKey
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import kotlin.uuid.Uuid

class AndroidPlatformKeyProvider : PlatformKeyProvider {

    override val supportedPlatformKeyTypes: Set<KeyType> =
        PlatformKeyProvider.DEFAULT_SUPPORTED_PLATFORM_KEY_TYPES

    override suspend fun generateKey(keyType: KeyType, keyId: String?): Key {
        val alias = keyId ?: "wallet_key_${Uuid.random()}"
        val options = AndroidKey.Options(kid = alias, keyType = keyType)
        return if (isPlatformBacked(keyType)) {
            AndroidKey.Platform.create(options)
        } else {
            AndroidKey.Software.create(options)
        }
    }

    override suspend fun loadKey(keyId: String, keyType: KeyType): Key? = runCatching {
        AndroidKey.Platform.load(AndroidKey.Options(kid = keyId, keyType = keyType))
    }.getOrNull()

    override suspend fun loadSoftwareKey(keyId: String, keyType: KeyType, jwkMaterial: ByteArray): Key? = runCatching {
        AndroidKey.Software.load(AndroidKey.Options(kid = keyId, keyType = keyType), jwkMaterial)
    }.getOrNull()

    override suspend fun exportSoftwareKeyMaterial(key: Key): ByteArray {
        require(key is AndroidKey.Software) { "Can only export material from Software keys" }
        return AndroidKey.Software.exportKeyMaterial(key)
    }

    override suspend fun deleteKey(keyId: String, keyType: KeyType): Boolean = runCatching {
        if (isPlatformBacked(keyType)) {
            AndroidKey.Platform.delete(keyId)
        }
    }.isSuccess
}
