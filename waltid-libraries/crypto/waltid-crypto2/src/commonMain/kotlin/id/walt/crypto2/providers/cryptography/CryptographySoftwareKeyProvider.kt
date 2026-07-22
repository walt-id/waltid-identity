@file:OptIn(CryptographyProviderApi::class, DelicateCryptographyApi::class)

package id.walt.crypto2.providers.cryptography

import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.BinarySize.Companion.bytes
import dev.whyoleg.cryptography.CryptographyAlgorithmId
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.CryptographyProviderApi
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.Digest
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDH
import dev.whyoleg.cryptography.algorithms.ECDSA
import dev.whyoleg.cryptography.algorithms.EdDSA
import dev.whyoleg.cryptography.algorithms.MD5
import dev.whyoleg.cryptography.algorithms.RIPEMD160
import dev.whyoleg.cryptography.algorithms.RSA
import dev.whyoleg.cryptography.algorithms.SHA1
import dev.whyoleg.cryptography.algorithms.SHA224
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.algorithms.SHA384
import dev.whyoleg.cryptography.algorithms.SHA3_224
import dev.whyoleg.cryptography.algorithms.SHA3_256
import dev.whyoleg.cryptography.algorithms.SHA3_384
import dev.whyoleg.cryptography.algorithms.SHA3_512
import dev.whyoleg.cryptography.algorithms.SHA512
import dev.whyoleg.cryptography.algorithms.XDH
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.AsymmetricEncryptionAlgorithm
import id.walt.crypto2.algorithms.KeyAgreementAlgorithm
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EdwardsCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.AsymmetricCiphertext
import id.walt.crypto2.keys.KeyCapabilities
import id.walt.crypto2.keys.KeyEncodingFormat
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.JWK_ALGORITHM_METADATA_KEY
import id.walt.crypto2.keys.normalizeJwk
import id.walt.crypto2.keys.toPkcs8Der
import id.walt.crypto2.keys.toPrivateJwk
import id.walt.crypto2.keys.toPublicJwk
import id.walt.crypto2.keys.toSpkiDer
import id.walt.crypto2.keys.toStoredSoftwareKey
import id.walt.crypto2.keys.validatePrivatePublicConsistency
import id.walt.crypto2.keys.MontgomeryCurve
import id.walt.crypto2.keys.PrivateKeyExporter
import id.walt.crypto2.keys.ProviderId
import id.walt.crypto2.keys.PublicKeyExporter
import id.walt.crypto2.keys.publicOnly
import id.walt.crypto2.keys.requireCompatibleJwkAlgorithm
import id.walt.crypto2.keys.toCryptographyCurve
import id.walt.crypto2.keys.Decryptor
import id.walt.crypto2.keys.Encryptor
import id.walt.crypto2.keys.KeyAgreement
import id.walt.crypto2.keys.Signer
import id.walt.crypto2.keys.SoftwareKey
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.keys.Verifier
import id.walt.crypto2.providers.CryptoOperation
import id.walt.crypto2.providers.CryptoRequirement
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.SoftwareKeyProvider
import id.walt.crypto2.serialization.BinaryData
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class SignatureFamily {
    ECDSA,
    EDDSA,
    RSA_PKCS1,
    RSA_PSS,
}

data class CryptographyCapabilityProfile(
    val keySpecs: Set<KeySpec>,
    val digests: Set<DigestAlgorithm>,
    val signatureFamilies: Set<SignatureFamily>,
    val ecdsaEncodings: Set<EcdsaSignatureEncoding>,
    val encryptionAlgorithms: Set<AsymmetricEncryptionAlgorithm>,
    val keyAgreementAlgorithms: Set<KeyAgreementAlgorithm>,
    val keyGenerationFormats: Set<KeyEncodingFormat>,
    val keyImportFormats: Set<KeyEncodingFormat>,
    val publicKeyExportFormats: Set<KeyEncodingFormat>,
    val privateKeyExportFormats: Set<KeyEncodingFormat>,
    val privateJwkValidationSpecs: Set<KeySpec>,
) {
    init {
        require(KeyEncodingFormat.SPKI_DER !in keyGenerationFormats) {
            "Software key generation requires a private key format"
        }
        require(KeyEncodingFormat.PKCS8_DER !in publicKeyExportFormats) { "PKCS8 is not a public key format" }
        require(KeyEncodingFormat.SPKI_DER !in privateKeyExportFormats) { "SPKI is not a private key format" }
        require(privateJwkValidationSpecs.all(keySpecs::contains)) {
            "Private JWK validation specifications must be supported key specifications"
        }
    }

    companion object {
        private val portableKeySpecs = setOf(
            KeySpec.Ec(EcCurve.P256),
            KeySpec.Ec(EcCurve.P384),
            KeySpec.Ec(EcCurve.P521),
            KeySpec.Edwards(EdwardsCurve.ED25519),
            KeySpec.Montgomery(MontgomeryCurve.X25519),
            KeySpec.Rsa(2048),
            KeySpec.Rsa(3072),
            KeySpec.Rsa(4096),
        )

        val Portable = CryptographyCapabilityProfile(
            keySpecs = portableKeySpecs,
            digests = setOf(DigestAlgorithm.SHA_256, DigestAlgorithm.SHA_384, DigestAlgorithm.SHA_512),
            signatureFamilies = SignatureFamily.entries.toSet(),
            ecdsaEncodings = EcdsaSignatureEncoding.entries.toSet(),
            encryptionAlgorithms = setOf(
                AsymmetricEncryptionAlgorithm.RsaOaep(DigestAlgorithm.SHA_256),
                AsymmetricEncryptionAlgorithm.RsaOaep(DigestAlgorithm.SHA_384),
                AsymmetricEncryptionAlgorithm.RsaOaep(DigestAlgorithm.SHA_512),
            ),
            keyAgreementAlgorithms = setOf(KeyAgreementAlgorithm.Ecdh, KeyAgreementAlgorithm.Xdh),
            keyGenerationFormats = setOf(KeyEncodingFormat.JWK, KeyEncodingFormat.PKCS8_DER),
            keyImportFormats = KeyEncodingFormat.entries.toSet(),
            publicKeyExportFormats = setOf(KeyEncodingFormat.JWK, KeyEncodingFormat.SPKI_DER),
            privateKeyExportFormats = setOf(KeyEncodingFormat.JWK, KeyEncodingFormat.PKCS8_DER),
            privateJwkValidationSpecs = portableKeySpecs,
        ).withPlatformCapabilities()

        internal val Secp256k1Signatures = CryptographyCapabilityProfile(
            keySpecs = setOf(KeySpec.Ec(EcCurve.SECP256K1)),
            digests = setOf(DigestAlgorithm.SHA_256),
            signatureFamilies = setOf(SignatureFamily.ECDSA),
            ecdsaEncodings = EcdsaSignatureEncoding.entries.toSet(),
            encryptionAlgorithms = emptySet(),
            keyAgreementAlgorithms = emptySet(),
            keyGenerationFormats = setOf(KeyEncodingFormat.JWK, KeyEncodingFormat.PKCS8_DER),
            keyImportFormats = KeyEncodingFormat.entries.toSet(),
            publicKeyExportFormats = setOf(KeyEncodingFormat.JWK, KeyEncodingFormat.SPKI_DER),
            privateKeyExportFormats = setOf(KeyEncodingFormat.JWK, KeyEncodingFormat.PKCS8_DER),
            privateJwkValidationSpecs = setOf(KeySpec.Ec(EcCurve.SECP256K1)),
        ).withPlatformCapabilities()
    }
}

