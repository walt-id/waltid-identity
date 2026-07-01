package id.walt.wallet2.persistence.keys

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType

interface PlatformKeyProvider {
    suspend fun generateKey(keyType: KeyType, keyId: String? = null): Key
    suspend fun loadKey(keyId: String, keyType: KeyType): Key?
    suspend fun loadSoftwareKey(keyId: String, keyType: KeyType, jwkMaterial: ByteArray): Key?
    suspend fun exportSoftwareKeyMaterial(key: Key): ByteArray
    suspend fun deleteKey(keyId: String, keyType: KeyType): Boolean
    fun isPlatformBacked(keyType: KeyType): Boolean = keyType in supportedPlatformKeyTypes
    val supportedPlatformKeyTypes: Set<KeyType>
    val isPlatformBackingAvailable: Boolean get() = true

    companion object {
        val DEFAULT_SUPPORTED_PLATFORM_KEY_TYPES: Set<KeyType> =
            setOf(KeyType.secp256r1, KeyType.secp384r1, KeyType.secp521r1, KeyType.RSA)
    }
}
