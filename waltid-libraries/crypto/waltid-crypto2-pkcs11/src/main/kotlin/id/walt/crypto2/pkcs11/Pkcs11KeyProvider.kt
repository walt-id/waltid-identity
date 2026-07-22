package id.walt.crypto2.pkcs11

import id.walt.crypto2.algorithms.AsymmetricEncryptionAlgorithm
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureCodec
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.KeyWrappingAlgorithm
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.algorithms.outputSizeBytes
import id.walt.crypto2.keys.AsymmetricCiphertext
import id.walt.crypto2.keys.Decryptor
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.EncodedKeyMaterial
import id.walt.crypto2.keys.Encryptor
import id.walt.crypto2.keys.KeyCapabilities
import id.walt.crypto2.keys.KeyDeleter
import id.walt.crypto2.keys.KeyDeletionResult
import id.walt.crypto2.keys.KeyEncodingFormat
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUnwrapper
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.KeyWrapper
import id.walt.crypto2.keys.ManagedKey
import id.walt.crypto2.keys.ProviderId
import id.walt.crypto2.keys.PublicKeyExporter
import id.walt.crypto2.keys.Signer
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.keys.Verifier
import id.walt.crypto2.keys.WrappedKey
import id.walt.crypto2.providers.GenerateManagedKeyRequest
import id.walt.crypto2.providers.ManagedKeyProvider
import id.walt.crypto2.serialization.BinaryData
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.KeyPairGenerator
import java.security.Key
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Provider
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PSSParameterSpec
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher

