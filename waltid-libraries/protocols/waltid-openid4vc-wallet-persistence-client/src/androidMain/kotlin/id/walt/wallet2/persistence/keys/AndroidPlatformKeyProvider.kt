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

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun generateKey(keyType: KeyType, keyId: String?): Key {
        require(keyType in supportedHardwareKeyTypes) {
            "KeyType $keyType is not supported in Android KeyStore. Supported: $supportedHardwareKeyTypes"
        }
        val alias = keyId ?: "wallet_key_${Uuid.random()}"
        return AndroidKey.create(AndroidKey.Options(kid = alias, keyType = keyType))
    }

    override suspend fun loadKey(keyId: String, keyType: KeyType): Key? = runCatching {
        AndroidKey.load(AndroidKey.Options(kid = keyId, keyType = keyType))
    }.getOrNull()

    override suspend fun deleteKey(keyId: String, keyType: KeyType): Boolean = runCatching {
        AndroidKey.delete(keyId, keyType)
    }.isSuccess
}
