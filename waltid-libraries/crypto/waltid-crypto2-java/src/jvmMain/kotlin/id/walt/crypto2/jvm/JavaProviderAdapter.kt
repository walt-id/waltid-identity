package id.walt.crypto2.jvm

import id.walt.crypto2.algorithms.AsymmetricEncryptionAlgorithm
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.KeyAgreementAlgorithm
import id.walt.crypto2.algorithms.KeyWrappingAlgorithm
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.Decryptor
import id.walt.crypto2.keys.AsymmetricCiphertext
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EdwardsCurve
import id.walt.crypto2.keys.EncodedKeyMaterial
import id.walt.crypto2.keys.Encryptor
import id.walt.crypto2.keys.KeyAgreement
import id.walt.crypto2.keys.KeyCapabilities
import id.walt.crypto2.keys.KeyDeleter
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUnwrapper
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.KeyWrapper
import id.walt.crypto2.keys.ManagedKey
import id.walt.crypto2.keys.MontgomeryCurve
import id.walt.crypto2.keys.ProviderId
import id.walt.crypto2.keys.PublicKeyExporter
import id.walt.crypto2.keys.Signer
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.keys.SymmetricKeyType
import id.walt.crypto2.keys.Verifier
import id.walt.crypto2.keys.WrappedKey
import id.walt.crypto2.providers.GenerateManagedKeyRequest
import id.walt.crypto2.providers.ManagedKeyProvider
import id.walt.crypto2.serialization.BinaryData
import kotlinx.coroutines.future.await
import java.util.ServiceLoader
import java.util.Collections
import java.util.concurrent.CompletionStage

fun JavaManagedKeyProvider.asKotlinProvider(): ManagedKeyProvider = JavaManagedKeyProviderAdapter(this)

object JavaManagedKeyProviders {
    @JvmStatic
    @JvmOverloads
    fun load(classLoader: ClassLoader? = Thread.currentThread().contextClassLoader): List<ManagedKeyProvider> =
        ServiceLoader.load(JavaManagedKeyProvider::class.java, classLoader ?: JavaManagedKeyProvider::class.java.classLoader)
            .map { it.asKotlinProvider() }
}

private class JavaManagedKeyProviderAdapter(
    private val delegate: JavaManagedKeyProvider,
) : ManagedKeyProvider {
    override val id = ProviderId(requireNotNull(delegate.id()) { "Java provider ID cannot be null" })

    override suspend fun generate(request: GenerateManagedKeyRequest): ManagedKey {
        val javaKey = awaitRequired(delegate.generate(request.toJava()), "Java managed-key generation")
        val stored = requireNotNull(javaKey.reference) { "Generated Java key reference cannot be null" }.toStoredKey()
        require(stored.provider == id) { "Generated key provider does not match ${id.value}" }
        require(stored.id == request.id) { "Generated key ID does not match request" }
        require(stored.spec == request.spec) { "Generated key specification does not match request" }
        require(stored.usages == request.usages) { "Generated key usages do not match request" }
        return JavaManagedKeyAdapter(javaKey, stored)
    }

    override suspend fun restore(stored: StoredKey.Managed): ManagedKey {
        val javaKey = awaitRequired(delegate.restore(stored.toJava()), "Java managed-key restoration")
        val restored = requireNotNull(javaKey.reference) { "Restored Java key reference cannot be null" }.toStoredKey()
        require(restored.version == stored.version) { "Restored key version changed" }
        require(restored.provider == stored.provider) { "Restored key provider changed" }
        require(restored.id == stored.id) { "Restored key ID changed" }
        require(restored.spec == stored.spec) { "Restored key specification changed" }
        require(restored.usages == stored.usages) { "Restored key usages changed" }
        require(restored.providerSchemaVersion == stored.providerSchemaVersion) { "Restored provider schema changed" }
        require(restored.providerData == stored.providerData) { "Restored provider data changed" }
        return JavaManagedKeyAdapter(javaKey, restored)
    }

    override suspend fun close() {
        awaitCompletion(delegate.close(), "Java managed-key provider close")
    }
}