class Pkcs11KeyProvider(
    pinResolver: Pkcs11PinResolver,
) : ManagedKeyProvider {
    override val id = ID
    private val sessions = Pkcs11SessionFactory(pinResolver)
    private val tokenMutexes = ConcurrentHashMap<TokenId, Mutex>()

    override suspend fun generate(request: GenerateManagedKeyRequest): ManagedKey {
        val options = Pkcs11Options.decode(request.providerOptions)
        val alias = options.alias ?: request.id.value
        validateUsages(request.spec, request.usages)
        return withSession(options) { session ->
            require(!session.keyStore.containsAlias(alias)) { "PKCS11 alias already exists: $alias" }
            val keyPair = when (val spec = request.spec) {
                is KeySpec.Ec -> KeyPairGenerator.getInstance("EC", session.provider).apply {
                    initialize(ECGenParameterSpec(spec.curve.jcaName()))
                }.generateKeyPair()
                is KeySpec.Rsa -> KeyPairGenerator.getInstance("RSA", session.provider).apply {
                    initialize(spec.bits)
                }.generateKeyPair()
                else -> throw IllegalArgumentException("Unsupported PKCS11 key specification: $spec")
            }
            val certificate = createPkcs11Certificate(alias, request.spec, keyPair, session.provider)
            session.keyStore.setKeyEntry(alias, keyPair.private, null, arrayOf(certificate))
            val storedData = Pkcs11StoredKeyData(options.copy(alias = null), alias)
            key(
                StoredKey.Managed(
                    version = StoredKey.CURRENT_VERSION,
                    id = request.id,
                    spec = request.spec,
                    usages = request.usages,
                    provider = this@Pkcs11KeyProvider.id,
                    providerSchemaVersion = PROVIDER_SCHEMA_VERSION,
                    providerData = storedData.encode(),
                    publicKey = EncodedKey.SpkiDer(BinaryData(certificate.publicKey.encoded)),
                    metadata = request.metadata,
                ),
                storedData,
            )
        }
    }

    override suspend fun restore(stored: StoredKey.Managed): ManagedKey {
        require(stored.provider == id) { "Stored key belongs to a different provider" }
        require(stored.providerSchemaVersion == PROVIDER_SCHEMA_VERSION) {
            "Unsupported PKCS11 provider schema: ${stored.providerSchemaVersion}"
        }
        validateUsages(stored.spec, stored.usages)
        val expectedPublicKey = stored.publicKey as? EncodedKey.SpkiDer
            ?: throw IllegalArgumentException("Stored PKCS11 key is missing its SPKI public key")
        val data = Pkcs11StoredKeyData.decode(stored.providerData)
        withSession(data.options) { session ->
            require(session.keyStore.isKeyEntry(data.alias)) { "PKCS11 alias does not exist: ${data.alias}" }
            require(session.keyStore.getCertificate(data.alias).publicKey.encoded.contentEquals(expectedPublicKey.data.toByteArray())) {
                "PKCS11 public key changed after restore"
            }
        }
        return key(stored, data)
    }

    private fun key(stored: StoredKey.Managed, data: Pkcs11StoredKeyData): ManagedKey = Pkcs11ManagedKey(stored, data)

    private inner class Pkcs11ManagedKey(
        override val storedKey: StoredKey.Managed,
        private val data: Pkcs11StoredKeyData,
    ) : ManagedKey {
        private val signatureAlgorithms = storedKey.spec.signatureAlgorithms()
        private val encryptionAlgorithms = storedKey.spec.encryptionAlgorithms()
        private val wrappingAlgorithms = storedKey.spec.wrappingAlgorithms()
        private val advertisedSignatureAlgorithms = signatureAlgorithms.takeIf {
            KeyUsage.SIGN in storedKey.usages || KeyUsage.VERIFY in storedKey.usages
        }.orEmpty()
        private val advertisedEncryptionAlgorithms = encryptionAlgorithms.takeIf {
            KeyUsage.ENCRYPT in storedKey.usages || KeyUsage.DECRYPT in storedKey.usages
        }.orEmpty()
        private val advertisedWrappingAlgorithms = wrappingAlgorithms.takeIf {
            KeyUsage.WRAP in storedKey.usages || KeyUsage.UNWRAP in storedKey.usages
        }.orEmpty()

        override val capabilities = KeyCapabilities(
            signer = KeyUsage.SIGN.takeIf(storedKey.usages::contains)?.let {
                Signer { message, algorithm -> sign(message, algorithm) }
            },
            verifier = KeyUsage.VERIFY.takeIf(storedKey.usages::contains)?.let {
                Verifier { message, signature, algorithm -> verify(message, signature, algorithm) }
            },
            encryptor = KeyUsage.ENCRYPT.takeIf(storedKey.usages::contains)?.let {
                Encryptor { plaintext, algorithm, associatedData -> encrypt(plaintext, algorithm, associatedData) }
            },
            decryptor = KeyUsage.DECRYPT.takeIf(storedKey.usages::contains)?.let {
                Decryptor { ciphertext, associatedData -> decrypt(ciphertext, associatedData) }
            },
            keyWrapper = KeyUsage.WRAP.takeIf(storedKey.usages::contains)?.let {
                KeyWrapper { material, algorithm -> wrap(material, algorithm) }
            },
            keyUnwrapper = KeyUsage.UNWRAP.takeIf(storedKey.usages::contains)?.let {
                KeyUnwrapper { wrapped -> unwrap(wrapped) }
            },
            deleter = KeyDeleter {
                withSession(data.options) { it.keyStore.deleteEntry(data.alias) }
                KeyDeletionResult.Deleted
            },
            publicKeyExporter = PublicKeyExporter { requireNotNull(storedKey.publicKey) },
            signatureAlgorithms = advertisedSignatureAlgorithms,
            encryptionAlgorithms = advertisedEncryptionAlgorithms,
            keyWrappingAlgorithms = advertisedWrappingAlgorithms,
            supportsSignatureAlgorithm = { it in advertisedSignatureAlgorithms },
            supportsEncryptionAlgorithm = { it in advertisedEncryptionAlgorithms },
            supportsKeyWrappingAlgorithm = { it in advertisedWrappingAlgorithms },
        )

        private suspend fun sign(message: ByteArray, algorithm: SignatureAlgorithm): ByteArray {
            require(algorithm in signatureAlgorithms) { "Unsupported PKCS11 signature algorithm" }
            val signature = withSession(data.options) { session ->
                algorithm.sign(session, session.privateKey(data.alias), message)
            }
            return if (algorithm is SignatureAlgorithm.Ecdsa && algorithm.encoding == EcdsaSignatureEncoding.IEEE_P1363) {
                EcdsaSignatureCodec.derToP1363(signature, storedKey.spec.ecComponentSize())
            } else signature
        }

        private suspend fun verify(message: ByteArray, signature: ByteArray, algorithm: SignatureAlgorithm): Boolean {
            require(algorithm in signatureAlgorithms) { "Unsupported PKCS11 signature algorithm" }
            val derSignature = if (algorithm is SignatureAlgorithm.Ecdsa && algorithm.encoding == EcdsaSignatureEncoding.IEEE_P1363) {
                EcdsaSignatureCodec.p1363ToDer(signature, storedKey.spec.ecComponentSize())
            } else signature
            return withSession(data.options) { session ->
                algorithm.verify(session, session.publicKey(data.alias), message, derSignature)
            }
        }

        private suspend fun encrypt(
            plaintext: ByteArray,
            algorithm: AsymmetricEncryptionAlgorithm,
            associatedData: ByteArray?,
        ): AsymmetricCiphertext {
            require(associatedData == null) { "PKCS11 RSA encryption does not support associated data" }
            require(algorithm in encryptionAlgorithms) { "Unsupported PKCS11 encryption algorithm" }
            val encrypted = withSession(data.options) { session ->
                algorithm.rsaCipher(session, Cipher.ENCRYPT_MODE, session.publicKey(data.alias)).doFinal(plaintext)
            }
            return AsymmetricCiphertext.Raw(algorithm, BinaryData(encrypted))
        }

        private suspend fun decrypt(ciphertext: AsymmetricCiphertext, associatedData: ByteArray?): ByteArray {
            require(associatedData == null) { "PKCS11 RSA decryption does not support associated data" }
            val raw = ciphertext as? AsymmetricCiphertext.Raw
                ?: throw IllegalArgumentException("PKCS11 expects raw RSA ciphertext")
            require(raw.algorithm in encryptionAlgorithms) { "Unsupported PKCS11 encryption algorithm" }
            return withSession(data.options) { session ->
                raw.algorithm.rsaCipher(session, Cipher.DECRYPT_MODE, session.privateKey(data.alias))
                    .doFinal(raw.data.toByteArray())
            }
        }

        private suspend fun wrap(material: EncodedKeyMaterial, algorithm: KeyWrappingAlgorithm): WrappedKey {
            require(algorithm in wrappingAlgorithms) { "Unsupported PKCS11 wrapping algorithm" }
            val blob = withSession(data.options) { session ->
                AsymmetricEncryptionAlgorithm.RsaPkcs1
                    .rsaCipher(session, Cipher.ENCRYPT_MODE, session.publicKey(data.alias))
                    .doFinal(material.key.data.toByteArray())
            }
            val metadata = Pkcs11WrappedData(
                encoding = material.key.encodingFormat,
                jwkPrivateMaterial = (material.key as? EncodedKey.Jwk)?.privateMaterial,
            ).encode()
            return WrappedKey.Opaque(
                algorithm = algorithm,
                blob = BinaryData(blob),
                wrappedKeySpec = material.spec,
                provider = this@Pkcs11KeyProvider.id,
                wrappingKeyId = storedKey.id,
                providerData = metadata,
            )
        }

        private suspend fun unwrap(wrapped: WrappedKey): EncodedKeyMaterial {
            val opaque = wrapped as? WrappedKey.Opaque
                ?: throw IllegalArgumentException("PKCS11 expects provider-opaque wrapped keys")
            require(opaque.provider == this@Pkcs11KeyProvider.id && opaque.wrappingKeyId == storedKey.id) {
                "Wrapped key belongs to a different provider or wrapping key"
            }
            require(opaque.algorithm in wrappingAlgorithms) { "Unsupported PKCS11 wrapping algorithm" }
            val bytes = withSession(data.options) { session ->
                AsymmetricEncryptionAlgorithm.RsaPkcs1
                    .rsaCipher(session, Cipher.DECRYPT_MODE, session.privateKey(data.alias))
                    .doFinal(opaque.blob.toByteArray())
            }
            val metadata = Pkcs11WrappedData.decode(opaque.providerData)
            val key = when (metadata.encoding) {
                KeyEncodingFormat.JWK -> EncodedKey.Jwk(
                    BinaryData(bytes),
                    privateMaterial = requireNotNull(metadata.jwkPrivateMaterial) {
                        "Wrapped JWK metadata is missing its private-material flag"
                    },
                )
                KeyEncodingFormat.SPKI_DER -> EncodedKey.SpkiDer(BinaryData(bytes))
                KeyEncodingFormat.PKCS8_DER -> EncodedKey.Pkcs8Der(BinaryData(bytes))
            }
            return EncodedKeyMaterial(opaque.wrappedKeySpec, key)
        }
    }

    private suspend fun <T> withSession(options: Pkcs11Options, block: suspend (Pkcs11Session) -> T): T {
        val token = TokenId(options.libraryPath, options.slotListIndex)
        return tokenMutexes.computeIfAbsent(token) { Mutex() }.withLock {
            sessions.open(options).use { block(it) }
        }
    }

    companion object {
        val ID = ProviderId("pkcs11-jca")
        private const val PROVIDER_SCHEMA_VERSION = 1
    }

    private data class TokenId(val libraryPath: String, val slotListIndex: Int)
}

