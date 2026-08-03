package id.walt.wallet2.persistence.keys

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.KeyUseAuthorizationFailure
import id.walt.wallet2.persistence.stores.MobileWalletKeyRecord

/**
 * Platform abstraction for creating, loading, and deleting mobile wallet signing keys.
 */
public interface PlatformKeyProvider {
    public suspend fun preflight(request: PlatformKeyRequest): PlatformKeyPreflight

    public suspend fun generate(request: PlatformKeyRequest): GeneratedPlatformKey

    public suspend fun load(record: MobileWalletKeyRecord): Key?

    public suspend fun delete(record: MobileWalletKeyRecord): Boolean

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
     * Shared defaults for platform key providers.
     */
    public companion object {
        /**
         * Default platform-backed key types shared by mobile providers.
         */
        public val DEFAULT_SUPPORTED_PLATFORM_KEY_TYPES: Set<KeyType> =
            setOf(KeyType.secp256r1, KeyType.secp384r1, KeyType.secp521r1, KeyType.RSA)

        internal val DEFAULT_SUPPORTED_SOFTWARE_KEY_TYPES: Set<KeyType> =
            setOf(KeyType.Ed25519, KeyType.secp256k1)
    }
}