/** Software-key adapter around one explicitly described cryptography-kotlin provider. */
class CryptographySoftwareKeyProvider(
    private val provider: CryptographyProvider = CryptographyProvider.Default,
    override val id: ProviderId = ProviderId("cryptography-kotlin"),
    profile: CryptographyCapabilityProfile = CryptographyCapabilityProfile.Portable,
) : SoftwareKeyProvider {
    private val profile = profile.withPlatformCapabilities()

    override fun supports(requirement: CryptoRequirement): Boolean =
        requirement.spec in profile.keySpecs &&
            supportsEncoding(requirement) &&
            when (requirement.operation) {
                CryptoOperation.GENERATE_KEY ->
                    supportsUsages(requirement.spec, requirement.usages) &&
                        requirement.usages.any { it in privateUsages } &&
                        supportsGeneration(requirement.spec, requirement.usages)
                CryptoOperation.IMPORT_KEY ->
                    supportsUsages(requirement.spec, requirement.usages) &&
                        supportsImportedMaterial(requirement.spec, requirement.keyEncoding, requirement.usages) &&
                        supportsCapabilities(requirement.spec, requirement.usages)
                CryptoOperation.SIGN, CryptoOperation.VERIFY ->
                    requirement.signatureAlgorithm?.let { supportsSignature(requirement.spec, it) } == true &&
                        operationUsage(requirement.operation) in requirement.usages
                CryptoOperation.ENCRYPT, CryptoOperation.DECRYPT ->
                    requirement.encryptionAlgorithm?.let { supportsEncryption(requirement.spec, it) } == true &&
                        operationUsage(requirement.operation) in requirement.usages
                CryptoOperation.KEY_AGREEMENT ->
                    requirement.keyAgreementAlgorithm?.let { supportsAgreement(requirement.spec, it) } == true &&
                        KeyUsage.KEY_AGREEMENT in requirement.usages
                CryptoOperation.WRAP,
                CryptoOperation.UNWRAP,
                CryptoOperation.DELETE,
                -> false
                CryptoOperation.EXPORT_PUBLIC ->
                    supportsUsages(requirement.spec, requirement.usages) &&
                        supportsCapabilities(requirement.spec, requirement.usages)
                CryptoOperation.EXPORT_PRIVATE ->
                    supportsUsages(requirement.spec, requirement.usages) &&
                        requirement.usages.any { it in privateUsages } &&
                        supportsCapabilities(requirement.spec, requirement.usages)
            }

    override suspend fun generate(request: GenerateSoftwareKeyRequest): SoftwareKey {
        request.spec.requireCompatibleJwkAlgorithm(request.metadata[JWK_ALGORITHM_METADATA_KEY])
        require(
            supports(
                CryptoRequirement(
                    operation = CryptoOperation.GENERATE_KEY,
                    spec = request.spec,
                    usages = request.usages,
                    keyEncoding = request.keyEncoding,
                ),
            ),
        ) { "Unsupported software-key generation request" }
        val privateMaterial = request.usages.any { it in privateUsages }
        val generated = EncodedKey.Jwk(
            BinaryData(generateJwk(request.spec, request.usages, privateMaterial)),
            privateMaterial,
        )
        val material = when (request.keyEncoding) {
            KeyEncodingFormat.JWK -> generated
            KeyEncodingFormat.PKCS8_DER -> generated.toPkcs8Der(request.spec, provider)
            KeyEncodingFormat.SPKI_DER -> error("Generated software keys require private material")
        }
        return CryptographySoftwareKey(
            provider = provider,
            profile = profile,
            storedKey = StoredKey.Software(
                version = StoredKey.CURRENT_VERSION,
                id = request.id,
                spec = request.spec,
                usages = request.usages,
                material = material,
                metadata = request.metadata,
            ),
        )
    }

    override suspend fun restore(stored: StoredKey.Software): SoftwareKey {
        require(
            supports(
                CryptoRequirement(
                    operation = CryptoOperation.IMPORT_KEY,
                    spec = stored.spec,
                    usages = stored.usages,
                    keyEncoding = stored.material.encodingFormat,
                ),
            ),
        ) { "Unsupported stored software key" }
        val privateMaterial = stored.material.hasPrivateMaterial()
        val needsPrivateMaterial = stored.usages.any { it in privateUsages }
        require(privateMaterial == needsPrivateMaterial) {
            if (privateMaterial) "Private material requires a private key usage"
            else "Private key usages require private material"
        }
        val validationJwk = when (stored.material) {
            is EncodedKey.Jwk -> stored.material
            is EncodedKey.SpkiDer -> stored.material.toPublicJwk(stored.spec, provider)
            is EncodedKey.Pkcs8Der -> stored.material.toPrivateJwk(stored.spec, provider)
        }
        val validated = validationJwk.toStoredSoftwareKey(stored.id, stored.usages, stored.metadata)
        require(validated.spec == stored.spec) { "Stored key specification does not match its material" }
        validationJwk.validatePrivatePublicConsistency(stored.spec, provider)
        val normalized = when (val material = stored.material) {
            is EncodedKey.Jwk -> EncodedKey.Jwk(
                BinaryData(normalizeJwk(material.data.toByteArray())),
                material.privateMaterial,
            )
            else -> material
        }
        return CryptographySoftwareKey(provider, profile, stored.copy(material = normalized, metadata = validated.metadata))
    }

    private fun supportsEncoding(requirement: CryptoRequirement): Boolean = when (requirement.operation) {
        CryptoOperation.GENERATE_KEY -> requirement.keyEncoding in profile.keyGenerationFormats
        CryptoOperation.IMPORT_KEY -> requirement.keyEncoding in profile.keyImportFormats
        CryptoOperation.EXPORT_PUBLIC ->
            (requirement.keyEncoding ?: KeyEncodingFormat.JWK) in profile.publicKeyExportFormats
        CryptoOperation.EXPORT_PRIVATE ->
            (requirement.keyEncoding ?: KeyEncodingFormat.JWK) in profile.privateKeyExportFormats
        else -> true
    }

    private fun supportsImportedMaterial(
        spec: KeySpec,
        format: KeyEncodingFormat?,
        usages: Set<KeyUsage>,
    ): Boolean = when (format) {
        KeyEncodingFormat.SPKI_DER -> usages.none { it in privateUsages }
        KeyEncodingFormat.PKCS8_DER -> usages.any { it in privateUsages } && spec in profile.privateJwkValidationSpecs
        KeyEncodingFormat.JWK -> usages.none { it in privateUsages } || spec in profile.privateJwkValidationSpecs
        null -> false
    }

    private fun supportsUsages(spec: KeySpec, usages: Set<KeyUsage>): Boolean {
        if (usages.isEmpty()) return false
        val allowed = when (spec) {
            is KeySpec.Ec -> setOf(KeyUsage.SIGN, KeyUsage.VERIFY, KeyUsage.KEY_AGREEMENT)
            is KeySpec.Edwards -> setOf(KeyUsage.SIGN, KeyUsage.VERIFY)
            is KeySpec.Montgomery -> setOf(KeyUsage.KEY_AGREEMENT)
            is KeySpec.Rsa -> setOf(KeyUsage.SIGN, KeyUsage.VERIFY, KeyUsage.ENCRYPT, KeyUsage.DECRYPT)
            is KeySpec.Symmetric, is KeySpec.Custom -> emptySet()
        }
        return usages.all { it in allowed }
    }

    internal fun supportsSignature(spec: KeySpec, algorithm: SignatureAlgorithm): Boolean = when (algorithm) {
        is SignatureAlgorithm.Ecdsa ->
            spec is KeySpec.Ec &&
                SignatureFamily.ECDSA in profile.signatureFamilies &&
                algorithm.digest in knownCryptographyDigests &&
                algorithm.digest in profile.digests &&
                algorithm.encoding in profile.ecdsaEncodings &&
                provider.getOrNull(ECDSA) != null
        SignatureAlgorithm.EdDsa ->
            spec is KeySpec.Edwards && spec.curve in supportedEdwardsCurves &&
                SignatureFamily.EDDSA in profile.signatureFamilies &&
                provider.getOrNull(EdDSA) != null
        is SignatureAlgorithm.RsaPkcs1 ->
            spec is KeySpec.Rsa &&
                SignatureFamily.RSA_PKCS1 in profile.signatureFamilies &&
                algorithm.digest in knownCryptographyDigests &&
                algorithm.digest in profile.digests &&
                provider.getOrNull(RSA.PKCS1) != null
        is SignatureAlgorithm.RsaPss ->
            spec is KeySpec.Rsa &&
                SignatureFamily.RSA_PSS in profile.signatureFamilies &&
                algorithm.digest in knownCryptographyDigests &&
                algorithm.digest in profile.digests &&
                algorithm.mgfDigest == algorithm.digest &&
                algorithm.saltLengthBytes?.let { it <= maxPssSaltLength(spec.bits, algorithm.digest) } != false &&
                provider.getOrNull(RSA.PSS) != null
        is SignatureAlgorithm.Custom -> false
    }

    internal fun supportsEncryption(spec: KeySpec, algorithm: AsymmetricEncryptionAlgorithm): Boolean =
        spec is KeySpec.Rsa &&
            algorithm in profile.encryptionAlgorithms &&
            algorithm is AsymmetricEncryptionAlgorithm.RsaOaep &&
            algorithm.digest in knownCryptographyDigests &&
            algorithm.mgfDigest == algorithm.digest &&
            provider.getOrNull(RSA.OAEP) != null

    internal fun supportsAgreement(spec: KeySpec, algorithm: KeyAgreementAlgorithm): Boolean = when (algorithm) {
        KeyAgreementAlgorithm.Ecdh -> spec is KeySpec.Ec &&
            algorithm in profile.keyAgreementAlgorithms && provider.getOrNull(ECDH) != null
        KeyAgreementAlgorithm.Xdh -> spec is KeySpec.Montgomery &&
            spec.curve in supportedMontgomeryCurves &&
            algorithm in profile.keyAgreementAlgorithms && provider.getOrNull(XDH) != null
        is KeyAgreementAlgorithm.Named -> false
        is KeyAgreementAlgorithm.Custom -> false
    }

    private suspend fun generateJwk(
        spec: KeySpec,
        usages: Set<KeyUsage>,
        includePrivate: Boolean,
    ): ByteArray = when (spec) {
        is KeySpec.Ec -> generateEcJwk(spec, usages, includePrivate)
        is KeySpec.Edwards -> provider.get(EdDSA).keyPairGenerator(spec.curve.toCryptographyCurve()).generateKey().let { pair ->
            if (includePrivate) pair.privateKey.encodeToByteArray(EdDSA.PrivateKey.Format.JWK)
            else pair.publicKey.encodeToByteArray(EdDSA.PublicKey.Format.JWK)
        }
        is KeySpec.Montgomery -> provider.get(XDH).keyPairGenerator(spec.curve.toCryptographyCurve()).generateKey().let { pair ->
            if (includePrivate) pair.privateKey.encodeToByteArray(XDH.PrivateKey.Format.JWK)
            else pair.publicKey.encodeToByteArray(XDH.PublicKey.Format.JWK)
        }
        is KeySpec.Rsa -> generateRsaJwk(spec, usages, includePrivate)
        is KeySpec.Symmetric, is KeySpec.Custom -> error("Unsupported software-key specification")
    }.let(::normalizeJwk)

    private suspend fun generateEcJwk(
        spec: KeySpec.Ec,
        usages: Set<KeyUsage>,
        includePrivate: Boolean,
    ): ByteArray {
        val curve = EC.Curve(spec.curve.name)
        return if (KeyUsage.SIGN in usages || KeyUsage.VERIFY in usages) {
            provider.get(ECDSA).keyPairGenerator(curve).generateKey().let { pair ->
                if (includePrivate) pair.privateKey.encodeToByteArray(EC.PrivateKey.Format.JWK)
                else pair.publicKey.encodeToByteArray(EC.PublicKey.Format.JWK)
            }
        } else {
            provider.get(ECDH).keyPairGenerator(curve).generateKey().let { pair ->
                if (includePrivate) pair.privateKey.encodeToByteArray(EC.PrivateKey.Format.JWK)
                else pair.publicKey.encodeToByteArray(EC.PublicKey.Format.JWK)
            }
        }
    }

    private suspend fun generateRsaJwk(
        spec: KeySpec.Rsa,
        usages: Set<KeyUsage>,
        includePrivate: Boolean,
    ): ByteArray {
        val keySize = spec.bits.bits
        return when {
            (KeyUsage.SIGN in usages || KeyUsage.VERIFY in usages) &&
                provider.getOrNull(RSA.PKCS1) != null -> provider.get(RSA.PKCS1)
                .keyPairGenerator(keySize, SHA256).generateKey().let { pair ->
                    if (includePrivate) pair.privateKey.encodeToByteArray(RSA.PrivateKey.Format.JWK)
                    else pair.publicKey.encodeToByteArray(RSA.PublicKey.Format.JWK)
                }
            (KeyUsage.SIGN in usages || KeyUsage.VERIFY in usages) &&
                provider.getOrNull(RSA.PSS) != null -> provider.get(RSA.PSS)
                .keyPairGenerator(keySize, SHA256).generateKey().let { pair ->
                    if (includePrivate) pair.privateKey.encodeToByteArray(RSA.PrivateKey.Format.JWK)
                    else pair.publicKey.encodeToByteArray(RSA.PublicKey.Format.JWK)
                }
            else -> provider.get(RSA.OAEP).keyPairGenerator(keySize, SHA256).generateKey().let { pair ->
                if (includePrivate) pair.privateKey.encodeToByteArray(RSA.PrivateKey.Format.JWK)
                else pair.publicKey.encodeToByteArray(RSA.PublicKey.Format.JWK)
            }
        }
    }

    private fun supportsGeneration(spec: KeySpec, usages: Set<KeyUsage>): Boolean = supportsCapabilities(spec, usages)

    private fun supportsCapabilities(spec: KeySpec, usages: Set<KeyUsage>): Boolean = when (spec) {
        is KeySpec.Ec ->
            (KeyUsage.SIGN !in usages && KeyUsage.VERIFY !in usages ||
                SignatureFamily.ECDSA in profile.signatureFamilies &&
                profile.digests.any(knownCryptographyDigests::contains) && profile.ecdsaEncodings.isNotEmpty() &&
                provider.getOrNull(ECDSA) != null) &&
                (KeyUsage.KEY_AGREEMENT !in usages ||
                    KeyAgreementAlgorithm.Ecdh in profile.keyAgreementAlgorithms && provider.getOrNull(ECDH) != null)
        is KeySpec.Edwards -> spec.curve in supportedEdwardsCurves &&
            SignatureFamily.EDDSA in profile.signatureFamilies && provider.getOrNull(EdDSA) != null
        is KeySpec.Montgomery ->
            spec.curve in supportedMontgomeryCurves &&
                KeyAgreementAlgorithm.Xdh in profile.keyAgreementAlgorithms && provider.getOrNull(XDH) != null
        is KeySpec.Rsa ->
            (KeyUsage.SIGN !in usages && KeyUsage.VERIFY !in usages ||
                profile.digests.any(knownCryptographyDigests::contains) &&
                (SignatureFamily.RSA_PKCS1 in profile.signatureFamilies && provider.getOrNull(RSA.PKCS1) != null ||
                    SignatureFamily.RSA_PSS in profile.signatureFamilies && provider.getOrNull(RSA.PSS) != null)) &&
                (KeyUsage.ENCRYPT !in usages && KeyUsage.DECRYPT !in usages ||
                    profile.encryptionAlgorithms.any { supportsEncryption(spec, it) })
        is KeySpec.Symmetric, is KeySpec.Custom -> false
    }

    companion object {
        private val privateUsages = setOf(
            KeyUsage.SIGN,
            KeyUsage.DECRYPT,
            KeyUsage.KEY_AGREEMENT,
            KeyUsage.UNWRAP,
        )
        private val knownCryptographyDigests = setOf(
            DigestAlgorithm.MD5,
            DigestAlgorithm.SHA_1,
            DigestAlgorithm.SHA_224,
            DigestAlgorithm.SHA_256,
            DigestAlgorithm.SHA_384,
            DigestAlgorithm.SHA_512,
            DigestAlgorithm.SHA3_224,
            DigestAlgorithm.SHA3_256,
            DigestAlgorithm.SHA3_384,
            DigestAlgorithm.SHA3_512,
            DigestAlgorithm.RIPEMD_160,
        )
        private val supportedEdwardsCurves = setOf(EdwardsCurve.ED25519, EdwardsCurve.ED448)
        private val supportedMontgomeryCurves = setOf(MontgomeryCurve.X25519, MontgomeryCurve.X448)
    }

}