private class JavaManagedKeyAdapter(
    private val delegate: JavaManagedKey,
    override val storedKey: StoredKey.Managed,
) : ManagedKey {
    private val signatureAlgorithms = Collections.unmodifiableSet(
        requireNotNull(delegate.signatureAlgorithms()).mapTo(linkedSetOf(), JavaSignatureAlgorithm::toKotlin)
            .takeIf {
                (KeyUsage.SIGN in storedKey.usages && delegate.signer() != null) ||
                    (KeyUsage.VERIFY in storedKey.usages && delegate.verifier() != null)
            }
            .orEmpty(),
    )
    private val encryptionAlgorithms = Collections.unmodifiableSet(
        requireNotNull(delegate.encryptionAlgorithms()).mapTo(linkedSetOf(), JavaAsymmetricEncryptionAlgorithm::toKotlin)
            .takeIf {
                (KeyUsage.ENCRYPT in storedKey.usages && delegate.encryptor() != null) ||
                    (KeyUsage.DECRYPT in storedKey.usages && delegate.decryptor() != null)
            }
            .orEmpty(),
    )
    private val agreementAlgorithms = Collections.unmodifiableSet(
        requireNotNull(delegate.keyAgreementAlgorithms()).mapTo(linkedSetOf(), JavaNamedAlgorithm::toKeyAgreement)
            .takeIf { KeyUsage.KEY_AGREEMENT in storedKey.usages && delegate.keyAgreement() != null }
            .orEmpty(),
    )
    private val wrappingAlgorithms = Collections.unmodifiableSet(
        requireNotNull(delegate.keyWrappingAlgorithms()).mapTo(linkedSetOf(), JavaNamedAlgorithm::toKeyWrapping)
            .takeIf {
                (KeyUsage.WRAP in storedKey.usages && delegate.keyWrapper() != null) ||
                    (KeyUsage.UNWRAP in storedKey.usages && delegate.keyUnwrapper() != null)
            }
            .orEmpty(),
    )

    init {
        require(signatureAlgorithms.all(::isSignatureCompatibleWithKey)) {
            "Java provider advertised a signature algorithm incompatible with the key specification"
        }
        require(encryptionAlgorithms.all(::isEncryptionCompatibleWithKey)) {
            "Java provider advertised an encryption algorithm incompatible with the key specification"
        }
        require(agreementAlgorithms.all(::isAgreementCompatibleWithKey)) {
            "Java provider advertised an agreement algorithm incompatible with the key specification"
        }
        require(wrappingAlgorithms.all(::isWrappingCompatibleWithKey)) {
            "Java provider advertised a wrapping algorithm incompatible with the key specification"
        }
    }

    override val capabilities = KeyCapabilities(
        signer = delegate.signer()?.also { requireUsage(KeyUsage.SIGN, "signing") }
            ?.let { java ->
                Signer { data, algorithm ->
                    require(isSignatureCompatibleWithKey(algorithm)) { "Signature algorithm is incompatible with key" }
                    require(delegate.supportsSignatureAlgorithm(algorithm.toJava())) {
                        "Java key does not support signature algorithm"
                    }
                    awaitRequired(java.sign(data, algorithm.toJava()), "Java signing")
                }
            },
        verifier = delegate.verifier()?.also { requireUsage(KeyUsage.VERIFY, "verification") }
            ?.let { java ->
                Verifier { data, signature, algorithm ->
                    require(isSignatureCompatibleWithKey(algorithm)) { "Signature algorithm is incompatible with key" }
                    require(delegate.supportsSignatureAlgorithm(algorithm.toJava())) {
                        "Java key does not support signature algorithm"
                    }
                    awaitRequired(java.verify(data, signature, algorithm.toJava()), "Java verification")
                }
            },
        encryptor = delegate.encryptor()?.also { requireUsage(KeyUsage.ENCRYPT, "encryption") }
            ?.let { java ->
                Encryptor { plaintext, algorithm, associatedData ->
                    require(isEncryptionCompatibleWithKey(algorithm)) { "Encryption algorithm is incompatible with key" }
                    require(delegate.supportsEncryptionAlgorithm(algorithm.toJava())) {
                        "Java key does not support encryption algorithm"
                    }
                    val encrypted = awaitRequired(
                        java.encrypt(plaintext, algorithm.toJava(), associatedData),
                        "Java encryption",
                    ).toKotlin()
                    require(encrypted.algorithm == algorithm) { "Java provider returned a different encryption algorithm" }
                    validateOpaqueCiphertextIdentity(encrypted)
                    encrypted
                }
            },
        decryptor = delegate.decryptor()?.also { requireUsage(KeyUsage.DECRYPT, "decryption") }
            ?.let { java ->
                Decryptor { ciphertext, associatedData ->
                    val algorithm = ciphertext.algorithm
                    require(isEncryptionCompatibleWithKey(algorithm)) { "Encryption algorithm is incompatible with key" }
                    require(delegate.supportsEncryptionAlgorithm(algorithm.toJava())) {
                        "Java key does not support encryption algorithm"
                    }
                    validateOpaqueCiphertextIdentity(ciphertext)
                    awaitRequired(java.decrypt(ciphertext.toJava(), associatedData), "Java decryption")
                }
            },
        keyAgreement = delegate.keyAgreement()
            ?.also { requireUsage(KeyUsage.KEY_AGREEMENT, "key agreement") }
            ?.let { java ->
                KeyAgreement { peerPublicKey, algorithm ->
                    require(isAgreementCompatibleWithKey(algorithm)) { "Agreement algorithm is incompatible with key" }
                    require(delegate.supportsKeyAgreementAlgorithm(algorithm.toJava())) {
                        "Java key does not support agreement algorithm"
                    }
                    BinaryData(awaitRequired(java.generateSharedSecret(peerPublicKey, algorithm.toJava()), "Java key agreement"))
                }
            },
        keyWrapper = delegate.keyWrapper()
            ?.also { requireUsage(KeyUsage.WRAP, "key wrapping") }
            ?.let { java ->
            KeyWrapper { key, algorithm ->
                require(isWrappingCompatibleWithKey(algorithm)) { "Wrapping algorithm is incompatible with key" }
                require(delegate.supportsKeyWrappingAlgorithm(algorithm.toJava())) {
                    "Java key does not support wrapping algorithm"
                }
                val wrapped = awaitRequired(java.wrapKey(key.toJava(), algorithm.toJava()), "Java key wrapping").toKotlin()
                require(wrapped.algorithm == algorithm) { "Java provider returned a different wrapping algorithm" }
                require(wrapped.wrappedKeySpec == key.spec) { "Java provider returned a different wrapped-key spec" }
                validateOpaqueWrappedKeyIdentity(wrapped)
                wrapped
            }
        },
        keyUnwrapper = delegate.keyUnwrapper()
            ?.also { requireUsage(KeyUsage.UNWRAP, "key unwrapping") }
            ?.let { java ->
            KeyUnwrapper { wrappedKey ->
                val algorithm = wrappedKey.algorithm
                require(isWrappingCompatibleWithKey(algorithm)) { "Wrapping algorithm is incompatible with key" }
                require(delegate.supportsKeyWrappingAlgorithm(algorithm.toJava())) {
                    "Java key does not support wrapping algorithm"
                }
                validateOpaqueWrappedKeyIdentity(wrappedKey)
                val unwrapped = awaitRequired(java.unwrapKey(wrappedKey.toJava()), "Java key unwrapping").toKotlin()
                require(unwrapped.spec == wrappedKey.wrappedKeySpec) { "Java provider returned a different unwrapped-key spec" }
                unwrapped
            }
        },
        deleter = delegate.deleter()?.let { java ->
            KeyDeleter { awaitRequired(java.delete(), "Java key deletion") }
        },
        publicKeyExporter = storedKey.publicKey?.let { publicKey -> PublicKeyExporter { publicKey } },
        signatureAlgorithms = signatureAlgorithms,
        encryptionAlgorithms = encryptionAlgorithms,
        keyAgreementAlgorithms = agreementAlgorithms,
        keyWrappingAlgorithms = wrappingAlgorithms,
        supportsSignatureAlgorithm = {
            (KeyUsage.SIGN in storedKey.usages || KeyUsage.VERIFY in storedKey.usages) &&
                (delegate.signer() != null || delegate.verifier() != null) &&
                isSignatureCompatibleWithKey(it) && delegate.supportsSignatureAlgorithm(it.toJava())
        },
        supportsEncryptionAlgorithm = {
            (KeyUsage.ENCRYPT in storedKey.usages || KeyUsage.DECRYPT in storedKey.usages) &&
                (delegate.encryptor() != null || delegate.decryptor() != null) &&
                isEncryptionCompatibleWithKey(it) && delegate.supportsEncryptionAlgorithm(it.toJava())
        },
        supportsKeyAgreementAlgorithm = {
            KeyUsage.KEY_AGREEMENT in storedKey.usages &&
                delegate.keyAgreement() != null && isAgreementCompatibleWithKey(it) &&
                delegate.supportsKeyAgreementAlgorithm(it.toJava())
        },
        supportsKeyWrappingAlgorithm = {
            (KeyUsage.WRAP in storedKey.usages || KeyUsage.UNWRAP in storedKey.usages) &&
                (delegate.keyWrapper() != null || delegate.keyUnwrapper() != null) &&
                isWrappingCompatibleWithKey(it) && delegate.supportsKeyWrappingAlgorithm(it.toJava())
        },
    )

    private fun requireUsage(usage: KeyUsage, capability: String) {
        require(usage in storedKey.usages) { "Java key exposes $capability without $usage usage" }
    }

    private fun validateOpaqueCiphertextIdentity(ciphertext: AsymmetricCiphertext) {
        if (ciphertext is AsymmetricCiphertext.Opaque) {
            require(ciphertext.provider == storedKey.provider) { "Opaque ciphertext provider does not match key" }
            require(ciphertext.keyId == storedKey.id) { "Opaque ciphertext key ID does not match key" }
        }
    }

    private fun validateOpaqueWrappedKeyIdentity(wrappedKey: WrappedKey) {
        when (wrappedKey) {
            is WrappedKey.Opaque -> {
                require(wrappedKey.provider == storedKey.provider) { "Opaque wrapped-key provider does not match key" }
                require(wrappedKey.wrappingKeyId == storedKey.id) { "Opaque wrapped-key ID does not match key" }
            }
            is WrappedKey.Raw -> wrappedKey.wrappingKeyId?.let {
                require(it == storedKey.id) { "Raw wrapped-key ID does not match key" }
            }
        }
    }

    private fun isSignatureCompatibleWithKey(algorithm: SignatureAlgorithm): Boolean = when (algorithm) {
        is SignatureAlgorithm.Ecdsa -> storedKey.spec is KeySpec.Ec
        SignatureAlgorithm.EdDsa -> storedKey.spec is KeySpec.Edwards
        is SignatureAlgorithm.RsaPkcs1, is SignatureAlgorithm.RsaPss -> storedKey.spec is KeySpec.Rsa
        is SignatureAlgorithm.Custom -> true
    }

    private fun isEncryptionCompatibleWithKey(algorithm: AsymmetricEncryptionAlgorithm): Boolean = when (algorithm) {
        is AsymmetricEncryptionAlgorithm.RsaOaep, AsymmetricEncryptionAlgorithm.RsaPkcs1 ->
            storedKey.spec is KeySpec.Rsa
        is AsymmetricEncryptionAlgorithm.Custom -> true
    }

    private fun isAgreementCompatibleWithKey(algorithm: KeyAgreementAlgorithm): Boolean = when (algorithm) {
        KeyAgreementAlgorithm.Ecdh -> storedKey.spec is KeySpec.Ec
        KeyAgreementAlgorithm.Xdh -> storedKey.spec is KeySpec.Montgomery
        is KeyAgreementAlgorithm.Named,
        is KeyAgreementAlgorithm.Custom,
        -> true
    }

    private fun isWrappingCompatibleWithKey(algorithm: KeyWrappingAlgorithm): Boolean = when (algorithm) {
        is KeyWrappingAlgorithm.BuiltIn -> when (algorithm.id) {
            "A128KW" -> storedKey.spec == KeySpec.Symmetric(SymmetricKeyType.AES, 128)
            "A192KW" -> storedKey.spec == KeySpec.Symmetric(SymmetricKeyType.AES, 192)
            "A256KW" -> storedKey.spec == KeySpec.Symmetric(SymmetricKeyType.AES, 256)
            else -> false
        }
        is KeyWrappingAlgorithm.Named, is KeyWrappingAlgorithm.Custom -> true
    }

}

