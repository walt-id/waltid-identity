package id.walt.wallet2.persistence.keys

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType

/**
 * Platform abstraction for creating, loading, and deleting mobile wallet signing keys.
 */
interface PlatformKeyProvider {
    /**
     * Creates a new platform-backed key.
     *
     * @param keyType Key type to create.
     * @param keyId Optional platform key identifier. When omitted, the provider generates one.
     * @return The created key.
     */
    suspend fun generateKey(keyType: KeyType, keyId: String? = null): Key

    /**
     * Loads a previously generated key.
     *
     * @param keyId Platform key identifier.
     * @param keyType Expected key type for the stored key.
     * @return The loaded key, or `null` when the platform store has no matching key.
     */
    suspend fun loadKey(keyId: String, keyType: KeyType): Key?

    /**
     * Deletes a key from the platform key store.
     *
     * @param keyId Platform key identifier.
     * @param keyType Key type of the stored key.
     * @return `true` when deletion succeeded or the platform reported success.
     */
    suspend fun deleteKey(keyId: String, keyType: KeyType): Boolean

    /**
     * Key types that this provider can create with platform-backed storage.
     */
    val supportedHardwareKeyTypes: Set<KeyType>

    /**
     * Whether the current device and provider can use hardware-backed storage.
     */
    val isHardwareBackingAvailable: Boolean
}