private class CryptographySoftwareKey(
    private val provider: CryptographyProvider,
    private val profile: CryptographyCapabilityProfile,
    override val storedKey: StoredKey.Software,
) : SoftwareKey {
    private val materialMutex = Mutex()
    private val signatureMaterials = mutableMapOf<SignatureAlgorithm, SignatureMaterial>()
    private val encryptionMaterials = mutableMapOf<AsymmetricEncryptionAlgorithm, RsaOaepMaterial>()
    private val agreementMaterials = mutableMapOf<KeyAgreementAlgorithm, AgreementMaterial>()
    private val adapter = CryptographySoftwareKeyProvider(provider, profile = profile)

    override val capabilities: KeyCapabilities = KeyCapabilities(
        signer = KeyUsage.SIGN.takeIf(usages::contains)?.let {
            Signer { data, algorithm ->
                require(adapter.supportsSignature(spec, algorithm)) { "Unsupported signature algorithm for key" }
                signatureMaterial(algorithm).sign(data)
            }
        },
        verifier = KeyUsage.VERIFY.takeIf(usages::contains)?.let {
            Verifier { data, signature, algorithm ->
                require(adapter.supportsSignature(spec, algorithm)) { "Unsupported signature algorithm for key" }
                signatureMaterial(algorithm).verify(data, signature)
            }
        },
        encryptor = KeyUsage.ENCRYPT.takeIf(usages::contains)?.let {
            Encryptor { plaintext, algorithm, label ->
                require(adapter.supportsEncryption(spec, algorithm)) { "Unsupported encryption algorithm for key" }
                AsymmetricCiphertext.Raw(
                    algorithm = algorithm,
                    data = BinaryData(encryptionMaterial(algorithm).publicKey.encryptor().encrypt(plaintext, label)),
                )
            }
        },
        decryptor = KeyUsage.DECRYPT.takeIf(usages::contains)?.let {
            Decryptor { ciphertext, label ->
                val algorithm = ciphertext.algorithm
                require(adapter.supportsEncryption(spec, algorithm)) { "Unsupported encryption algorithm for key" }
                val raw = ciphertext as? AsymmetricCiphertext.Raw
                    ?: throw IllegalArgumentException("Software keys cannot decrypt provider-opaque ciphertext")
                requireNotNull(encryptionMaterial(algorithm).privateKey).decryptor()
                    .decrypt(raw.data.toByteArray(), label)
            }
        },
        keyAgreement = KeyUsage.KEY_AGREEMENT.takeIf(usages::contains)?.let {
            KeyAgreement { peerPublicKey, algorithm ->
                require(adapter.supportsAgreement(spec, algorithm)) { "Unsupported agreement algorithm for key" }
                BinaryData(agreementMaterial(algorithm).generateSharedSecret(provider, spec, peerPublicKey))
            }
        },
        publicKeyExporter = profile.publicKeyExportFormats.takeIf { it.isNotEmpty() }?.let {
            object : PublicKeyExporter {
                override suspend fun exportPublicKey(): EncodedKey = exportPublicKey(KeyEncodingFormat.JWK)

                override suspend fun exportPublicKey(format: KeyEncodingFormat): EncodedKey {
                    require(format in profile.publicKeyExportFormats) {
                        "Public key export format is not supported: $format"
                    }
                    return when (format) {
                        KeyEncodingFormat.JWK -> storedKey.material.toPublicJwk(spec, provider)
                            .withStoredAlgorithm(storedKey.metadata)
                        KeyEncodingFormat.SPKI_DER -> storedKey.material.toSpkiDer(spec, provider)
                        KeyEncodingFormat.PKCS8_DER -> error("PKCS8 is a private key format")
                    }
                }
            }
        },
        privateKeyExporter = storedKey.material.hasPrivateMaterial()
            .takeIf { it && profile.privateKeyExportFormats.isNotEmpty() }?.let {
            object : PrivateKeyExporter {
                override suspend fun exportPrivateKey(): EncodedKey = exportPrivateKey(KeyEncodingFormat.JWK)

                override suspend fun exportPrivateKey(format: KeyEncodingFormat): EncodedKey {
                    require(format in profile.privateKeyExportFormats) {
                        "Private key export format is not supported: $format"
                    }
                    return when (format) {
                        KeyEncodingFormat.JWK -> storedKey.material.toPrivateJwk(spec, provider)
                            .withStoredAlgorithm(storedKey.metadata)
                        KeyEncodingFormat.PKCS8_DER -> storedKey.material.toPkcs8Der(spec, provider)
                        KeyEncodingFormat.SPKI_DER -> error("SPKI is a public key format")
                    }
                }
            }
        },
        signatureAlgorithms = advertisedSignatureAlgorithms().takeIf {
            KeyUsage.SIGN in usages || KeyUsage.VERIFY in usages
        }.orEmpty(),
        encryptionAlgorithms = advertisedEncryptionAlgorithms().takeIf {
            KeyUsage.ENCRYPT in usages || KeyUsage.DECRYPT in usages
        }.orEmpty(),
        keyAgreementAlgorithms = advertisedAgreementAlgorithms().takeIf {
            KeyUsage.KEY_AGREEMENT in usages
        }.orEmpty(),
        supportsSignatureAlgorithm = { algorithm ->
            (KeyUsage.SIGN in usages || KeyUsage.VERIFY in usages) && adapter.supportsSignature(spec, algorithm)
        },
        supportsEncryptionAlgorithm = { algorithm ->
            (KeyUsage.ENCRYPT in usages || KeyUsage.DECRYPT in usages) && adapter.supportsEncryption(spec, algorithm)
        },
        supportsKeyAgreementAlgorithm = { algorithm ->
            KeyUsage.KEY_AGREEMENT in usages && adapter.supportsAgreement(spec, algorithm)
        },
    )

    private suspend fun signatureMaterial(algorithm: SignatureAlgorithm): SignatureMaterial {
        materialMutex.lock()
        return try {
            signatureMaterials[algorithm] ?: decodeSignatureMaterial(provider, storedKey, algorithm)
                .also { signatureMaterials[algorithm] = it }
        } finally {
            materialMutex.unlock()
        }
    }

    private suspend fun encryptionMaterial(algorithm: AsymmetricEncryptionAlgorithm): RsaOaepMaterial {
        materialMutex.lock()
        return try {
            encryptionMaterials[algorithm] ?: decodeEncryptionMaterial(provider, storedKey, algorithm)
                .also { encryptionMaterials[algorithm] = it }
        } finally {
            materialMutex.unlock()
        }
    }

    private suspend fun agreementMaterial(algorithm: KeyAgreementAlgorithm): AgreementMaterial {
        materialMutex.lock()
        return try {
            agreementMaterials[algorithm] ?: decodeAgreementMaterial(provider, storedKey, algorithm)
                .also { agreementMaterials[algorithm] = it }
        } finally {
            materialMutex.unlock()
        }
    }

    private fun advertisedSignatureAlgorithms(): Set<SignatureAlgorithm> {
        if (KeyUsage.SIGN !in usages && KeyUsage.VERIFY !in usages) return emptySet()
        return when (spec) {
            is KeySpec.Ec -> profile.digests.flatMap { digest ->
                profile.ecdsaEncodings.map { encoding -> SignatureAlgorithm.Ecdsa(digest, encoding) }
            }.filterTo(mutableSetOf()) { adapter.supportsSignature(spec, it) }
            is KeySpec.Edwards -> setOf(SignatureAlgorithm.EdDsa).filterTo(mutableSetOf()) {
                adapter.supportsSignature(spec, it)
            }
            is KeySpec.Rsa -> profile.digests.flatMap { digest ->
                listOf(
                    SignatureAlgorithm.RsaPkcs1(digest),
                    SignatureAlgorithm.RsaPss(digest),
                    SignatureAlgorithm.RsaPss(digest, saltLengthBytes = digest.sizeBytes),
                )
            }.filterTo(mutableSetOf()) { adapter.supportsSignature(spec, it) }
            else -> emptySet()
        }
    }

    private fun advertisedEncryptionAlgorithms(): Set<AsymmetricEncryptionAlgorithm> =
        profile.encryptionAlgorithms.filterTo(mutableSetOf()) { adapter.supportsEncryption(spec, it) }

    private fun advertisedAgreementAlgorithms(): Set<KeyAgreementAlgorithm> =
        profile.keyAgreementAlgorithms.filterTo(mutableSetOf()) { adapter.supportsAgreement(spec, it) }
}