private fun StoredKey.Managed.toJava() = JavaManagedKeyReference(
    version,
    id.value,
    spec.toJava(),
    usages,
    provider.value,
    providerSchemaVersion,
    providerData.toByteArray(),
    publicKey,
    metadata,
)

private fun JavaManagedKeyReference.toStoredKey() = StoredKey.Managed(
    version = version,
    id = KeyId(id),
    spec = spec.toKotlin(),
    usages = usages,
    provider = ProviderId(provider),
    providerSchemaVersion = providerSchemaVersion,
    providerData = BinaryData(providerData),
    publicKey = publicKey,
    metadata = metadata,
)

private fun GenerateManagedKeyRequest.toJava() = JavaGenerateManagedKeyRequest(
    id.value,
    spec.toJava(),
    usages,
    providerOptions.toByteArray(),
    metadata,
)

private suspend fun <T : Any> awaitRequired(stage: CompletionStage<T>?, operation: String): T =
    requireNotNull(requireNotNull(stage) { "$operation returned a null CompletionStage" }.await()) {
        "$operation completed with null"
    }

private suspend fun awaitCompletion(stage: CompletionStage<*>?, operation: String) {
    requireNotNull(stage) { "$operation returned a null CompletionStage" }.await()
}

private fun SignatureAlgorithm.toJava(): JavaSignatureAlgorithm = when (this) {
    is SignatureAlgorithm.Ecdsa -> JavaSignatureAlgorithm.ecdsa(
        JavaDigestAlgorithm(digest.name),
        encoding.name,
    )
    SignatureAlgorithm.EdDsa -> JavaSignatureAlgorithm.edDsa()
    is SignatureAlgorithm.RsaPkcs1 -> JavaSignatureAlgorithm.rsaPkcs1(JavaDigestAlgorithm(digest.name))
    is SignatureAlgorithm.RsaPss -> JavaSignatureAlgorithm.rsaPss(
        JavaDigestAlgorithm(digest.name),
        JavaDigestAlgorithm(mgfDigest.name),
        saltLengthBytes,
    )
    is SignatureAlgorithm.Custom -> JavaSignatureAlgorithm.custom(id, parameters)
}

