package id.walt.wallet2.persistence.keys

import id.walt.crypto.keys.KeyType
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EdwardsCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.ManagedKey
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.keys.toStoredSoftwareKey
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.crypto2.serialization.BinaryData
import id.walt.crypto2.signum.SignumKeyPolicy

/**
 * Crypto2 generation and persistence support for native platform keys.
 */
public interface Crypto2PlatformKeyProvider {
    /**
     * Generates a managed crypto2 key in the platform key store.
     */
    public suspend fun generateManagedKey(
        id: KeyId,
        spec: KeySpec,
        usages: Set<KeyUsage>,
        policy: SignumKeyPolicy? = null,
    ): ManagedKey

    /**
     * Creates a crypto2 descriptor for an existing platform key without replacing it.
     */
    public suspend fun migratePlatformKey(
        id: KeyId,
        keyType: KeyType,
        usages: Set<KeyUsage>,
    ): StoredKey.Managed

    /**
     * Restores a platform key from its persisted crypto2 descriptor.
     */
    public suspend fun restoreManagedKey(stored: StoredKey.Managed): Key

    /**
     * Deletes a platform key using its descriptor without restoring the alias first.
     */
    public suspend fun deleteManagedKey(stored: StoredKey.Managed)
}

internal class MobileStoredKeyMigration(
    private val platformProvider: Crypto2PlatformKeyProvider?,
) {
    private val softwareRuntime = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()))

    suspend fun migrate(
        id: KeyId,
        keyType: KeyType,
        platformBacked: Boolean,
        keyMaterial: String?,
        usages: Set<KeyUsage>,
    ): StoredKey? {
        val migrated = if (platformBacked) {
            val provider = platformProvider ?: return null
            provider.migratePlatformKey(id, keyType, usages)
        } else {
            if (keyType == KeyType.secp256k1) return null
            val material = requireNotNull(keyMaterial) { "Mobile software key has no material: ${id.value}" }
            EncodedKey.Jwk(
                data = BinaryData(material.encodeToByteArray()),
                privateMaterial = true,
            ).toStoredSoftwareKey(id, usages)
        }
        require(migrated.id == id) { "Mobile migration changed the key ID" }
        require(migrated.spec == keyType.toCrypto2KeySpec()) { "Mobile migration changed the key specification" }
        require(migrated.usages == usages) { "Mobile migration changed key usages" }
        restore(migrated)
        return migrated
    }

    suspend fun restore(stored: StoredKey): Key = when (stored) {
        is StoredKey.Managed -> requireNotNull(platformProvider) {
            "No crypto2 platform provider is available for ${stored.provider.value}"
        }.restoreManagedKey(stored)
        is StoredKey.Software -> softwareRuntime.restore(stored)
    }

    suspend fun delete(stored: StoredKey.Managed) {
        requireNotNull(platformProvider) {
            "No crypto2 platform provider is available for ${stored.provider.value}"
        }.deleteManagedKey(stored)
    }
}

internal fun KeyType.toCrypto2KeySpec(): KeySpec = when (this) {
    KeyType.Ed25519 -> KeySpec.Edwards(EdwardsCurve.ED25519)
    KeyType.secp256k1 -> KeySpec.Ec(EcCurve.SECP256K1)
    KeyType.secp256r1 -> KeySpec.Ec(EcCurve.P256)
    KeyType.secp384r1 -> KeySpec.Ec(EcCurve.P384)
    KeyType.secp521r1 -> KeySpec.Ec(EcCurve.P521)
    KeyType.RSA -> KeySpec.Rsa(2048)
    KeyType.RSA3072 -> KeySpec.Rsa(3072)
    KeyType.RSA4096 -> KeySpec.Rsa(4096)
}

internal fun KeySpec.toLegacyKeyType(): KeyType = when (this) {
    is KeySpec.Ec -> when (curve) {
        EcCurve.P256 -> KeyType.secp256r1
        EcCurve.P384 -> KeyType.secp384r1
        EcCurve.P521 -> KeyType.secp521r1
        EcCurve.SECP256K1 -> KeyType.secp256k1
        else -> throw IllegalArgumentException("Mobile key schema cannot represent crypto2 EC curve: ${curve.name}")
    }
    is KeySpec.Edwards -> when (curve) {
        EdwardsCurve.ED25519 -> KeyType.Ed25519
        else -> throw IllegalArgumentException("Mobile key schema cannot represent crypto2 Edwards curve: ${curve.name}")
    }
    is KeySpec.Rsa -> when (bits) {
        2048 -> KeyType.RSA
        3072 -> KeyType.RSA3072
        4096 -> KeyType.RSA4096
        else -> throw IllegalArgumentException("Mobile key schema cannot represent crypto2 RSA size: $bits")
    }
    is KeySpec.Montgomery,
    is KeySpec.Symmetric,
    is KeySpec.Custom,
    -> throw IllegalArgumentException("Mobile key schema cannot represent crypto2 key spec: $this")
}