private sealed interface SignatureMaterial {
    suspend fun sign(data: ByteArray): ByteArray
    suspend fun verify(data: ByteArray, signature: ByteArray): Boolean

    data class Ec(
        val publicKey: ECDSA.PublicKey,
        val privateKey: ECDSA.PrivateKey?,
        val digest: CryptographyAlgorithmId<Digest>,
        val format: ECDSA.SignatureFormat,
    ) : SignatureMaterial {
        override suspend fun sign(data: ByteArray): ByteArray = requireNotNull(privateKey)
            .signatureGenerator(digest, format).generateSignature(data)
        override suspend fun verify(data: ByteArray, signature: ByteArray): Boolean =
            publicKey.signatureVerifier(digest, format).tryVerifySignature(data, signature)
    }

    data class Ed(
        val publicKey: EdDSA.PublicKey,
        val privateKey: EdDSA.PrivateKey?,
    ) : SignatureMaterial {
        override suspend fun sign(data: ByteArray): ByteArray = requireNotNull(privateKey)
            .signatureGenerator().generateSignature(data)
        override suspend fun verify(data: ByteArray, signature: ByteArray): Boolean =
            publicKey.signatureVerifier().tryVerifySignature(data, signature)
    }

    data class RsaPkcs1(
        val publicKey: RSA.PKCS1.PublicKey,
        val privateKey: RSA.PKCS1.PrivateKey?,
    ) : SignatureMaterial {
        override suspend fun sign(data: ByteArray): ByteArray = requireNotNull(privateKey)
            .signatureGenerator().generateSignature(data)
        override suspend fun verify(data: ByteArray, signature: ByteArray): Boolean =
            publicKey.signatureVerifier().tryVerifySignature(data, signature)
    }