private fun JavaSignatureAlgorithm.toKotlin(): SignatureAlgorithm = when (type) {
    JavaSignatureAlgorithm.Type.ECDSA -> SignatureAlgorithm.Ecdsa(
        DigestAlgorithm(requireNotNull(digest).name),
        EcdsaSignatureEncoding.valueOf(requireNotNull(encoding)),
    )
    JavaSignatureAlgorithm.Type.EDDSA -> SignatureAlgorithm.EdDsa
    JavaSignatureAlgorithm.Type.RSA_PKCS1 -> SignatureAlgorithm.RsaPkcs1(DigestAlgorithm(requireNotNull(digest).name))
    JavaSignatureAlgorithm.Type.RSA_PSS -> SignatureAlgorithm.RsaPss(
        DigestAlgorithm(requireNotNull(digest).name),
        DigestAlgorithm(requireNotNull(mgfDigest).name),
        saltLengthBytes,
    )
    JavaSignatureAlgorithm.Type.CUSTOM -> SignatureAlgorithm.Custom(requireNotNull(customId), parameters)
}

private fun AsymmetricEncryptionAlgorithm.toJava(): JavaAsymmetricEncryptionAlgorithm = when (this) {
    is AsymmetricEncryptionAlgorithm.RsaOaep -> JavaAsymmetricEncryptionAlgorithm.rsaOaep(
        JavaDigestAlgorithm(digest.name),
        JavaDigestAlgorithm(mgfDigest.name),
    )
    AsymmetricEncryptionAlgorithm.RsaPkcs1 -> JavaAsymmetricEncryptionAlgorithm.rsaPkcs1()
    is AsymmetricEncryptionAlgorithm.Custom -> JavaAsymmetricEncryptionAlgorithm.custom(id, parameters)
}

