package id.walt.wallet2.persistence.keys

import id.walt.crypto.keys.AndroidKey
import id.walt.crypto.keys.AndroidKeyGenerator
import id.walt.crypto.keys.AndroidKeyParameters
import id.walt.crypto.keys.AndroidKeystoreLoader
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyAlias
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
        return AndroidKeyGenerator.generate(
            type = keyType,
            metadata = AndroidKeyParameters(keyId = alias),
        )
    }

    override suspend fun loadKey(keyId: String, keyType: KeyType): Key? =
        AndroidKeystoreLoader.load(type = keyType, keyId = keyId)

    override suspend fun deleteKey(keyId: String, keyType: KeyType): Boolean = runCatching {
        AndroidKey(KeyAlias(keyId), keyType).deleteKey()
    }.getOrDefault(false)
}