    data class RsaPss(
        val publicKey: RSA.PSS.PublicKey,
        val privateKey: RSA.PSS.PrivateKey?,
        val saltLengthBytes: Int?,
    ) : SignatureMaterial {
        override suspend fun sign(data: ByteArray): ByteArray = requireNotNull(privateKey).let { key ->
            saltLengthBytes?.let { key.signatureGenerator(it.bytes) } ?: key.signatureGenerator()
        }.generateSignature(data)
        override suspend fun verify(data: ByteArray, signature: ByteArray): Boolean =
            (saltLengthBytes?.let { publicKey.signatureVerifier(it.bytes) } ?: publicKey.signatureVerifier())
                .tryVerifySignature(data, signature)
    }
}

private data class RsaOaepMaterial(
    val publicKey: RSA.OAEP.PublicKey,
    val privateKey: RSA.OAEP.PrivateKey?,
)

private sealed interface AgreementMaterial {
    suspend fun generateSharedSecret(
        provider: CryptographyProvider,
        spec: KeySpec,
        peerPublicKey: EncodedKey,
    ): ByteArray

    data class Ecdh(val privateKey: ECDH.PrivateKey, val curve: EC.Curve) : AgreementMaterial {
        override suspend fun generateSharedSecret(
            provider: CryptographyProvider,
            spec: KeySpec,
            peerPublicKey: EncodedKey,
        ): ByteArray {
            val jwk = peerPublicKey.toPublicJwk(spec, provider)
            val peer = provider.get(ECDH).publicKeyDecoder(curve)
                .decodeFromByteArray(EC.PublicKey.Format.JWK, jwk.data.toByteArray())
            return privateKey.sharedSecretGenerator().generateSharedSecretToByteArray(peer)
        }
    }