private fun JavaAsymmetricEncryptionAlgorithm.toKotlin(): AsymmetricEncryptionAlgorithm = when (type) {
    JavaAsymmetricEncryptionAlgorithm.Type.RSA_OAEP -> AsymmetricEncryptionAlgorithm.RsaOaep(
        DigestAlgorithm(requireNotNull(digest).name),
        DigestAlgorithm(requireNotNull(mgfDigest).name),
    )
    JavaAsymmetricEncryptionAlgorithm.Type.RSA_PKCS1 -> AsymmetricEncryptionAlgorithm.RsaPkcs1
    JavaAsymmetricEncryptionAlgorithm.Type.CUSTOM ->
        AsymmetricEncryptionAlgorithm.Custom(requireNotNull(customId), parameters)
}

private fun KeyAgreementAlgorithm.toJava(): JavaNamedAlgorithm = when (this) {
    KeyAgreementAlgorithm.Ecdh -> JavaNamedAlgorithm.builtin("ECDH")
    KeyAgreementAlgorithm.Xdh -> JavaNamedAlgorithm.builtin("XDH")
    is KeyAgreementAlgorithm.Named -> JavaNamedAlgorithm.named(id, parameters)
    is KeyAgreementAlgorithm.Custom -> JavaNamedAlgorithm.custom(id, parameters)
}

