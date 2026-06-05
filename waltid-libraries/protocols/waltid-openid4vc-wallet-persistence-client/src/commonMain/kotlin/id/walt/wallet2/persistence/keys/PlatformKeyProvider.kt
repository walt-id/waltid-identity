package id.walt.wallet2.persistence.keys

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType

interface PlatformKeyProvider {
    suspend fun generateKey(keyType: KeyType, keyId: String? = null): Key
    suspend fun loadKey(keyId: String, keyType: KeyType): Key?
    suspend fun loadSoftwareKey(keyId: String, keyType: KeyType, jwkMaterial: ByteArray): Key?
    suspend fun exportSoftwareKeyMaterial(key: Key): ByteArray
    suspend fun deleteKey(keyId: String, keyType: KeyType): Boolean
    fun isHardwareBacked(keyType: KeyType): Boolean
    val supportedHardwareKeyTypes: Set<KeyType>
    val isHardwareBackingAvailable: Boolean
}
