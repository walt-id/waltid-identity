package id.walt.wallet2.persistence.keys

import id.walt.crypto2.keys.KeyUseAuthorizationPolicy
import id.walt.crypto2.keys.KeyUseAuthorizationException
import id.walt.crypto2.keys.KeyUseAuthorizationSupport
import id.walt.crypto2.keys.PlatformKeyFacts

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
    /** Checks whether the requested requirements are supported without fallback. */
    public suspend fun preflight(requirements: WalletKeyRequirements): KeyUseAuthorizationSupport

    /** Generates a managed key in the platform key store. */
    public suspend fun generateManagedKey(request: WalletKeyCreationRequest): ManagedKey

    /** Whether this native backend supports importing a private key with these requirements. */
    public fun supportsPrivateKeyImport(requirements: WalletKeyRequirements): Boolean = false

    /** Imports private material into native storage. Implementations must verify the public key. */
    public suspend fun importManagedKey(request: WalletKeyCreationRequest,
        material: id.walt.crypto2.keys.EncodedKey.Jwk): ManagedKey =
        throw UnsupportedOperationException("Native private-key import is unavailable")

    /** Inspects actual key origin and protection without claiming certification. */
    public suspend fun keyFacts(stored: StoredKey.Managed): PlatformKeyFacts = PlatformKeyFacts()

    /** Reads the immutable wallet authorization policy encoded in a managed-key descriptor. */
    public fun keyUseAuthorizationPolicy(stored: StoredKey.Managed): KeyUseAuthorizationPolicy

    /**
     * Restores a platform key from its persisted descriptor.
     *
     * Returns a structured restoration result so persisted authorization policy is available
     * even when the native key is absent.
     */
    public suspend fun restoreManagedKey(stored: StoredKey.Managed): PlatformManagedKeyRestoration

    /** Cleans up an alias reserved by an interrupted, uncommitted identity operation. */
    public suspend fun deleteUncommittedKey(request: WalletKeyCreationRequest, imported: Boolean): Unit =
        throw UnsupportedOperationException("Uncommitted-key cleanup is unavailable")

    /**
     * Deletes a platform key using its descriptor without restoring the alias first.
     */
    public suspend fun deleteManagedKey(stored: StoredKey.Managed)

}