private fun JavaNamedAlgorithm.toKeyAgreement(): KeyAgreementAlgorithm = when (type) {
    JavaNamedAlgorithm.Type.BUILTIN -> when (id) {
        "ECDH" -> KeyAgreementAlgorithm.Ecdh
        "XDH" -> KeyAgreementAlgorithm.Xdh
        else -> error("Unsupported built-in key-agreement algorithm: $id")
    }
    JavaNamedAlgorithm.Type.NAMED -> KeyAgreementAlgorithm.Named(id, parameters)
    JavaNamedAlgorithm.Type.CUSTOM -> KeyAgreementAlgorithm.Custom(id, parameters)
}

private fun KeyWrappingAlgorithm.toJava(): JavaNamedAlgorithm = when (this) {
    is KeyWrappingAlgorithm.BuiltIn -> JavaNamedAlgorithm.builtin(id)
    is KeyWrappingAlgorithm.Named -> JavaNamedAlgorithm.named(id, parameters)
    is KeyWrappingAlgorithm.Custom -> JavaNamedAlgorithm.custom(id, parameters)
}

private fun JavaNamedAlgorithm.toKeyWrapping(): KeyWrappingAlgorithm = when (type) {
    JavaNamedAlgorithm.Type.BUILTIN -> KeyWrappingAlgorithm.BuiltIn(id)
    JavaNamedAlgorithm.Type.NAMED -> KeyWrappingAlgorithm.Named(id, parameters)
    JavaNamedAlgorithm.Type.CUSTOM -> KeyWrappingAlgorithm.Custom(id, parameters)
}

private fun KeySpec.toJava(): JavaKeySpec = when (this) {
    is KeySpec.Ec -> JavaKeySpec.ec(curve.name)
    is KeySpec.Edwards -> JavaKeySpec.edwards(curve.name)
    is KeySpec.Montgomery -> JavaKeySpec.montgomery(curve.name)
    is KeySpec.Rsa -> JavaKeySpec.rsa(bits)
    is KeySpec.Symmetric -> JavaKeySpec.symmetric(family.name, bits)
    is KeySpec.Custom -> JavaKeySpec.custom(family, parameters)
}

