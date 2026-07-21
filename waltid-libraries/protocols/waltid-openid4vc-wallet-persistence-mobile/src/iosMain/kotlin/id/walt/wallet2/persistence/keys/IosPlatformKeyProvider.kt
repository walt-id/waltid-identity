package id.walt.wallet2.persistence.keys

import id.walt.crypto.IosKey
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.wallet2.persistence.encryption.WalletPersistenceException
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Security.SecItemCopyMatching
import platform.Security.SecKeyGeneratePair
import platform.Security.SecKeyRefVar
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessGroup
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrApplicationLabel
import platform.Security.kSecAttrApplicationTag
import platform.Security.kSecAttrIsPermanent
import platform.Security.kSecAttrKeyClass
import platform.Security.kSecAttrKeyClassPrivate
import platform.Security.kSecAttrKeySizeInBits
import platform.Security.kSecAttrKeyType
import platform.Security.kSecAttrKeyTypeECSECPrimeRandom
import platform.Security.kSecAttrLabel
import platform.Security.kSecAttrTokenID
import platform.Security.kSecAttrTokenIDSecureEnclave
import platform.Security.kSecClass
import platform.Security.kSecClassKey
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecPrivateKeyAttrs
import platform.Security.kSecPublicKeyAttrs
import kotlin.uuid.Uuid

/**
 * [PlatformKeyProvider] implementation backed by iOS Keychain and Secure Enclave.
 *
 * @param useSecureElement When `true`, P-256 keys are created in Secure Enclave where available.
 */
@OptIn(ExperimentalForeignApi::class)
public class IosPlatformKeyProvider(
    private val useSecureElement: Boolean = true,
    private val accessGroup: String? = null,
) : PlatformKeyProvider {

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
            if (accessGroup != null && keyType == KeyType.secp256r1) {
                generateSharedP256Key(kid, accessGroup)
                IosKey.Platform.load(options)
            } else {
                IosKey.Platform.create(options)
            }
        } else {
            IosKey.Software.create(options)
        }
    }

    /**
     * Loads an iOS key by identifier and expected key type.
     */
    override suspend fun loadKey(keyId: String, keyType: KeyType): Key? {
        val options = IosKey.Options(
            kid = keyId,
            keyType = keyType,
            inSecureElement = useSecureElement && keyType == KeyType.secp256r1,
        )
        if (accessGroup != null && keyType == KeyType.secp256r1 && !sharedKeyExists(keyId, accessGroup)) {
            val legacyKeyExists = runCatching { IosKey.Platform.load(options) }.isSuccess
            if (legacyKeyExists) throw WalletPersistenceException.LegacyKeyRequiresCredentialReissuance(keyId)
            return null
        }
        return runCatching { IosKey.Platform.load(options) }.getOrNull()
    }

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

    private fun generateSharedP256Key(keyId: String, group: String) = memScoped {
        val privateAttributes = RetainedDictionary(6).apply {
            addRetained(kSecAttrApplicationLabel, keyId)
            addRetained(kSecAttrApplicationTag, PRIVATE_KEY_TAG)
            add(kSecAttrIsPermanent, kCFBooleanTrue)
            addRetained(kSecAttrAccessGroup, group)
            add(kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
        }
        val publicAttributes = RetainedDictionary(5).apply {
            addRetained(kSecAttrApplicationLabel, keyId)
            addRetained(kSecAttrApplicationTag, PUBLIC_KEY_TAG)
            // Signum stores non-secret signer metadata on the public-key item and requires it
            // when reconstructing its platform signer in either process.
            addRetained(kSecAttrLabel, SIGNUM_P256_SIGNING_METADATA)
            add(kSecAttrIsPermanent, kCFBooleanTrue)
            addRetained(kSecAttrAccessGroup, group)
        }
        val attributes = RetainedDictionary(8).apply {
            add(kSecAttrKeyType, kSecAttrKeyTypeECSECPrimeRandom)
            addRetained(kSecAttrKeySizeInBits, 256)
            if (useSecureElement) add(kSecAttrTokenID, kSecAttrTokenIDSecureEnclave)
            add(kSecPrivateKeyAttrs, privateAttributes.dictionary)
            add(kSecPublicKeyAttrs, publicAttributes.dictionary)
        }
        val publicKey = alloc<SecKeyRefVar>()
        val privateKey = alloc<SecKeyRefVar>()
        try {
            val status = SecKeyGeneratePair(attributes.dictionary, publicKey.ptr, privateKey.ptr)
            check(status == errSecSuccess) { "Shared Secure Enclave key generation failed with status $status" }
            publicKey.value?.let(::CFRelease)
            privateKey.value?.let(::CFRelease)
        } finally {
            attributes.release()
            privateAttributes.release()
            publicAttributes.release()
        }
    }

    private fun sharedKeyExists(keyId: String, group: String): Boolean {
        val query = RetainedDictionary(9).apply {
            add(kSecClass, kSecClassKey)
            add(kSecAttrKeyClass, kSecAttrKeyClassPrivate)
            add(kSecAttrKeyType, kSecAttrKeyTypeECSECPrimeRandom)
            addRetained(kSecAttrApplicationLabel, keyId)
            addRetained(kSecAttrApplicationTag, PRIVATE_KEY_TAG)
            addRetained(kSecAttrAccessGroup, group)
            add(kSecMatchLimit, kSecMatchLimitOne)
        }
        return try {
            when (SecItemCopyMatching(query.dictionary, null)) {
                errSecSuccess -> true
                errSecItemNotFound -> false
                else -> false
            }
        } finally {
            query.release()
        }
    }

    private class RetainedDictionary(capacity: Long) {
        val dictionary = CFDictionaryCreateMutable(
            kCFAllocatorDefault,
            capacity,
            kCFTypeDictionaryKeyCallBacks.ptr,
            kCFTypeDictionaryValueCallBacks.ptr,
        )
        private val retainedValues = mutableListOf<CFTypeRef?>()

        fun add(key: CFTypeRef?, value: CFTypeRef?) {
            CFDictionaryAddValue(dictionary, key, value)
        }

        fun addRetained(key: CFTypeRef?, value: Any?) {
            val retained = CFBridgingRetain(value)
            retainedValues += retained
            CFDictionaryAddValue(dictionary, key, retained)
        }

        fun release() {
            retainedValues.forEach { CFBridgingRelease(it) }
            CFBridgingRelease(dictionary)
        }
    }

    private companion object {
        const val PRIVATE_KEY_TAG = "supreme.privatekey"
        const val PUBLIC_KEY_TAG = "supreme.publickey"
        const val SIGNUM_P256_SIGNING_METADATA =
            "{\"attestation\":null,\"rawUnlockTimeout\":null,\"algSpecific\":{\"type\":\"ecdsa\",\"supportedDigests\":[\"SHA256\"]},\"allowSigning\":true,\"allowKeyAgreement\":false,\"allowEncryption\":false}"
    }
}