@Serializable
private data class Pkcs11StoredKeyData(val options: Pkcs11Options, val alias: String) {
    fun encode(): BinaryData = BinaryData(json.encodeToString(this).encodeToByteArray())

    companion object {
        private val json = Json { encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = false }
        fun decode(data: BinaryData): Pkcs11StoredKeyData = json.decodeFromString(data.toByteArray().decodeToString())
    }
}

@Serializable
private data class Pkcs11WrappedData(
    val encoding: KeyEncodingFormat,
    val jwkPrivateMaterial: Boolean? = null,
) {
    fun encode(): BinaryData = BinaryData(json.encodeToString(this).encodeToByteArray())

    companion object {
        private val json = Json
        fun decode(data: BinaryData): Pkcs11WrappedData = json.decodeFromString(data.toByteArray().decodeToString())
    }
}

private fun validateUsages(spec: KeySpec, usages: Set<KeyUsage>) {
    require(usages.isNotEmpty()) { "PKCS11 key usages cannot be empty" }
    val allowed = if (spec is KeySpec.Rsa) {
        setOf(KeyUsage.SIGN, KeyUsage.VERIFY, KeyUsage.ENCRYPT, KeyUsage.DECRYPT, KeyUsage.WRAP, KeyUsage.UNWRAP)
    } else {
        setOf(KeyUsage.SIGN, KeyUsage.VERIFY)
    }
    require(usages.all(allowed::contains)) { "PKCS11 key usages are not supported by the key specification" }
}