    data class Xdh(val privateKey: XDH.PrivateKey, val curve: XDH.Curve) : AgreementMaterial {
        override suspend fun generateSharedSecret(
            provider: CryptographyProvider,
            spec: KeySpec,
            peerPublicKey: EncodedKey,
        ): ByteArray {
            val jwk = peerPublicKey.toPublicJwk(spec, provider)
            val peer = provider.get(XDH).publicKeyDecoder(curve)
                .decodeFromByteArray(XDH.PublicKey.Format.JWK, jwk.data.toByteArray())
            return privateKey.sharedSecretGenerator().generateSharedSecretToByteArray(peer)
        }
    }
}

private suspend fun decodeSignatureMaterial(
    provider: CryptographyProvider,
    stored: StoredKey.Software,
    algorithm: SignatureAlgorithm,
): SignatureMaterial {
    val jwk = stored.operationJwk(provider)
    val bytes = jwk.data.toByteArray()
    return when (algorithm) {
        is SignatureAlgorithm.Ecdsa -> {
            val curve = EC.Curve((stored.spec as KeySpec.Ec).curve.name)
            val ecdsa = provider.get(ECDSA)
            val privateKey = bytes.takeIf { jwk.privateMaterial }?.let {
                ecdsa.privateKeyDecoder(curve).decodeFromByteArray(EC.PrivateKey.Format.JWK, it)
            }
            SignatureMaterial.Ec(
                privateKey?.getPublicKey()
                    ?: ecdsa.publicKeyDecoder(curve).decodeFromByteArray(EC.PublicKey.Format.JWK, bytes),
                privateKey,
                algorithm.digest.toCryptographyDigest(),
                algorithm.encoding.toCryptographyFormat(),
            )
        }
        SignatureAlgorithm.EdDsa -> {
            val curve = (stored.spec as KeySpec.Edwards).curve.toCryptographyCurve()
            val edDsa = provider.get(EdDSA)
            val privateKey = bytes.takeIf { jwk.privateMaterial }?.let {
                edDsa.privateKeyDecoder(curve).decodeFromByteArray(EdDSA.PrivateKey.Format.JWK, it)
            }
            SignatureMaterial.Ed(
                privateKey?.getPublicKey()
                    ?: edDsa.publicKeyDecoder(curve).decodeFromByteArray(EdDSA.PublicKey.Format.JWK, bytes),
                privateKey,
            )
        }
        is SignatureAlgorithm.RsaPkcs1 -> decodeRsaPkcs1(provider, bytes, jwk.privateMaterial, algorithm)
        is SignatureAlgorithm.RsaPss -> decodeRsaPss(provider, bytes, jwk.privateMaterial, algorithm)
        is SignatureAlgorithm.Custom -> error("Custom signature algorithm requires a custom provider")
    }
}

