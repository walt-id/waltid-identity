package id.walt.wallet2.persistence.keys

import id.walt.crypto.AndroidKey
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
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
import id.walt.crypto2.keys.Key as Crypto2Key
import kotlin.uuid.Uuid

/**
 * [PlatformKeyProvider] implementation backed by Android KeyStore.
 */
public class AndroidPlatformKeyProvider : PlatformKeyProvider, Crypto2PlatformKeyProvider {
    private val signumProvider = SignumManagedKeyProvider(AndroidSignumKeyBackend())

    /**
     * Android platform-backed key types supported by this provider.
     */
    override val supportedPlatformKeyTypes: Set<KeyType> =
        PlatformKeyProvider.DEFAULT_SUPPORTED_PLATFORM_KEY_TYPES

    /**
     * Generates an Android platform-backed key for supported types, otherwise a software key.
     */
    override suspend fun generateKey(keyType: KeyType, keyId: String?): Key {
        val alias = keyId ?: "wallet_key_${Uuid.random()}"
        val options = AndroidKey.Options(kid = alias, keyType = keyType)
        return if (isPlatformBacked(keyType)) {
            AndroidKey.Platform.create(options)
        } else {
            AndroidKey.Software.create(options)
        }
    }

    override fun isPlatformBacked(key: Key): Boolean = key is AndroidKey.Platform

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

    override suspend fun migratePlatformKey(
        id: KeyId,
        keyType: KeyType,
        usages: Set<KeyUsage>,
    ): StoredKey.Managed {
        return signumProvider.storedKeyForExisting(
            id = id,
            spec = keyType.toCrypto2KeySpec(),
            usages = usages,
            alias = id.value,
            policy = SignumKeyPolicy(),
        )
    }

    override suspend fun restoreManagedKey(stored: StoredKey.Managed): Crypto2Key =
        signumProvider.restore(stored)

    override suspend fun deleteManagedKey(stored: StoredKey.Managed) {
        signumProvider.delete(stored, expectedAlias = stored.id.value)
    }

    /**
     * Loads an Android platform-backed key by alias and expected key type.
     */
    override suspend fun loadKey(keyId: String, keyType: KeyType): Key? = runCatching {
        AndroidKey.Platform.load(AndroidKey.Options(kid = keyId, keyType = keyType))
    }.getOrNull()

    /**
     * Loads an Android software key from serialized JWK material.
     */
    override suspend fun loadSoftwareKey(keyId: String, keyType: KeyType, jwkMaterial: ByteArray): Key? = runCatching {
        AndroidKey.Software.load(AndroidKey.Options(kid = keyId, keyType = keyType), jwkMaterial)
    }.getOrNull()

    /**
     * Exports serialized JWK material from an Android software key.
     */
    override suspend fun exportSoftwareKeyMaterial(key: Key): ByteArray {
        require(key is AndroidKey.Software) { "Can only export material from Software keys" }
        return AndroidKey.Software.exportKeyMaterial(key)
    }

    /**
     * Deletes an Android platform-backed key by alias and expected key type.
     */
    override suspend fun deleteKey(keyId: String, keyType: KeyType): Boolean = runCatching {
        if (isPlatformBacked(keyType)) {
            AndroidKey.Platform.delete(keyId)
        }
    }.isSuccess
}
