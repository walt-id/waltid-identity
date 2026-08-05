package id.walt.wallet2.persistence.keys

import id.walt.crypto2.keys.ManagedKey
import id.walt.crypto2.keys.StoredKey

/**
 * Generation, restoration, and deletion support for managed native platform keys.
 */
public interface PlatformManagedKeyProvider {
    /**
     * Checks whether the exact request can be enforced without a fallback.
     */
    public suspend fun preflight(request: PlatformKeyRequest): PlatformKeyPreflight

    /** Generates a managed key in the platform key store. */
    public suspend fun generateManagedKey(request: PlatformKeyRequest): ManagedKey

    /**
     * Restores a platform key from its persisted descriptor.
     *
     * Returns null only when the native alias is known to be absent.
     */
    public suspend fun restoreManagedKey(stored: StoredKey.Managed): ManagedKey?

    /**
     * Deletes a platform key using its descriptor without restoring the alias first.
     */
    public suspend fun deleteManagedKey(stored: StoredKey.Managed)

    /** Reads wallet-facing metadata from a persisted descriptor without loading the native key. */
    public fun inspectManagedKey(stored: StoredKey.Managed): PlatformManagedKeyInfo
}