private suspend fun decodeRsaPkcs1(
    provider: CryptographyProvider,
    bytes: ByteArray,
    privateMaterial: Boolean,
    algorithm: SignatureAlgorithm.RsaPkcs1,
): SignatureMaterial.RsaPkcs1 {
    val rsa = provider.get(RSA.PKCS1)
    val digest = algorithm.digest.toCryptographyDigest()
    val privateKey = bytes.takeIf { privateMaterial }?.let {
        rsa.privateKeyDecoder(digest).decodeFromByteArray(RSA.PrivateKey.Format.JWK, it)
    }
    return SignatureMaterial.RsaPkcs1(
        privateKey?.getPublicKey()
            ?: rsa.publicKeyDecoder(digest).decodeFromByteArray(RSA.PublicKey.Format.JWK, bytes),
        privateKey,
    )
}

private suspend fun decodeRsaPss(
    provider: CryptographyProvider,
    bytes: ByteArray,
    privateMaterial: Boolean,
    algorithm: SignatureAlgorithm.RsaPss,
): SignatureMaterial.RsaPss {
    val rsa = provider.get(RSA.PSS)
    val digest = algorithm.digest.toCryptographyDigest()
    val privateKey = bytes.takeIf { privateMaterial }?.let {
        rsa.privateKeyDecoder(digest).decodeFromByteArray(RSA.PrivateKey.Format.JWK, it)
    }
    return SignatureMaterial.RsaPss(
        privateKey?.getPublicKey()
            ?: rsa.publicKeyDecoder(digest).decodeFromByteArray(RSA.PublicKey.Format.JWK, bytes),
        privateKey,
        algorithm.saltLengthBytes,
    )
}

