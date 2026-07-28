package id.walt.wallet2.persistence.keys

import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.ManagedKey
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.providers.GenerateManagedKeyRequest
import id.walt.crypto2.signum.IosSignumKeyBackend
import id.walt.crypto2.signum.SignumHardwarePolicy
import id.walt.crypto2.signum.SignumKeyPolicy
import id.walt.crypto2.signum.SignumKeyOptions
import id.walt.crypto2.signum.SignumManagedKeyProvider

/**
 * Managed-key provider backed by iOS Keychain and Secure Enclave.
 *
 * @param useSecureElement When `true`, P-256 keys are created in Secure Enclave where available.
 */
public class IosPlatformKeyProvider(
    private val useSecureElement: Boolean = true,
) : PlatformManagedKeyProvider {
    private val signumProvider = SignumManagedKeyProvider(IosSignumKeyBackend())

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
            providerOptions = SignumKeyOptions(policy = policy ?: defaultSignumPolicy()).encode(),
        )
    )

    override suspend fun restoreManagedKey(stored: StoredKey.Managed): ManagedKey =
        signumProvider.restore(stored)

    override suspend fun deleteManagedKey(stored: StoredKey.Managed) {
        signumProvider.delete(stored, expectedAlias = stored.id.value)
    }

    private fun defaultSignumPolicy(): SignumKeyPolicy = SignumKeyPolicy(
        hardware = if (useSecureElement) SignumHardwarePolicy.PREFERRED else SignumHardwarePolicy.DISCOURAGED,
    )
}
