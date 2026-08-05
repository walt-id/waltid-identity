package id.walt.crypto2.signum

import at.asitplus.signum.supreme.os.IosKeychainProvider
import at.asitplus.signum.supreme.os.IosSigner
import at.asitplus.signum.supreme.CFCryptoOperationFailed
import at.asitplus.signum.supreme.os.PlatformSigningProviderSigner
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.ProviderId
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryGetValue
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSProcessInfo
import platform.Security.SecItemCopyMatching
import platform.Security.SecKeyCopyAttributes
import platform.Security.SecKeyRef
import platform.Security.errSecSuccess
import platform.Security.errSecItemNotFound
import platform.Security.kSecAttrApplicationLabel
import platform.Security.kSecAttrKeyClass
import platform.Security.kSecAttrKeyClassPublic
import platform.Security.kSecAttrTokenID
import platform.Security.kSecAttrTokenIDSecureEnclave
import platform.Security.kSecClass
import platform.Security.kSecClassKey
import platform.Security.kSecReturnRef
import kotlin.coroutines.cancellation.CancellationException

class IosSignumKeyBackend : SignumPlatformBackend {
    override val id = ProviderId("ios-keychain-signum")

    override fun supports(spec: KeySpec, usages: Set<KeyUsage>, policy: SignumKeyPolicy): Boolean =
        spec.isSupportedSignumSpec() &&
            usages.all { it == KeyUsage.SIGN || it == KeyUsage.VERIFY || it == KeyUsage.KEY_AGREEMENT } &&
            (KeyUsage.KEY_AGREEMENT !in usages || spec is KeySpec.Ec) &&
            (KeyUsage.KEY_AGREEMENT in usages) == policy.keyAgreement &&
            (policy.hardware != SignumHardwarePolicy.REQUIRED || spec == KeySpec.Ec(EcCurve.P256))

    override suspend fun create(
        alias: String,
        spec: KeySpec,
        usages: Set<KeyUsage>,
        policy: SignumKeyPolicy,
    ): SignumPlatformKey {
        require(supports(spec, usages, policy)) { "iOS Signum backend does not support the requested key and policy" }
        val signer = try {
            createSigner(alias, spec, usages, policy.withoutUnavailableSecureEnclave())
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Throwable) {
            // Signum turns SignumHardwarePolicy.PREFERRED into kSecAttrTokenIDSecureEnclave with no fallback of its
            // own, so SecKeyGeneratePair fails outright wherever no Secure Enclave exists or where it rejects the
            // configuration. PREFERRED has to mean preferred, so fall back to the software keychain. REQUIRED and
            // attested keys keep failing loudly, and the reported protection level stays UNKNOWN either way
            // because without an attestation the backing cannot be proven (see effectiveProtection).
            if (policy.hardware != SignumHardwarePolicy.PREFERRED ||
                policy.attestationChallenge != null ||
                policy.authentication.isBiometricCurrentSet()
            ) throw cause
            // Best-effort cleanup of anything the failed attempt left behind; a failure here must not hide `cause`.
            try {
                delete(alias)
            } catch (ignored: Throwable) {
                cause.addSuppressed(ignored)
            }
            createSigner(alias, spec, usages, policy.copy(hardware = SignumHardwarePolicy.DISCOURAGED))
        }
        try {
            validateNativePolicy(signer, policy, alias)
        } catch (cause: Throwable) {
            try {
                delete(alias)
            } catch (cleanupFailure: Throwable) {
                cause.addSuppressed(cleanupFailure)
            }
            throw cause
        }
        return handle(alias, spec, usages, policy, signer)
    }

    /**
     * The iOS simulator has no Secure Enclave, so asking for one only produces a failed key generation whose
     * half-created keychain entries then get in the way of the retry. Decide up front instead of failing first.
     */
    private fun SignumKeyPolicy.withoutUnavailableSecureEnclave(): SignumKeyPolicy =
        if (hardware == SignumHardwarePolicy.PREFERRED && isSimulator) {
            copy(hardware = SignumHardwarePolicy.DISCOURAGED)
        } else this

    private suspend fun createSigner(
        alias: String,
        spec: KeySpec,
        usages: Set<KeyUsage>,
        policy: SignumKeyPolicy,
    ): PlatformSigningProviderSigner<*, *> = IosKeychainProvider.createSigningKey(alias) {
        configureSignumKey(spec, usages, policy)
    }.getOrThrow()