private suspend fun decodeEncryptionMaterial(
    provider: CryptographyProvider,
    stored: StoredKey.Software,
    algorithm: AsymmetricEncryptionAlgorithm,
): RsaOaepMaterial {
    val rsaOaep = algorithm as AsymmetricEncryptionAlgorithm.RsaOaep
    val rsa = provider.get(RSA.OAEP)
    val digest = rsaOaep.digest.toCryptographyDigest()
    val jwk = stored.operationJwk(provider)
    val bytes = jwk.data.toByteArray()
    val privateKey = bytes.takeIf { jwk.privateMaterial }?.let {
        rsa.privateKeyDecoder(digest).decodeFromByteArray(RSA.PrivateKey.Format.JWK, it)
    }
    return RsaOaepMaterial(
        privateKey?.getPublicKey()
            ?: rsa.publicKeyDecoder(digest).decodeFromByteArray(RSA.PublicKey.Format.JWK, bytes),
        privateKey,
    )
}

private suspend fun decodeAgreementMaterial(
    provider: CryptographyProvider,
    stored: StoredKey.Software,
    algorithm: KeyAgreementAlgorithm,
): AgreementMaterial {
    val jwk = stored.operationJwk(provider)
    require(jwk.privateMaterial) { "Key agreement requires private material" }
    val bytes = jwk.data.toByteArray()
    return when (algorithm) {
        KeyAgreementAlgorithm.Ecdh -> {
            val curve = EC.Curve((stored.spec as KeySpec.Ec).curve.name)
            val key = provider.get(ECDH).privateKeyDecoder(curve)
                .decodeFromByteArray(EC.PrivateKey.Format.JWK, bytes)
            AgreementMaterial.Ecdh(key, curve)
        }
        KeyAgreementAlgorithm.Xdh -> {
            val curve = (stored.spec as KeySpec.Montgomery).curve.toCryptographyCurve()
            val key = provider.get(XDH).privateKeyDecoder(curve)
                .decodeFromByteArray(XDH.PrivateKey.Format.JWK, bytes)
            AgreementMaterial.Xdh(key, curve)
        }
        is KeyAgreementAlgorithm.Custom -> error("Custom agreement algorithm requires a custom provider")
        is KeyAgreementAlgorithm.Named -> error("Named agreement algorithm requires another provider")
    }
}

internal fun DigestAlgorithm.toCryptographyDigest(): CryptographyAlgorithmId<Digest> = when (this) {
    DigestAlgorithm.MD5 -> MD5
    DigestAlgorithm.SHA_1 -> SHA1
    DigestAlgorithm.SHA_224 -> SHA224
    DigestAlgorithm.SHA_256 -> SHA256
    DigestAlgorithm.SHA_384 -> SHA384
    DigestAlgorithm.SHA_512 -> SHA512
    DigestAlgorithm.SHA3_224 -> SHA3_224
    DigestAlgorithm.SHA3_256 -> SHA3_256
    DigestAlgorithm.SHA3_384 -> SHA3_384
    DigestAlgorithm.SHA3_512 -> SHA3_512
    DigestAlgorithm.RIPEMD_160 -> RIPEMD160
    else -> error("Unsupported digest: $name")
}

internal val DigestAlgorithm.sizeBytes: Int
    get() = when (this) {
        DigestAlgorithm.MD5 -> 16
        DigestAlgorithm.SHA_1 -> 20
        DigestAlgorithm.SHA_224, DigestAlgorithm.SHA3_224 -> 28
        DigestAlgorithm.SHA_256, DigestAlgorithm.SHA3_256 -> 32
        DigestAlgorithm.SHA_384, DigestAlgorithm.SHA3_384 -> 48
        DigestAlgorithm.SHA_512, DigestAlgorithm.SHA3_512 -> 64
        DigestAlgorithm.RIPEMD_160 -> 20
        else -> error("Unknown digest size: $name")
    }

private fun EcdsaSignatureEncoding.toCryptographyFormat(): ECDSA.SignatureFormat = when (this) {
    EcdsaSignatureEncoding.IEEE_P1363 -> ECDSA.SignatureFormat.RAW
    EcdsaSignatureEncoding.DER -> ECDSA.SignatureFormat.DER
}

private fun maxPssSaltLength(rsaBits: Int, digest: DigestAlgorithm): Int =
    ((rsaBits - 1) + Byte.SIZE_BITS - 1) / Byte.SIZE_BITS - digest.sizeBytes - 2

private fun EncodedKey.Jwk.withStoredAlgorithm(metadata: Map<String, String>): EncodedKey.Jwk {
    val algorithm = metadata[JWK_ALGORITHM_METADATA_KEY] ?: return this
    val jwk = Json.parseToJsonElement(data.toByteArray().decodeToString()).jsonObject
    return copy(
        data = BinaryData(
            Json.encodeToString(JsonObject(jwk + ("alg" to JsonPrimitive(algorithm)))).encodeToByteArray()
        )
    )
}

private fun operationUsage(operation: CryptoOperation): KeyUsage = when (operation) {
    CryptoOperation.SIGN -> KeyUsage.SIGN
    CryptoOperation.VERIFY -> KeyUsage.VERIFY
    CryptoOperation.ENCRYPT -> KeyUsage.ENCRYPT
    CryptoOperation.DECRYPT -> KeyUsage.DECRYPT
    CryptoOperation.KEY_AGREEMENT -> KeyUsage.KEY_AGREEMENT
    CryptoOperation.WRAP -> KeyUsage.WRAP
    CryptoOperation.UNWRAP -> KeyUsage.UNWRAP
    else -> error("Operation has no direct key usage: $operation")
}

private suspend fun StoredKey.Software.operationJwk(provider: CryptographyProvider): EncodedKey.Jwk =
    when (material) {
        is EncodedKey.Jwk -> material
        is EncodedKey.SpkiDer -> material.toPublicJwk(spec, provider)
        is EncodedKey.Pkcs8Der -> material.toPrivateJwk(spec, provider)
    }

private fun EncodedKey.hasPrivateMaterial(): Boolean = when (this) {
    is EncodedKey.Jwk -> privateMaterial
    is EncodedKey.SpkiDer -> false
    is EncodedKey.Pkcs8Der -> true
}
