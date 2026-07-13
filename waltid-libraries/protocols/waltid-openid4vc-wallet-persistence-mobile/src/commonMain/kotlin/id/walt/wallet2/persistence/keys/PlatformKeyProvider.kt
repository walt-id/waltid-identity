package id.walt.wallet2.persistence.keys

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType

/**
 * Platform abstraction for creating, loading, and deleting mobile wallet signing keys.
 */
public interface PlatformKeyProvider {
    /**
     * Creates a new key, using platform-backed storage when this provider supports [keyType].
     *
     * @param keyType Key type to create.
     * @param keyId Optional platform key identifier. When omitted, the provider generates one.
     * @return The created key.
     */
    public suspend fun generateKey(keyType: KeyType, keyId: String? = null): Key

    /**
     * Loads a previously generated key.
     *
     * @param keyId Platform key identifier.
     * @param keyType Expected key type for the stored key.
     * @return The loaded key, or `null` when the platform store has no matching key.
     */
    public suspend fun loadKey(keyId: String, keyType: KeyType): Key?

    /**
     * Loads a serialized software key for platforms that need a non-platform-backed fallback.
     *
     * @param keyId Wallet-local key identifier to assign to the loaded key.
     * @param keyType Expected key type for the serialized key material.
     * @param jwkMaterial Serialized JWK key material.
     * @return The loaded software key, or `null` when the material cannot be loaded.
     */
    public suspend fun loadSoftwareKey(keyId: String, keyType: KeyType, jwkMaterial: ByteArray): Key?

    /**
     * Exports serialized JWK material from a software key.
     *
     * @param key Software key to export.
     * @return Serialized JWK key material.
     */
    public suspend fun exportSoftwareKeyMaterial(key: Key): ByteArray

    /**
     * Deletes a key from the platform key store.
     *
     * @param keyId Platform key identifier.
     * @param keyType Key type of the stored key.
     * @return `true` when deletion succeeded or the platform reported success.
     */
    public suspend fun deleteKey(keyId: String, keyType: KeyType): Boolean

    /**
     * Returns whether [keyType] is created with platform-backed storage by this provider.
     */
    public fun isPlatformBacked(keyType: KeyType): Boolean = keyType in supportedPlatformKeyTypes

    /**
     * Key types that this provider can create with platform-backed storage.
     */
    public val supportedPlatformKeyTypes: Set<KeyType>

    /**
     * Whether the current device and provider can use platform-backed storage.
     */
    public val isPlatformBackingAvailable: Boolean get() = true

    /**
     * Shared defaults for platform key providers.
     */
    public companion object {
        /**
         * Default platform-backed key types shared by mobile providers.
         */
        public val DEFAULT_SUPPORTED_PLATFORM_KEY_TYPES: Set<KeyType> =
            setOf(KeyType.secp256r1, KeyType.secp384r1, KeyType.secp521r1, KeyType.RSA)
    }
}
