@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package id.walt.crypto2.signum

import id.walt.crypto2.signum.corefoundation.waltCfEqual
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureCodec
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.KeyAgreementAlgorithm
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.*
import id.walt.crypto2.serialization.BinaryData
import kotlinx.cinterop.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import platform.CoreFoundation.*
import platform.Foundation.*
import platform.LocalAuthentication.LAContext
import platform.Security.*
import kotlin.io.encoding.Base64
import kotlin.time.TimeSource

/** Native Keychain import is deliberately separate from Secure Enclave generation. */
internal object IosNativeKeys {
    fun supports(policy: SignumKeyPolicy, importing: Boolean): Boolean =
        policy.platform !is SignumPlatformPolicy.AndroidKeystore && !policy.keyAgreement &&
            policy.attestationChallenge == null && (!importing || policy.hardware != SignumHardwarePolicy.REQUIRED)

    suspend fun create(alias: String, policy: SignumKeyPolicy, material: EncodedKey.Jwk?): SignumPlatformKey =
        withContext(Dispatchers.Default) {
            require(supports(policy, importing = material != null)) { "Unsupported iOS native key policy" }
            require(!exists(alias, policy)) { "Keychain alias already exists" }
            val raw = material?.let(::rawPrivateKey)
            val privateKey = try {
                attributes().use { attributes ->
                    if (material != null) attributes.put(kSecAttrKeyClass, kSecAttrKeyClassPrivate)
                    if (material == null) Dictionary().use { privateAttributes ->
                        addAccessControl(privateAttributes, policy)
                        privateAttributes.put(kSecAttrIsPermanent, kCFBooleanFalse)
                        attributes.put(kSecPrivateKeyAttrs, privateAttributes.ref)
                    }
                    if (material == null && policy.hardware != SignumHardwarePolicy.DISCOURAGED) {
                        attributes.put(kSecAttrTokenID, kSecAttrTokenIDSecureEnclave)
                    }
                    memScoped {
                        val error = alloc<CFErrorRefVar>(); error.value = null
                        val result = if (raw == null) SecKeyCreateRandomKey(attributes.ref, error.ptr)
                        else retained(raw.toNSData()) { SecKeyCreateWithData(it as CFDataRef, attributes.ref, error.ptr) }
                        result ?: throw failure(alias, error.value)
                    }
                }
            } finally { raw?.fill(0) }
            try {
                // Explicit add keeps generation/import and persistence under the same ownership rules.
                query(alias, policy).use { query ->
                    query.put(kSecValueRef, privateKey)
                    addAccessControl(query, policy)
                    checkStatus(SecItemAdd(query.ref, null), alias)
                }
            } finally { CFRelease(privateKey) }
            try { requireNotNull(load(alias, policy, material != null)) }
            catch (cause: Throwable) {
                try { delete(alias, policy) } catch (cleanup: Throwable) { cause.addSuppressed(cleanup) }
                throw cause
            }
        }

    suspend fun load(alias: String, policy: SignumKeyPolicy, imported: Boolean): SignumPlatformKey? {
        if (!exists(alias, policy)) return null
        return NativeKey(alias, policy, imported)
    }

    fun delete(alias: String, policy: SignumKeyPolicy) {
        query(alias, policy).use { checkStatus(SecItemDelete(it.ref), alias, missingAllowed = true) }
    }

    private fun exists(alias: String, policy: SignumKeyPolicy): Boolean = query(alias, policy).use {
        it.put(kSecUseAuthenticationUI, kSecUseAuthenticationUIFail)
        when (val status = SecItemCopyMatching(it.ref, null)) {
            errSecSuccess, errSecInteractionNotAllowed, errSecAuthFailed -> true
            errSecItemNotFound -> false
            else -> { checkStatus(status, alias); false }
        }
    }

    private class NativeKey(
        override val alias: String,
        private val policy: SignumKeyPolicy,
        imported: Boolean,
    ) : SignumPlatformKey {
        override val spec: KeySpec = KeySpec.Ec(EcCurve.P256)
        override val origin = if (imported) SignumKeyOrigin.IMPORTED else SignumKeyOrigin.GENERATED
        override val securityLevel: SignumSecurityLevel = withKey(alias, policy, null) { key ->
            val attributes = SecKeyCopyAttributes(key) ?: error("Keychain attributes unavailable")
            try {
                val token = CFDictionaryGetValue(attributes, kSecAttrTokenID)
                if (token != null && waltCfEqual(token, kSecAttrTokenIDSecureEnclave)) {
                    require(!imported) { "A recovered key cannot claim Secure Enclave import" }
                    SignumSecurityLevel.SECURE_ENCLAVE
                } else {
                    require(policy.hardware != SignumHardwarePolicy.REQUIRED) { "Secure Enclave backing was not observed" }
                    SignumSecurityLevel.SOFTWARE
                }
            } finally { CFRelease(attributes) }
        }
        override val protectionLevel = if (securityLevel == SignumSecurityLevel.SECURE_ENCLAVE) {
            SignumProtectionLevel.HARDWARE
        } else SignumProtectionLevel.SOFTWARE
        override val attestation: SignumKeyAttestation? = null
        override val signatureAlgorithms = setOf(SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256))
        override val keyAgreementAlgorithms: Set<KeyAgreementAlgorithm> = emptySet()
        private val signingMutex = Mutex()
        private var context: LAContext? = null
        private var authenticatedAt: TimeSource.Monotonic.ValueTimeMark? = null

