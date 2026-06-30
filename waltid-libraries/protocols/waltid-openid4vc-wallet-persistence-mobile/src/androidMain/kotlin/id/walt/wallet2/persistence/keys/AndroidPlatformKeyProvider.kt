package id.walt.wallet2.persistence.keys

import id.walt.crypto.keys.AndroidKey
import id.walt.crypto.keys.AndroidKeyGenerator
import id.walt.crypto.keys.AndroidKeyParameters
import id.walt.crypto.keys.AndroidKeystoreLoader
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyAlias
import id.walt.crypto.keys.KeyType
import kotlin.uuid.Uuid

/**
 * [PlatformKeyProvider] implementation backed by Android KeyStore.
 */
class AndroidPlatformKeyProvider : PlatformKeyProvider {

    /**
     * Android KeyStore-backed key types supported by this provider.
     */
    override val supportedHardwareKeyTypes: Set<KeyType> =
        setOf(KeyType.secp256r1, KeyType.RSA)

    /**
     * Android KeyStore is treated as available for supported Android wallet builds.
     */
    override val isHardwareBackingAvailable: Boolean = true

    /**
     * Generates an Android KeyStore key and returns its walt.id crypto wrapper.
     */
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

    /**
     * Loads an Android KeyStore key by alias and expected key type.
     */
    override suspend fun loadKey(keyId: String, keyType: KeyType): Key? =
        AndroidKeystoreLoader.load(type = keyType, keyId = keyId)

    /**
     * Deletes an Android KeyStore key by alias and expected key type.
     */
    override suspend fun deleteKey(keyId: String, keyType: KeyType): Boolean = runCatching {
        AndroidKey(KeyAlias(keyId), keyType).deleteKey()
    }.getOrDefault(false)
}