private fun KeySpec.signatureAlgorithms(): Set<SignatureAlgorithm> = when (this) {
    is KeySpec.Ec -> {
        val digest = when (curve) {
            EcCurve.P256 -> DigestAlgorithm.SHA_256
            EcCurve.P384 -> DigestAlgorithm.SHA_384
            EcCurve.P521 -> DigestAlgorithm.SHA_512
            else -> throw IllegalArgumentException("Unsupported PKCS11 EC curve: ${curve.name}")
        }
        EcdsaSignatureEncoding.entries.mapTo(mutableSetOf()) { SignatureAlgorithm.Ecdsa(digest, it) }
    }
    is KeySpec.Rsa -> listOf(DigestAlgorithm.SHA_256, DigestAlgorithm.SHA_384, DigestAlgorithm.SHA_512)
        .flatMap { digest ->
            listOf(
                SignatureAlgorithm.RsaPkcs1(digest),
                SignatureAlgorithm.RsaPss(digest, saltLengthBytes = requireNotNull(digest.outputSizeBytes)),
            )
        }.toSet()
    else -> emptySet()
}

private fun KeySpec.encryptionAlgorithms(): Set<AsymmetricEncryptionAlgorithm> = when (this) {
    is KeySpec.Rsa -> setOf(AsymmetricEncryptionAlgorithm.RsaPkcs1)
    else -> emptySet()
}

private fun KeySpec.wrappingAlgorithms(): Set<KeyWrappingAlgorithm> =
    if (this is KeySpec.Rsa) setOf(Pkcs11WrappingAlgorithms.RSA_PKCS1) else emptySet()

private fun SignatureAlgorithm.jcaSignature(provider: Provider): Signature {
    val signature = when (this) {
        is SignatureAlgorithm.Ecdsa -> Signature.getInstance("${digest.jcaDigest()}withECDSA", provider)
        is SignatureAlgorithm.RsaPkcs1 -> Signature.getInstance("${digest.jcaDigest()}withRSA", provider)
        is SignatureAlgorithm.RsaPss -> Signature.getInstance("RSASSA-PSS", provider).apply {
            require(mgfDigest == digest && saltLengthBytes == digest.outputSizeBytes) {
                "PKCS11 RSA-PSS requires matching digest, MGF digest, and salt length"
            }
            setParameter(
                PSSParameterSpec(
                    digest.jcaName(),
                    "MGF1",
                    digest.mgfSpec(),
                    requireNotNull(saltLengthBytes),
                    1,
                )
            )
        }
        else -> throw IllegalArgumentException("Unsupported PKCS11 signature algorithm: $this")
    }
    return signature
}

