@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package id.walt.crypto2.signum

import id.walt.crypto2.keys.PlatformKeyConfiguration
import id.walt.crypto2.keys.HardwarePreference
import id.walt.crypto2.keys.KeyProtectionLevel
import id.walt.crypto2.keys.KeyAttestation
import id.walt.crypto2.keys.KeyOrigin
import id.walt.crypto2.keys.KeySecurityLevel

import id.walt.crypto2.signum.corefoundation.waltCfEqual
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureCodec
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.KeyAgreementAlgorithm
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.*
import id.walt.crypto2.serialization.BinaryData
import kotlinx.cinterop.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
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

internal interface AppleKeychainKey : SignumPlatformKey {
    val nativeIdentity: IosKeyIdentity
}

/** Native Keychain import is deliberately separate from Secure Enclave generation. */
internal object AppleKeychainKeys {
    fun supports(policy: SignumKeyPolicy, importing: Boolean): Boolean =
        policy.platform !is PlatformKeyConfiguration.AndroidKeystore && !policy.keyAgreement &&
            policy.attestationChallenge == null && (!importing || policy.hardware != HardwarePreference.REQUIRED)

    suspend fun create(alias: String, policy: SignumKeyPolicy, material: EncodedKey.Jwk?): AppleKeychainKey =
        withContext(Dispatchers.Default) {
            require(supports(policy, importing = material != null)) { "Unsupported iOS native key policy" }
            require(!exists(alias, policy)) { "Keychain alias already exists" }
            val enclave = material == null && policy.hardware != HardwarePreference.DISCOURAGED
            val raw = material?.let(::rawPrivateKey)
            val privateKey = try {
                attributes().use { attributes ->
                    if (material != null) attributes.put(kSecAttrKeyClass, kSecAttrKeyClassPrivate)
                    if (material == null) KeychainDictionary().use { privateAttributes ->
                        addAccessControl(privateAttributes, policy, enclave)
                        privateAttributes.put(kSecAttrIsPermanent, kCFBooleanFalse)
                        attributes.put(kSecPrivateKeyAttrs, privateAttributes.ref)
                    }
                    if (enclave) {
                        attributes.put(kSecAttrTokenID, kSecAttrTokenIDSecureEnclave)
                    }
                    memScoped {
                        val error = alloc<CFErrorRefVar>(); error.value = null
                        val result = if (raw == null) SecKeyCreateRandomKey(attributes.ref, error.ptr)
                        else retained(raw.toNSData()) { SecKeyCreateWithData(it as CFDataRef, attributes.ref, error.ptr) }
                        result ?: throw takeKeychainFailure(alias, error.value)
                    }
                }
            } finally { raw?.fill(0) }
            val publicKey = try { publicKey(alias, privateKey) } catch (cause: Throwable) { CFRelease(privateKey); throw cause }
            val actualEnclave: Boolean
            val persistentReference: BinaryData
            var persisted = false
            try {
                val nativeAttributes = SecKeyCopyAttributes(privateKey) ?: error("Native key attributes are unavailable")
                actualEnclave = try {
                    CFDictionaryGetValue(nativeAttributes, kSecAttrTokenID)?.let { waltCfEqual(it, kSecAttrTokenIDSecureEnclave) } == true
                } finally { CFRelease(nativeAttributes) }
                // Explicit add keeps generation/import and persistence under the same ownership rules.
                query(alias, policy).use { query ->
                    query.put(kSecValueRef, privateKey)
                    addAccessControl(query, policy, enclave)
                    query.put(kSecReturnPersistentRef, kCFBooleanTrue)
                    persistentReference = memScoped {
                        val result = alloc<CFTypeRefVar>(); result.value = null
                        checkKeychainStatus(SecItemAdd(query.ref, result.ptr), alias)
                        persisted = true
                        val reference = result.value as? CFDataRef ?: error("Keychain did not return a persistent reference")
                        try { BinaryData(reference.toBytes()) } finally { CFRelease(reference) }
                    }
                }
            } catch (cause: Throwable) {
                if (persisted) try { delete(alias, policy) } catch (cleanup: Throwable) { cause.addSuppressed(cleanup) }
                throw cause
            } finally { CFRelease(privateKey) }
            val native = IosKeyIdentity(persistentReference, publicKey.data,
                if (!actualEnclave && policy.authentication == SignumAuthenticationPolicy.None)
                    CFBridgingRelease(CFRetain(policy.iosSettings().accessibility.nativeAccessibility)) as String else null,
                actualEnclave)
            try { requireNotNull(load(alias, policy, material != null, publicKey, native)) }
            catch (cause: Throwable) {
                try { delete(alias, policy) } catch (cleanup: Throwable) { cause.addSuppressed(cleanup) }
                throw cause
            }
        }

