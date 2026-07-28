package id.walt.wallet2.persistence.keys

import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.ManagedKey
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.providers.GenerateManagedKeyRequest
import id.walt.crypto2.signum.AndroidSignumKeyBackend
import id.walt.crypto2.signum.SignumKeyPolicy
import id.walt.crypto2.signum.SignumKeyOptions
import id.walt.crypto2.signum.SignumManagedKeyProvider

/**
 * Managed-key provider backed by Android KeyStore.
 */
public class AndroidPlatformKeyProvider : PlatformManagedKeyProvider {
    private val signumProvider = SignumManagedKeyProvider(AndroidSignumKeyBackend())

    override suspend fun generateManagedKey(
        id: KeyId,
        spec: KeySpec,
        usages: Set<KeyUsage>,
        policy: SignumKeyPolicy?,
    ): ManagedKey = signumProvider.generate(
        GenerateManagedKeyRequest(
            id = id,
            spec = spec,
            usages = usages,
            providerOptions = SignumKeyOptions(policy = policy ?: SignumKeyPolicy()).encode(),
        )
    )

    override suspend fun restoreManagedKey(stored: StoredKey.Managed): Key =
        signumProvider.restore(stored)

    override suspend fun deleteManagedKey(stored: StoredKey.Managed) {
        signumProvider.delete(stored, expectedAlias = stored.id.value)
    }
}