        override val publicKey: EncodedKey.SpkiDer by lazy {
            withKey(alias, policy, null) { key ->
                val public = SecKeyCopyPublicKey(key) ?: error("Keychain public key unavailable")
                try {
                    memScoped {
                        val error = alloc<CFErrorRefVar>(); error.value = null
                        val data = SecKeyCopyExternalRepresentation(public, error.ptr)
                            ?: throw failure(alias, error.value)
                        try {
                            // P-256 SubjectPublicKeyInfo header followed by its uncompressed ANSI X9.63 point.
                            val point = data.toBytes()
                            require(point.size == 65 && point[0] == 4.toByte()) { "Invalid P-256 public key" }
                            EncodedKey.SpkiDer(BinaryData(SPKI_PREFIX + point))
                        } finally { CFRelease(data) }
                    }
                } finally { CFRelease(public) }
            }
        }

        override suspend fun sign(data: ByteArray, algorithm: SignatureAlgorithm): ByteArray = signingMutex.withLock { withContext(Dispatchers.Default) {
            require(algorithm == SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256)) { "Unsupported signature algorithm" }
            val auth = policy.authentication as? SignumAuthenticationPolicy.UserPresence
            val reusable = auth != null && auth.timeoutSeconds > 0 &&
                authenticatedAt?.elapsedNow()?.inWholeMilliseconds?.let { it < auth.timeoutSeconds * 1000L } == true
            val activeContext = if (reusable) requireNotNull(context) else LAContext().apply {
                localizedReason = auth?.prompt ?: "Authorize signing"
                localizedCancelTitle = auth?.cancelText ?: "Cancel"
            }
            try {
                val signature = withKey(alias, policy, activeContext) { key ->
                    retained(data.toNSData()) { input -> memScoped {
                        val error = alloc<CFErrorRefVar>(); error.value = null
                        val result = SecKeyCreateSignature(key, kSecKeyAlgorithmECDSASignatureMessageX962SHA256,
                            input as CFDataRef, error.ptr) ?: throw failure(alias, error.value)
                        try { result.toBytes() } finally { CFRelease(result) }
                    } }
                }
                if (!reusable && auth != null && auth.timeoutSeconds > 0) {
                    context?.invalidate(); context = activeContext; authenticatedAt = TimeSource.Monotonic.markNow()
                } else if (auth == null || auth.timeoutSeconds == 0) activeContext.invalidate()
                EcdsaSignatureCodec.derToP1363(signature, 32)
            } catch (cause: Throwable) {
                activeContext.invalidate(); context = null; authenticatedAt = null; throw cause
            }
        }

        }

        override suspend fun verify(data: ByteArray, signature: ByteArray, algorithm: SignatureAlgorithm): Boolean {
            require(algorithm == SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256))
            val runtime = id.walt.crypto2.CryptoRuntime(id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders())
            val key = runtime.restore(StoredKey.Software(StoredKey.CURRENT_VERSION, KeyId(alias), spec,
                setOf(KeyUsage.VERIFY), publicKey))
            return requireNotNull(key.capabilities.verifier).verify(data, signature, algorithm)
        }

        override suspend fun generateSharedSecret(peerPublicKey: EncodedKey, algorithm: KeyAgreementAlgorithm): BinaryData =
            throw UnsupportedOperationException("Identity signing keys do not permit key agreement")
    }

    private fun attributes(): Dictionary = Dictionary().apply {
        put(kSecAttrKeyType, kSecAttrKeyTypeECSECPrimeRandom)
        putRetained(kSecAttrKeySizeInBits, 256)
        // Access control is installed by SecItemAdd; imported keys never receive an Enclave token.
    }

    private fun query(alias: String, policy: SignumKeyPolicy): Dictionary = Dictionary().apply {
        put(kSecClass, kSecClassKey)
        put(kSecAttrKeyType, kSecAttrKeyTypeECSECPrimeRandom)
        put(kSecAttrKeyClass, kSecAttrKeyClassPrivate)
        putRetained(kSecAttrApplicationTag, "id.walt.crypto2.native:$alias".encodeToByteArray().toNSData())
        (policy.platform as? SignumPlatformPolicy.IosKeychain)?.accessGroup?.let { putRetained(kSecAttrAccessGroup, it) }
        put(kSecUseDataProtectionKeychain, kCFBooleanTrue)
    }

    private fun addAccessControl(dictionary: Dictionary, policy: SignumKeyPolicy) {
        val settings = policy.platform as? SignumPlatformPolicy.IosKeychain ?: SignumPlatformPolicy.IosKeychain()
        val accessibility = when (settings.accessibility) {
            SignumKeychainAccessibility.WHEN_UNLOCKED -> kSecAttrAccessibleWhenUnlocked
            SignumKeychainAccessibility.AFTER_FIRST_UNLOCK -> kSecAttrAccessibleAfterFirstUnlock
            SignumKeychainAccessibility.WHEN_UNLOCKED_DEVICE_ONLY -> kSecAttrAccessibleWhenUnlockedThisDeviceOnly
            SignumKeychainAccessibility.AFTER_FIRST_UNLOCK_DEVICE_ONLY -> kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            SignumKeychainAccessibility.WHEN_PASSCODE_SET_DEVICE_ONLY -> kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly
        }
        val auth = policy.authentication as? SignumAuthenticationPolicy.UserPresence
        if (auth == null) { dictionary.put(kSecAttrAccessible, accessibility); return }
        val flags = when {
            auth.biometric && auth.deviceCredential -> kSecAccessControlUserPresence
            auth.biometric && !auth.allowNewBiometrics -> kSecAccessControlBiometryCurrentSet
            auth.biometric -> kSecAccessControlBiometryAny
            else -> kSecAccessControlDevicePasscode
        } or kSecAccessControlPrivateKeyUsage
        memScoped {
            val error = alloc<CFErrorRefVar>(); error.value = null
            val control = SecAccessControlCreateWithFlags(kCFAllocatorDefault, accessibility, flags, error.ptr)
                ?: throw failure("access control", error.value)
            try { dictionary.put(kSecAttrAccessControl, control) } finally { CFRelease(control) }
        }
    }

    private fun <T> withKey(alias: String, policy: SignumKeyPolicy, context: LAContext?, block: (SecKeyRef) -> T): T =
        query(alias, policy).use { query ->
            query.put(kSecReturnRef, kCFBooleanTrue)
            context?.let { query.putRetained(kSecUseAuthenticationContext, it) }
            memScoped {
                val result = alloc<CFTypeRefVar>(); result.value = null
                checkStatus(SecItemCopyMatching(query.ref, result.ptr), alias)
                val key = result.value as? SecKeyRef ?: error("Keychain returned no key")
                try { block(key) } finally { CFRelease(key) }
            }
        }

    private fun rawPrivateKey(material: EncodedKey.Jwk): ByteArray {
        val jwk = Json.parseToJsonElement(material.data.toByteArray().decodeToString()).jsonObject
        val base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
        fun value(name: String) = base64.decode(jwk.getValue(name).jsonPrimitive.content).also { require(it.size == 32) }
        return byteArrayOf(4) + value("x") + value("y") + value("d")
    }

    private fun checkStatus(status: Int, alias: String, missingAllowed: Boolean = false) {
        when (status) {
            errSecSuccess -> Unit
            errSecItemNotFound -> if (!missingAllowed) throw SignumKeyNotFoundException(alias)
            errSecUserCanceled -> throw SignumUserCancelledException(IllegalStateException("Key authorization cancelled"))
            errSecInteractionNotAllowed -> throw SignumInteractionContextUnavailableException()
            else -> error("Keychain operation failed with status $status")
        }
    }

    private fun failure(alias: String, error: CFErrorRef?): Throwable {
        if (error == null) return IllegalStateException("Keychain operation failed")
        val code = CFErrorGetCode(error).toInt(); CFRelease(error)
        return when (code) {
            errSecUserCanceled -> SignumUserCancelledException(IllegalStateException("Key authorization cancelled"))
            errSecItemNotFound -> SignumKeyNotFoundException(alias)
            errSecInteractionNotAllowed -> SignumInteractionContextUnavailableException()
            else -> IllegalStateException("Keychain operation failed with code $code")
        }
    }

    private class Dictionary : AutoCloseable {
        val ref = CFDictionaryCreateMutable(kCFAllocatorDefault, 0, kCFTypeDictionaryKeyCallBacks.ptr,
            kCFTypeDictionaryValueCallBacks.ptr)!!
        fun put(key: CFTypeRef?, value: CFTypeRef?) = CFDictionarySetValue(ref, key, value)
        fun putRetained(key: CFTypeRef?, value: Any) = retained(value) { put(key, it) }
        override fun close() = CFRelease(ref)
    }
    private fun <T> retained(value: Any, block: (CFTypeRef) -> T): T {
        val reference = CFBridgingRetain(value)!!
        try { return block(reference) } finally { CFRelease(reference) }
    }
    private fun ByteArray.toNSData(): NSData = usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }
    private fun CFDataRef.toBytes(): ByteArray = CFDataGetBytePtr(this)!!.readBytes(CFDataGetLength(this).toInt())
    private val SPKI_PREFIX = byteArrayOf(0x30,0x59,0x30,0x13,0x06,0x07,0x2a,0x86.toByte(),0x48,0xce.toByte(),0x3d,0x02,0x01,
        0x06,0x08,0x2a,0x86.toByte(),0x48,0xce.toByte(),0x3d,0x03,0x01,0x07,0x03,0x42,0x00)
}