    suspend fun load(alias: String, policy: SignumKeyPolicy, imported: Boolean, publicKey: EncodedKey.SpkiDer, native: IosKeyIdentity): AppleKeychainKey? {
        validateIdentity(alias, policy, native)
        return NativeKey(alias, policy, imported, session(alias, policy), publicKey, native)
    }

    fun delete(alias: String, policy: SignumKeyPolicy) {
        sessionsLock.lock()
        try { sessions.remove(alias to policy)?.invalidate() } finally { sessionsLock.unlock() }
        query(alias, policy).use { checkKeychainStatus(SecItemDelete(it.ref), alias, missingAllowed = true) }
    }

    fun validateIdentity(alias: String, policy: SignumKeyPolicy, native: IosKeyIdentity) {
        referenceQuery(native).use { query ->
            query.put(kSecUseAuthenticationUI, kSecUseAuthenticationUIFail)
            when (val status = SecItemCopyMatching(query.ref, null)) {
                // The opaque reference identifies the entry even when its ACL requires interaction.
                // Actual key use resolves this same reference with the operation's LAContext.
                errSecSuccess, errSecInteractionNotAllowed, errSecAuthFailed -> Unit
                errSecItemNotFound -> {
                    if (exists(alias, policy)) throw SignumKeyPolicyMismatchException(alias, "native key was replaced")
                    throw SignumKeyNotFoundException(alias)
                }
                else -> checkKeychainStatus(status, alias)
            }
        }
    }

    private fun referenceQuery(native: IosKeyIdentity): KeychainDictionary = KeychainDictionary().apply {
        put(kSecClass, kSecClassKey)
        putRetained(kSecValuePersistentRef, native.persistentReference.toByteArray().toNSData())
        put(kSecUseDataProtectionKeychain, kCFBooleanTrue)
    }

    private fun exists(alias: String, policy: SignumKeyPolicy): Boolean = query(alias, policy).use {
        it.put(kSecUseAuthenticationUI, kSecUseAuthenticationUIFail)
        when (val status = SecItemCopyMatching(it.ref, null)) {
            errSecSuccess, errSecInteractionNotAllowed, errSecAuthFailed -> true
            errSecItemNotFound -> false
            else -> { checkKeychainStatus(status, alias); false }
        }
    }

    private class NativeKey(
        override val alias: String,
        private val policy: SignumKeyPolicy,
        imported: Boolean,
        private val authorization: AuthorizationSession,
        override val publicKey: EncodedKey.SpkiDer,
        override val nativeIdentity: IosKeyIdentity,
    ) : AppleKeychainKey {
        override val spec: KeySpec = KeySpec.Ec(EcCurve.P256)
        override val origin = if (imported) KeyOrigin.IMPORTED else KeyOrigin.GENERATED
        override val securityLevel = if (nativeIdentity.secureEnclave) KeySecurityLevel.SECURE_ENCLAVE else KeySecurityLevel.SOFTWARE

        override val protectionLevel = if (securityLevel == KeySecurityLevel.SECURE_ENCLAVE) {
            KeyProtectionLevel.HARDWARE
        } else KeyProtectionLevel.SOFTWARE
        override val attestation: KeyAttestation? = null
        override val privateKeyExporter: PrivateKeyExporter? = if (protectionLevel == KeyProtectionLevel.SOFTWARE) {
            PrivateKeyExporter {
                authorization.use { context -> withKey(alias, policy, nativeIdentity, context) { key -> memScoped {
                    val error = alloc<CFErrorRefVar>(); error.value = null
                    val data = SecKeyCopyExternalRepresentation(key, error.ptr) ?: throw takeKeychainFailure(alias, error.value)
                    val raw = try { data.toBytes() } finally { CFRelease(data) }
                    try {
                        require(raw.size == 97 && raw[0] == 4.toByte()) { "Invalid P-256 private representation" }
                        val base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
                        val jwk = kotlinx.serialization.json.buildJsonObject {
                            put("kty", kotlinx.serialization.json.JsonPrimitive("EC"))
                            put("crv", kotlinx.serialization.json.JsonPrimitive("P-256"))
                            for ((name, offset) in listOf("x" to 1, "y" to 33, "d" to 65)) {
                                val component = raw.copyOfRange(offset, offset + 32)
                                try { put(name, kotlinx.serialization.json.JsonPrimitive(base64.encode(component))) }
                                finally { component.fill(0) }
                            }
                        }
                        EncodedKey.Jwk(BinaryData(jwk.toString().encodeToByteArray()), true)
                    } finally { raw.fill(0) }
                } } }
            }
        } else null

        override val signatureAlgorithms = setOf(SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256))
        override val keyAgreementAlgorithms: Set<KeyAgreementAlgorithm> = emptySet()


