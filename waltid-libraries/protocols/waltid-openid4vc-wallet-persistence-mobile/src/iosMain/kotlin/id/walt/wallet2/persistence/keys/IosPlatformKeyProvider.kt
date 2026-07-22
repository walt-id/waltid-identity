package id.walt.wallet2.persistence.keys

import id.walt.crypto.IosKey
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
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
import id.walt.crypto2.keys.Key as Crypto2Key
import kotlin.uuid.Uuid

/**
 * [PlatformKeyProvider] implementation backed by iOS Keychain and Secure Enclave.
 *
 * @param useSecureElement When `true`, P-256 keys are created in Secure Enclave where available.
 */
public class IosPlatformKeyProvider(
    private val useSecureElement: Boolean = true,
) : PlatformKeyProvider, Crypto2PlatformKeyProvider {
    private val signumProvider = SignumManagedKeyProvider(IosSignumKeyBackend())

    /**
     * iOS platform-backed key types supported by this provider.
     */
    override val supportedPlatformKeyTypes: Set<KeyType> =
        PlatformKeyProvider.DEFAULT_SUPPORTED_PLATFORM_KEY_TYPES

    /**
     * Generates an iOS platform-backed key for supported types, otherwise a software key.
     */
    override suspend fun generateKey(keyType: KeyType, keyId: String?): Key {
        val kid = keyId ?: Uuid.random().toString()
        val options = IosKey.Options(
            kid = kid,
            keyType = keyType,
            inSecureElement = useSecureElement && keyType == KeyType.secp256r1,
        )
        return if (isPlatformBacked(keyType)) {
            IosKey.Platform.create(options)
        } else {
            IosKey.Software.create(options)
        }
    }

    override fun isPlatformBacked(key: Key): Boolean = key is IosKey.Platform

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
            policy = defaultSignumPolicy(),
        )
    }

    override suspend fun restoreManagedKey(stored: StoredKey.Managed): Crypto2Key =
        signumProvider.restore(stored)

    override suspend fun deleteManagedKey(stored: StoredKey.Managed) {
        signumProvider.delete(stored, expectedAlias = stored.id.value)
    }

    /**
     * Loads an iOS key by identifier and expected key type.
     */
    override suspend fun loadKey(keyId: String, keyType: KeyType): Key? = runCatching {
        val options = IosKey.Options(
            kid = keyId,
            keyType = keyType,
            inSecureElement = useSecureElement && keyType == KeyType.secp256r1,
        )
        IosKey.Platform.load(options)
    }.getOrNull()

    /**
     * Loads an iOS software key from serialized JWK material.
     */
    override suspend fun loadSoftwareKey(keyId: String, keyType: KeyType, jwkMaterial: ByteArray): Key? = runCatching {
        IosKey.Software.load(IosKey.Options(kid = keyId, keyType = keyType), jwkMaterial)
    }.getOrNull()

    /**
     * Exports serialized JWK material from an iOS software key.
     */
    override suspend fun exportSoftwareKeyMaterial(key: Key): ByteArray {
        require(key is IosKey.Software) { "Can only export material from Software keys" }
        return IosKey.Software.exportKeyMaterial(key)
    }

    /**
     * Deletes an iOS key by identifier and expected key type.
     */
    override suspend fun deleteKey(keyId: String, keyType: KeyType): Boolean = runCatching {
        if (isPlatformBacked(keyType)) {
            IosKey.Platform.delete(keyId)
        }
    }.isSuccess

    private fun defaultSignumPolicy(): SignumKeyPolicy = SignumKeyPolicy(
        hardware = if (useSecureElement) SignumHardwarePolicy.PREFERRED else SignumHardwarePolicy.DISCOURAGED,
    )
}