private fun JavaKeySpec.toKotlin(): KeySpec = when (type) {
    JavaKeySpec.Type.EC -> KeySpec.Ec(EcCurve(requireNotNull(name)))
    JavaKeySpec.Type.EDWARDS -> KeySpec.Edwards(EdwardsCurve(requireNotNull(name)))
    JavaKeySpec.Type.MONTGOMERY -> KeySpec.Montgomery(MontgomeryCurve(requireNotNull(name)))
    JavaKeySpec.Type.RSA -> KeySpec.Rsa(requireNotNull(bits))
    JavaKeySpec.Type.SYMMETRIC ->
        KeySpec.Symmetric(SymmetricKeyType(requireNotNull(name)), requireNotNull(bits))
    JavaKeySpec.Type.CUSTOM -> KeySpec.Custom(requireNotNull(name), parameters)
}

private fun AsymmetricCiphertext.toJava(): JavaAsymmetricCiphertext = when (this) {
    is AsymmetricCiphertext.Raw -> JavaAsymmetricCiphertext.raw(algorithm.toJava(), data.toByteArray())
    is AsymmetricCiphertext.Opaque -> JavaAsymmetricCiphertext(
        JavaAsymmetricCiphertext.Type.OPAQUE,
        algorithm.toJava(),
        blob.toByteArray(),
        provider.value,
        keyId.value,
        keyVersion,
        context,
        providerData.toByteArray(),
    )
}

private fun JavaAsymmetricCiphertext.toKotlin(): AsymmetricCiphertext = when (type) {
    JavaAsymmetricCiphertext.Type.RAW -> AsymmetricCiphertext.Raw(
        algorithm.toKotlin(),
        BinaryData(data),
    )
    JavaAsymmetricCiphertext.Type.OPAQUE -> AsymmetricCiphertext.Opaque(
        algorithm = algorithm.toKotlin(),
        provider = ProviderId(requireNotNull(provider)),
        keyId = KeyId(requireNotNull(keyId)),
        blob = BinaryData(data),
        keyVersion = keyVersion,
        context = context,
        providerData = BinaryData(providerData),
    )
}

private fun WrappedKey.toJava(): JavaWrappedKey = when (this) {
    is WrappedKey.Raw -> JavaWrappedKey(
        JavaWrappedKey.Type.RAW,
        algorithm.toJava(),
        blob.toByteArray(),
        wrappedKeySpec.toJava(),
        wrappingKeyId?.value,
        null,
        null,
        byteArrayOf(),
    )
    is WrappedKey.Opaque -> JavaWrappedKey(
        JavaWrappedKey.Type.OPAQUE,
        algorithm.toJava(),
        blob.toByteArray(),
        wrappedKeySpec.toJava(),
        wrappingKeyId.value,
        provider.value,
        keyVersion,
        providerData.toByteArray(),
    )
}

private fun JavaWrappedKey.toKotlin(): WrappedKey = when (type) {
    JavaWrappedKey.Type.RAW -> WrappedKey.Raw(
        algorithm = algorithm.toKeyWrapping(),
        blob = BinaryData(blob),
        wrappedKeySpec = wrappedKeySpec.toKotlin(),
        wrappingKeyId = wrappingKeyId?.let(::KeyId),
    )
    JavaWrappedKey.Type.OPAQUE -> WrappedKey.Opaque(
        algorithm = algorithm.toKeyWrapping(),
        blob = BinaryData(blob),
        wrappedKeySpec = wrappedKeySpec.toKotlin(),
        wrappingKeyId = KeyId(requireNotNull(wrappingKeyId)),
        provider = ProviderId(requireNotNull(provider)),
        keyVersion = keyVersion,
        providerData = BinaryData(providerData),
    )
}

private fun EncodedKeyMaterial.toJava(): JavaEncodedKeyMaterial = JavaEncodedKeyMaterial(spec.toJava(), key)

private fun JavaEncodedKeyMaterial.toKotlin(): EncodedKeyMaterial = EncodedKeyMaterial(spec.toKotlin(), key)