private fun SignatureAlgorithm.sign(session: Pkcs11Session, key: PrivateKey, message: ByteArray): ByteArray =
    if (this is SignatureAlgorithm.Ecdsa) {
        val digest = MessageDigest.getInstance(digest.jcaName()).digest(message)
        Signature.getInstance("NONEwithECDSA", session.provider).apply {
            initSign(key)
            update(digest)
        }.sign()
    } else {
        jcaSignature(session.provider).apply {
            initSign(key)
            update(message)
        }.sign()
    }

private fun SignatureAlgorithm.verify(
    session: Pkcs11Session,
    key: PublicKey,
    message: ByteArray,
    signature: ByteArray,
): Boolean = if (this is SignatureAlgorithm.Ecdsa) {
    val digest = MessageDigest.getInstance(digest.jcaName()).digest(message)
    Signature.getInstance("NONEwithECDSA", session.provider).apply {
        initVerify(key)
        update(digest)
    }.verify(signature)
} else {
    jcaSignature(session.provider).apply {
        initVerify(key)
        update(message)
    }.verify(signature)
}

private fun AsymmetricEncryptionAlgorithm.rsaCipher(
    session: Pkcs11Session,
    mode: Int,
    key: Key,
): Cipher {
    require(this == AsymmetricEncryptionAlgorithm.RsaPkcs1) {
        "PKCS11 JCA provider supports explicit RSA PKCS#1 encryption"
    }
    return Cipher.getInstance("RSA/ECB/PKCS1Padding", session.provider).apply {
        init(mode, key)
    }
}

private fun Pkcs11Session.privateKey(alias: String): PrivateKey =
    keyStore.getKey(alias, null) as? PrivateKey ?: error("PKCS11 alias has no private key: $alias")

private fun Pkcs11Session.publicKey(alias: String): PublicKey =
    keyStore.getCertificate(alias)?.publicKey ?: error("PKCS11 alias has no public key: $alias")

private fun EcCurve.jcaName(): String = when (this) {
    EcCurve.P256 -> "secp256r1"
    EcCurve.P384 -> "secp384r1"
    EcCurve.P521 -> "secp521r1"
    else -> throw IllegalArgumentException("Unsupported PKCS11 EC curve: $name")
}

private fun KeySpec.ecComponentSize(): Int = when ((this as? KeySpec.Ec)?.curve) {
    EcCurve.P256 -> 32
    EcCurve.P384 -> 48
    EcCurve.P521 -> 66
    else -> throw IllegalArgumentException("PKCS11 ECDSA requires a supported EC key")
}

private fun DigestAlgorithm.jcaDigest(): String = when (this) {
    DigestAlgorithm.SHA_256 -> "SHA256"
    DigestAlgorithm.SHA_384 -> "SHA384"
    DigestAlgorithm.SHA_512 -> "SHA512"
    else -> throw IllegalArgumentException("Unsupported PKCS11 digest: $name")
}

private fun DigestAlgorithm.jcaName(): String = when (this) {
    DigestAlgorithm.SHA_1 -> "SHA-1"
    DigestAlgorithm.SHA_256 -> "SHA-256"
    DigestAlgorithm.SHA_384 -> "SHA-384"
    DigestAlgorithm.SHA_512 -> "SHA-512"
    else -> throw IllegalArgumentException("Unsupported PKCS11 digest: $name")
}

private fun DigestAlgorithm.mgfSpec(): MGF1ParameterSpec = when (this) {
    DigestAlgorithm.SHA_1 -> MGF1ParameterSpec.SHA1
    DigestAlgorithm.SHA_256 -> MGF1ParameterSpec.SHA256
    DigestAlgorithm.SHA_384 -> MGF1ParameterSpec.SHA384
    DigestAlgorithm.SHA_512 -> MGF1ParameterSpec.SHA512
    else -> throw IllegalArgumentException("Unsupported PKCS11 digest: $name")
}
