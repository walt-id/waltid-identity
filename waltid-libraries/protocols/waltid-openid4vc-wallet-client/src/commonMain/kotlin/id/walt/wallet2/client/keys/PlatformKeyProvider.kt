package id.walt.wallet2.client.keys

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType

interface PlatformKeyProvider {
    suspend fun generateKey(keyType: KeyType, keyId: String? = null): Key
    suspend fun loadKey(keyId: String, keyType: KeyType): Key?
    suspend fun deleteKey(keyId: String, keyType: KeyType): Boolean
    val supportedHardwareKeyTypes: Set<KeyType>
    val isHardwareBackingAvailable: Boolean
}
