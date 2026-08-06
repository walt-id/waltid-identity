package id.walt.wallet2.persistence.keys

import id.walt.crypto2.keys.ManagedKey
import id.walt.crypto2.keys.StoredKey

/**
 * Platform-managed key provider used by mobile wallet persistence.
 *
 * Implementations must expose capability and restoration state through the structured result
 * types below. Known key-use authorization failures must be surfaced as
 * [KeyUseAuthorizationException]; callers must not need to understand provider-specific
 * exceptions.
 */
public interface PlatformManagedKeyProvider {
    /** Checks whether the exact requirements can be enforced without fallback. */
    public suspend fun preflight(requirements: WalletKeyRequirements): KeyUseAuthorizationSupport

    /** Generates a managed key in the platform key store. */
    public suspend fun generateManagedKey(request: WalletKeyCreationRequest): ManagedKey

    /**
     * Restores a platform key from its persisted descriptor.
     *
     * Returns a structured restoration result so persisted authorization policy is available
     * even when the native key is absent.
     */
    public suspend fun restoreManagedKey(stored: StoredKey.Managed): PlatformManagedKeyRestoration

    /**
     * Deletes a platform key using its descriptor without restoring the alias first.
     */
    public suspend fun deleteManagedKey(stored: StoredKey.Managed)

}
