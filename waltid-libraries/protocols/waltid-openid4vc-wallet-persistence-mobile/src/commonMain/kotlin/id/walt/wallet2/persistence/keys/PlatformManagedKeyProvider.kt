package id.walt.wallet2.persistence.keys

import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.ManagedKey
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.signum.SignumKeyPolicy

/**
 * Generation, restoration, and deletion support for managed native platform keys.
 */
public interface PlatformManagedKeyProvider {
    /**
     * Generates a managed key in the platform key store.
     */
    public suspend fun generateManagedKey(
        id: KeyId,
        spec: KeySpec,
        usages: Set<KeyUsage>,
        policy: SignumKeyPolicy? = null,
    ): ManagedKey

    /**
     * Restores a platform key from its persisted descriptor.
     */
    public suspend fun restoreManagedKey(stored: StoredKey.Managed): Key

    /**
     * Deletes a platform key using its descriptor without restoring the alias first.
     */
    public suspend fun deleteManagedKey(stored: StoredKey.Managed)
}