        override suspend fun sign(data: ByteArray, algorithm: SignatureAlgorithm): ByteArray = withContext(Dispatchers.Default) {
            require(algorithm == SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256)) { "Unsupported signature algorithm" }
            val coroutineContext = currentCoroutineContext()
            authorization.use { context ->
                val signature = withKey(alias, policy, nativeIdentity, context) { key ->
                    retained(data.toNSData()) { input -> memScoped {
                        val error = alloc<CFErrorRefVar>(); error.value = null
                        val result = SecKeyCreateSignature(key, kSecKeyAlgorithmECDSASignatureMessageX962SHA256,
                            input as CFDataRef, error.ptr) ?: throw takeKeychainFailure(alias, error.value)
                        try { result.toBytes() } finally { CFRelease(result) }
                    } }
                }
                coroutineContext.ensureActive()
                EcdsaSignatureCodec.derToP1363(signature, 32)
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

    // Handles are reconstructed from storage. Keep only authorization state across those lookups,
    // never a cached key or public key that could conceal native deletion or replacement.
    private val sessionsLock = NSLock()
    private val sessions = linkedMapOf<Pair<String, SignumKeyPolicy>, AuthorizationSession>()

    private fun session(alias: String, policy: SignumKeyPolicy): AuthorizationSession {
        sessionsLock.lock()
        try {
            return sessions.getOrPut(alias to policy) {
                if (sessions.size >= 64) sessions.remove(sessions.keys.first())?.invalidate()
                AuthorizationSession(policy.authentication as? SignumAuthenticationPolicy.UserPresence)
            }
        } finally { sessionsLock.unlock() }
    }

    private class AuthorizationSession(private val policy: SignumAuthenticationPolicy.UserPresence?) {
        private val lock = NSRecursiveLock()
        private var context: LAContext? = null
        private var authenticatedAt: TimeSource.Monotonic.ValueTimeMark? = null

        fun <T> use(operation: (LAContext) -> T): T {
            lock.lock()
            try {
                val reusable = policy != null && policy.timeoutSeconds > 0 &&
                    authenticatedAt?.elapsedNow()?.inWholeMilliseconds?.let { it < policy.timeoutSeconds * 1000L } == true
                val active = if (reusable) requireNotNull(context) else LAContext().apply {
                    localizedReason = policy?.prompt ?: "Authorize signing"
                    localizedCancelTitle = policy?.cancelText ?: "Cancel"
                }
                try {
                    val result = operation(active)
                    if (!reusable && policy != null && policy.timeoutSeconds > 0) {
                        context?.invalidate()
                        context = active
                        authenticatedAt = TimeSource.Monotonic.markNow()
                    } else if (policy == null || policy.timeoutSeconds == 0) active.invalidate()
                    return result
                } catch (cause: Throwable) {
                    active.invalidate()
                    invalidate()
                    throw cause
                }
            } finally { lock.unlock() }
        }

        fun invalidate() {
            lock.lock()
            try {
                context?.invalidate()
                context = null
                authenticatedAt = null
            } finally { lock.unlock() }
        }
    }

    private fun attributes(): KeychainDictionary = KeychainDictionary().apply {
        put(kSecAttrKeyType, kSecAttrKeyTypeECSECPrimeRandom)
        putRetained(kSecAttrKeySizeInBits, 256)
        // Access control is installed by SecItemAdd; imported keys never receive an Enclave token.
    }

    private fun query(alias: String, policy: SignumKeyPolicy): KeychainDictionary = KeychainDictionary().apply {
        put(kSecClass, kSecClassKey)
        put(kSecAttrKeyType, kSecAttrKeyTypeECSECPrimeRandom)
        put(kSecAttrKeyClass, kSecAttrKeyClassPrivate)
        putRetained(kSecAttrApplicationTag, "id.walt.crypto2.native:$alias".encodeToByteArray().toNSData())
        (policy.platform as? PlatformKeyConfiguration.IosKeychain)?.accessGroup?.let { putRetained(kSecAttrAccessGroup, it) }
        put(kSecUseDataProtectionKeychain, kCFBooleanTrue)
    }

    private fun addAccessControl(dictionary: KeychainDictionary, policy: SignumKeyPolicy, enclave: Boolean) {
        val settings = policy.platform as? PlatformKeyConfiguration.IosKeychain ?: PlatformKeyConfiguration.IosKeychain()
        val accessibility = settings.accessibility.nativeAccessibility
        val auth = policy.authentication as? SignumAuthenticationPolicy.UserPresence
        if (auth == null && !enclave) { dictionary.put(kSecAttrAccessible, accessibility); return }
        val flags = when {
            auth == null -> 0uL
            auth.biometric && auth.deviceCredential -> kSecAccessControlUserPresence
            auth.biometric && !auth.allowNewBiometrics -> kSecAccessControlBiometryCurrentSet
            auth.biometric -> kSecAccessControlBiometryAny
            else -> kSecAccessControlDevicePasscode
        } or (if (enclave) kSecAccessControlPrivateKeyUsage else 0uL)
        memScoped {
            val error = alloc<CFErrorRefVar>(); error.value = null
            val control = SecAccessControlCreateWithFlags(kCFAllocatorDefault, accessibility, flags, error.ptr)
                ?: throw takeKeychainFailure("access control", error.value)
            try { dictionary.put(kSecAttrAccessControl, control) } finally { CFRelease(control) }
        }
    }

    private fun <T> withKey(alias: String, policy: SignumKeyPolicy, native: IosKeyIdentity,
        context: LAContext?, block: (SecKeyRef) -> T): T {
        requireIosKeyBiometrics(alias, policy)
        return referenceQuery(native).use { query ->
            query.put(kSecReturnRef, kCFBooleanTrue)
            context?.let { query.putRetained(kSecUseAuthenticationContext, it) }
            memScoped {
                val result = alloc<CFTypeRefVar>(); result.value = null
                checkKeychainStatus(SecItemCopyMatching(query.ref, result.ptr), alias)
                val key = result.value as? SecKeyRef ?: error("Keychain returned no key")
                try { block(key) } finally { CFRelease(key) }
            }
        }
    }

    private fun publicKey(alias: String, key: SecKeyRef): EncodedKey.SpkiDer {
        val public = SecKeyCopyPublicKey(key) ?: error("Keychain public key unavailable")
        try {
            return memScoped {
                val error = alloc<CFErrorRefVar>(); error.value = null
                val data = SecKeyCopyExternalRepresentation(public, error.ptr) ?: throw takeKeychainFailure(alias, error.value)
                try {
                    val point = data.toBytes()
                    require(point.size == 65 && point[0] == 4.toByte()) { "Invalid P-256 public key" }
                    EncodedKey.SpkiDer(BinaryData(SPKI_PREFIX + point))
                } finally { CFRelease(data) }
            }
        } finally { CFRelease(public) }
    }

    private fun rawPrivateKey(material: EncodedKey.Jwk): ByteArray {
        val jwk = Json.parseToJsonElement(material.data.toByteArray().decodeToString()).jsonObject
        val base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
        fun value(name: String) = base64.decode(jwk.getValue(name).jsonPrimitive.content).also { require(it.size == 32) }
        return byteArrayOf(4) + value("x") + value("y") + value("d")
    }

    private val SPKI_PREFIX = byteArrayOf(0x30,0x59,0x30,0x13,0x06,0x07,0x2a,0x86.toByte(),0x48,0xce.toByte(),0x3d,0x02,0x01,
        0x06,0x08,0x2a,0x86.toByte(),0x48,0xce.toByte(),0x3d,0x03,0x01,0x07,0x03,0x42,0x00)
}