    override suspend fun load(
        alias: String,
        spec: KeySpec,
        usages: Set<KeyUsage>,
        policy: SignumKeyPolicy,
    ): SignumPlatformKey? {
        val signer = IosKeychainProvider.getSignerForKey(alias).getOrElse { failure ->
            throw failure.mapSignumFailure(alias)
        }
        validateNativePolicy(signer, policy, alias)
        return handle(alias, spec, usages, policy, signer)
    }

    override suspend fun delete(alias: String) {
        IosKeychainProvider.deleteSigningKey(alias).getOrElse { failure ->
            val mapped = failure.mapSignumFailure(alias)
            if (mapped is SignumKeyNotFoundException) return
            throw mapped
        }
    }

    private fun handle(
        alias: String,
        spec: KeySpec,
        usages: Set<KeyUsage>,
        policy: SignumKeyPolicy,
        signer: PlatformSigningProviderSigner<*, *>,
    ): SignumPlatformKey {
        val attestation = signer.toAttestation()
        return SignumPlatformKeyHandle(
            alias = alias,
            spec = spec,
            protectionLevel = policy.effectiveProtection(attestation),
            attestation = attestation,
            authentication = policy.authentication,
            signerFor = { algorithm: SignatureAlgorithm ->
                IosKeychainProvider.getSignerForKey(alias) {
                    configureSignumOperation(algorithm, policy.authentication)
                }.getOrThrow()
            },
            defaultSigner = signer,
            keyAgreementEnabled = KeyUsage.KEY_AGREEMENT in usages && policy.keyAgreement,
        )
    }

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    private fun validateNativePolicy(
        signer: PlatformSigningProviderSigner<*, *>,
        policy: SignumKeyPolicy,
        alias: String,
    ) {
        if (!policy.authentication.isBiometricCurrentSet()) return
        val iosSigner = signer as? IosSigner
            ?: throw SignumKeyPolicyMismatchException(alias, "the native signer is not Keychain-backed")
        if (!iosSigner.needsAuthenticationForEveryUse || !isSecureEnclaveKey(alias)) {
            throw SignumKeyPolicyMismatchException(
                alias,
                "the native key does not enforce Secure Enclave biometric authentication for every use",
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    private fun isSecureEnclaveKey(alias: String): Boolean = memScoped {
        val keyRef = alloc<platform.CoreFoundation.CFTypeRefVar>()
        val query = RetainedDictionary(5)
        query.add(kSecClass, kSecClassKey)
        // The public half exposes the token identifier without evaluating the
        // private key's access-control prompt during restore.
        query.add(kSecAttrKeyClass, kSecAttrKeyClassPublic)
        query.addRetained(kSecAttrApplicationLabel, alias)
        query.add(kSecReturnRef, kCFBooleanTrue)
        try {
            if (SecItemCopyMatching(query.dictionary, keyRef.ptr) != errSecSuccess || keyRef.value == null) {
                return@memScoped false
            }
            val nativeKey = keyRef.value as? SecKeyRef ?: return@memScoped false
            val attributes = SecKeyCopyAttributes(nativeKey) ?: return@memScoped false
            try {
                CFDictionaryGetValue(attributes, kSecAttrTokenID) == kSecAttrTokenIDSecureEnclave
            } finally {
                CFRelease(attributes)
            }
        } finally {
            query.release()
        }
    }

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
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
}

private fun Throwable.mapSignumFailure(alias: String): Throwable {
    val causes = generateSequence(this) { it.cause }.toList()
    return if (causes.filterIsInstance<CFCryptoOperationFailed>().any { it.osStatus == errSecItemNotFound }) {
        SignumKeyNotFoundException(alias, this)
    } else {
        this
    }
}

private fun KeySpec.isSupportedSignumSpec(): Boolean = when (this) {
    is KeySpec.Ec -> curve == EcCurve.P256 || curve == EcCurve.P384 || curve == EcCurve.P521
    is KeySpec.Rsa -> bits == 2048 || bits == 3072 || bits == 4096
    else -> false
}

/** `simctl spawn` exports the simulator device environment; a real device never has it. */
private val isSimulator: Boolean by lazy {
    NSProcessInfo.processInfo.environment.keys.any { it == "SIMULATOR_UDID" || it == "SIMULATOR_DEVICE_NAME" }
}
