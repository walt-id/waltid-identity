package id.walt.crypto2.pkcs11

import id.walt.crypto2.algorithms.*
import id.walt.crypto2.keys.*
import id.walt.crypto2.keys.Signer
import id.walt.crypto2.providers.GenerateManagedKeyRequest
import id.walt.crypto2.providers.ManagedKeyProvider
import id.walt.crypto2.serialization.BinaryData
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.*
import java.security.spec.ECGenParameterSpec
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PSSParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

/**
 * PKCS#11 managed-key provider.
 *
 * Vendor-neutral by construction: token addressing, capability advertisement and signature-algorithm selection are all
 * resolved from what the token reports rather than from assumptions about a particular library. Verified against
 * SoftHSMv2; see [Pkcs11Options] for how to address a Thales Luna partition or a tpm2-pkcs11 token.
 *
 * Keys are sign/verify only - see [validateUsages].
 */
class Pkcs11KeyProvider(
    pinResolver: Pkcs11PinResolver,
) : ManagedKeyProvider {
    override val id = ID
    private val sessions = Pkcs11SessionFactory(pinResolver)

    override suspend fun generate(request: GenerateManagedKeyRequest): ManagedKey {
        val options = Pkcs11Options.decode(request.providerOptions)
        val alias = options.alias ?: request.id.value
        validateUsages(request.usages)
        val spec = request.spec
        // The hardening attribute template is best-effort: tpm2-pkcs11 rejects a generate template containing any
        // attribute at all. Try it first, and downgrade this token's session once if it is refused.
        val session = sessions.session(options)
        return try {
            generateOn(session, options, alias, spec, request)
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            // Not GeneralSecurityException: SunPKCS11 reports this as java.security.ProviderException, which extends
            // RuntimeException.
            if (session.hardened && cause.indicatesRejectedAttributeTemplate()) {
                return try {
                    generateOn(sessions.downgradeToPlainSession(options), options, alias, spec, request)
                } catch (retry: CancellationException) {
                    throw retry
                } catch (retry: Exception) {
                    if (retry.indicatesTokenFailure()) sessions.invalidate(options)
                    throw retry
                }
            }
            if (cause.indicatesTokenFailure()) sessions.invalidate(options)
            throw cause
        }
    }

    private suspend fun generateOn(
        session: Pkcs11Session,
        options: Pkcs11Options,
        alias: String,
        spec: KeySpec,
        request: GenerateManagedKeyRequest,
    ): ManagedKey {
        require(!session.keyStore.containsAlias(alias)) { "PKCS11 alias already exists: $alias" }
        require(session.supportsKeyPairGeneration(spec.keyPairGeneratorName())) {
            "PKCS11 token cannot generate ${spec.keyPairGeneratorName()} keys"
        }
        val advertised = spec.signatureAlgorithms(session)
        require(advertised.isNotEmpty()) {
            "PKCS11 token implements no signature algorithm for key specification: $spec"
        }

        val keyPair = when (spec) {
            is KeySpec.Ec -> KeyPairGenerator.getInstance("EC", session.provider).apply {
                initialize(ECGenParameterSpec(spec.curve.jcaName()))
            }.generateKeyPair()

            is KeySpec.Rsa -> KeyPairGenerator.getInstance("RSA", session.provider).apply {
                initialize(spec.bits)
            }.generateKeyPair()

            else -> throw IllegalArgumentException("Unsupported PKCS11 key specification: $spec")
        }
        // CKA_TOKEN = true makes the key pair persistent the moment it is generated, so a failure between here and
        // setKeyEntry would leave an unreachable private key occupying token storage with no alias to delete it by.
        val certificate = try {
            verifyGeneratedKeySafety(session, spec, keyPair)
            createPkcs11Certificate(alias, spec, keyPair, session)
                .also { session.keyStore.setKeyEntry(alias, keyPair.private, null, arrayOf(it)) }
        } catch (cause: Throwable) {
            runCatching { session.keyStore.deleteEntry(alias) }
            throw cause
        }
        val storedData = Pkcs11StoredKeyData(options.copy(alias = null), alias)
        return key(
            advertised,
            StoredKey.Managed(
                version = StoredKey.CURRENT_VERSION,
                id = request.id,
                spec = spec,
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

    /**
     * Checks the two properties that make a token-held key worth having, on the key the token actually produced.
     *
     * The attribute template is only a request, and on tokens that reject it the key is created with the token's own
     * defaults - so the properties are verified rather than assumed. Measured behaviour that motivates this: SoftHSM
     * without the template returns an RSA private key that is decrypt-capable, i.e. an RSAES-PKCS1-v1_5 padding
     * oracle for any PKCS#11 client holding the PIN; tpm2-pkcs11 rejects the template entirely but produces a key
     * that is sensitive and signing-only because the hardware cannot do otherwise.
     */
    private fun verifyGeneratedKeySafety(session: Pkcs11Session, spec: KeySpec, keyPair: KeyPair) {
        require(keyPair.private.encoded == null) {
            "PKCS11 token produced an extractable private key; refusing to use it"
        }
        if (spec !is KeySpec.Rsa) return
        val decryptCapable = runCatching {
            Cipher.getInstance("RSA/ECB/PKCS1Padding", session.provider)
                .init(Cipher.DECRYPT_MODE, keyPair.private)
        }.isSuccess
        require(!decryptCapable) {
            "PKCS11 token produced an RSA private key that permits decryption, which is an RSAES-PKCS1-v1_5 padding " +
                    "oracle. The token rejected the CKA_DECRYPT=false template; set it through " +
                    "Pkcs11Options.providerConfigurationLines or use an EC key."
        }
    }

    override suspend fun restore(stored: StoredKey.Managed): ManagedKey {
        require(stored.provider == id) { "Stored key belongs to a different provider" }
        require(stored.providerSchemaVersion == PROVIDER_SCHEMA_VERSION) {
            "Unsupported PKCS11 provider schema: ${stored.providerSchemaVersion}"
        }
        validateUsages(stored.usages)
        val expectedPublicKey = stored.publicKey as? EncodedKey.SpkiDer
            ?: throw IllegalArgumentException("Stored PKCS11 key is missing its SPKI public key")
        val data = Pkcs11StoredKeyData.decode(stored.providerData)
        val advertised = onToken(data.options) { session ->
            require(session.keyStore.isKeyEntry(data.alias)) { "PKCS11 alias does not exist: ${data.alias}" }
            require(
                session.keyStore.getCertificate(data.alias).publicKey.encoded
                    .contentEquals(expectedPublicKey.data.toByteArray())
            ) { "PKCS11 public key changed after restore" }
            // Probed once here, while a session is available: KeyCapabilities.supportsSignatureAlgorithm is not a
            // suspending predicate, so it cannot reach the token. Failing here also means a spec the token cannot
            // sign with is rejected at restore instead of on the first signature.
            stored.spec.signatureAlgorithms(session).also {
                require(it.isNotEmpty()) {
                    "PKCS11 token implements no signature algorithm for stored key specification: ${stored.spec}"
                }
            }
        }
        return key(advertised, stored, data)
    }

    override suspend fun close() = sessions.close()

    /**
     * Runs a token operation and rebuilds the token's session if the token or its library failed.
     *
     * Without this, one failed operation is permanent: on tpm2-pkcs11 a rejected key generation leaves the ESAPI
     * context in a state where every later operation fails, including operations on unrelated working keys. Verified
     * against a real TPM through the full service stack.
     */
    private suspend fun <T> onToken(options: Pkcs11Options, block: suspend (Pkcs11Session) -> T): T = try {
        block(sessions.session(options))
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: Exception) {
        if (cause.indicatesTokenFailure()) sessions.invalidate(options)
        throw cause
    }

    private fun key(
        advertisedAlgorithms: Set<SignatureAlgorithm>,
        stored: StoredKey.Managed,
        data: Pkcs11StoredKeyData,
    ): ManagedKey = Pkcs11ManagedKey(advertisedAlgorithms, stored, data)

    private inner class Pkcs11ManagedKey(
        private val advertisedAlgorithms: Set<SignatureAlgorithm>,
        override val storedKey: StoredKey.Managed,
        private val data: Pkcs11StoredKeyData,
    ) : ManagedKey {

        override val capabilities = KeyCapabilities(
            signer = KeyUsage.SIGN.takeIf(storedKey.usages::contains)?.let {
                Signer { message, algorithm -> sign(message, algorithm) }
            },
            verifier = KeyUsage.VERIFY.takeIf(storedKey.usages::contains)?.let {
                Verifier { message, signature, algorithm -> verify(message, signature, algorithm) }
            },
            deleter = KeyDeleter { delete() },
            publicKeyExporter = PublicKeyExporter { requireNotNull(storedKey.publicKey) },
            // Probed from this token's mechanism list rather than declared from a table, so a token without RSA-PSS
            // or with only raw CKM_ECDSA advertises exactly what it can do.
            signatureAlgorithms = advertisedAlgorithms,
            supportsSignatureAlgorithm = { it in advertisedAlgorithms },
        )

        private suspend fun sign(message: ByteArray, algorithm: SignatureAlgorithm): ByteArray {
            requireSupported(algorithm)
            val signature = onToken(data.options) { session ->
                algorithm.sign(session, session.privateKey(data.alias), message)
            }
            return if (algorithm is SignatureAlgorithm.Ecdsa && algorithm.encoding == EcdsaSignatureEncoding.IEEE_P1363) {
                EcdsaSignatureCodec.derToP1363(signature, storedKey.spec.ecComponentSize())
            } else signature
        }

        /**
         * Verification runs in software against the pinned public key, never on the token.
         *
         * It needs no private key, so involving the token only adds a round trip - and it is not portable: the
         * public key handed to the token comes from the stored certificate, so it is an ordinary software key that
         * the token must import first. tpm2-pkcs11 cannot, and SunPKCS11 then reports the signature as simply
         * invalid rather than failing, so every verification silently returned false. Measured on a real TPM.
         */
        private fun verify(
            message: ByteArray,
            signature: ByteArray,
            algorithm: SignatureAlgorithm,
        ): Boolean {
            requireSupported(algorithm)
            // The signature is untrusted input, so a malformed one (wrong length, undecodable DER) is an invalid
            // signature rather than an error the caller has to catch.
            return try {
                val derSignature =
                    if (algorithm is SignatureAlgorithm.Ecdsa && algorithm.encoding == EcdsaSignatureEncoding.IEEE_P1363) {
                        EcdsaSignatureCodec.p1363ToDer(signature, storedKey.spec.ecComponentSize())
                    } else signature
                algorithm.verifyInSoftware(publicKey(), message, derSignature)
            } catch (_: IllegalArgumentException) {
                false
            } catch (_: GeneralSecurityException) {
                false
            }
        }

        /** The pinned public key from the stored descriptor, decoded once. restore() proved it matches the token. */
        private val publicKey: () -> PublicKey = run {
            val spki = requireNotNull(storedKey.publicKey as? EncodedKey.SpkiDer) {
                "Stored PKCS11 key is missing its SPKI public key"
            }.data.toByteArray()
            val algorithm = when (storedKey.spec) {
                is KeySpec.Ec -> "EC"
                is KeySpec.Rsa -> "RSA"
                else -> throw IllegalArgumentException("Unsupported PKCS11 key specification: ${storedKey.spec}")
            }
            val decoded by lazy { KeyFactory.getInstance(algorithm).generatePublic(X509EncodedKeySpec(spki)) }
            ({ decoded })
        }

        private suspend fun delete(): KeyDeletionResult = onToken(data.options) { session ->
            // deleteEntry is a silent no-op for an unknown alias, so without this the caller would be told a key was
            // deleted that never existed.
            require(session.keyStore.containsAlias(data.alias)) {
                "PKCS11 alias does not exist: ${data.alias}"
            }
            session.keyStore.deleteEntry(data.alias)
            KeyDeletionResult.Deleted
        }

        private fun requireSupported(algorithm: SignatureAlgorithm) {
            require(algorithm in advertisedAlgorithms) {
                "Unsupported PKCS11 signature algorithm for this token: $algorithm"
            }
        }
    }

    /**
     * Descriptor for a key that already exists on a token, without generating anything.
     *
     * This is the normal HSM workflow: the key and its certificate are provisioned by whoever administers the token,
     * often under a key ceremony, and the application is given only an alias and a PIN. Reading the public key from
     * the token also proves the alias exists, that it has a usable certificate, and that the PIN is correct, before
     * any descriptor is persisted. The key specification is derived from the token's own public key rather than
     * supplied by the caller, so a descriptor cannot disagree with the key it points at.
     */
    suspend fun storedKeyForExisting(
        id: KeyId,
        usages: Set<KeyUsage>,
        options: Pkcs11Options,
        metadata: Map<String, String> = emptyMap(),
    ): StoredKey.Managed {
        validateUsages(usages)
        val alias = requireNotNull(options.alias) { "Attaching an existing PKCS11 key requires its alias" }
        val certificate = onToken(options) { session ->
            require(session.keyStore.isKeyEntry(alias)) { "PKCS11 alias does not exist: $alias" }
            requireNotNull(session.keyStore.getCertificate(alias)) {
                "PKCS11 alias has no certificate, so its public key cannot be read: $alias"
            }.also {
                require(it.publicKey.toKeySpec().signatureAlgorithms(session).isNotEmpty()) {
                    "PKCS11 token implements no signature algorithm for key specification: ${it.publicKey.toKeySpec()}"
                }
            }
        }
        val spec = certificate.publicKey.toKeySpec()
        return StoredKey.Managed(
            version = StoredKey.CURRENT_VERSION,
            id = id,
            spec = spec,
            usages = usages,
            provider = ID,
            providerSchemaVersion = PROVIDER_SCHEMA_VERSION,
            providerData = Pkcs11StoredKeyData(options.copy(alias = null), alias).encode(),
            publicKey = EncodedKey.SpkiDer(BinaryData(certificate.publicKey.encoded)),
            metadata = metadata,
        )
    }

    companion object {
        val ID = ProviderId("pkcs11-jca")
        private const val PROVIDER_SCHEMA_VERSION = 1

        /**
         * Whether a failure looks like the token refusing the generate attribute template rather than a real error.
         * tpm2-pkcs11 answers `CKR_GENERAL_ERROR` for a single-attribute template and `CKR_ATTRIBUTE_VALUE_INVALID`
         * for a longer one, and other libraries use the more specific attribute codes, so all are treated as a
         * template rejection worth retrying once without it.
         */
        private fun Exception.indicatesRejectedAttributeTemplate(): Boolean {
            val text = generateSequence(this as Throwable) { it.cause }
                .mapNotNull { it.message }
                .joinToString(" ")
            return sequenceOf(
                "CKR_ATTRIBUTE_VALUE_INVALID",
                "CKR_ATTRIBUTE_TYPE_INVALID",
                "CKR_ATTRIBUTE_READ_ONLY",
                "CKR_TEMPLATE_INCONSISTENT",
                "CKR_TEMPLATE_INCOMPLETE",
                "CKR_GENERAL_ERROR",
            ).any(text::contains)
        }
    }
}

@Serializable
private data class Pkcs11StoredKeyData(val options: Pkcs11Options, val alias: String) {
    fun encode(): BinaryData = BinaryData(json.encodeToString(this).encodeToByteArray())

    companion object {
        private val json = Json { encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = false }
        fun decode(data: BinaryData): Pkcs11StoredKeyData = json.decodeFromString(data.toByteArray().decodeToString())
    }
}

private val signingUsages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY)

private val rsaDigests = listOf(DigestAlgorithm.SHA_256, DigestAlgorithm.SHA_384, DigestAlgorithm.SHA_512)

private fun validateUsages(usages: Set<KeyUsage>) {
    require(usages.isNotEmpty()) { "PKCS11 key usages cannot be empty" }
    // Signing only: JCA exposes no RSA-OAEP for PKCS#11 tokens (SunPKCS11 registers only RSA/ECB/PKCS1Padding and
    // RSA/ECB/NoPadding, and P11RSACipher rejects every OAEP padding), and RSAES-PKCS1-v1_5 decryption is a
    // Bleichenbacher padding oracle, so RSA encryption and key wrapping are deliberately not offered.
    require(usages.all(signingUsages::contains)) {
        "PKCS11 keys only support signing usages, got: ${usages - signingUsages}"
    }
}

private fun KeySpec.keyPairGeneratorName(): String = when (this) {
    is KeySpec.Ec -> "EC"
    is KeySpec.Rsa -> "RSA"
    else -> throw IllegalArgumentException("Unsupported PKCS11 key specification: $this")
}

/**
 * Signature algorithms this token offers for this key specification.
 *
 * Every candidate is checked against the token's registered services, so a token without RSA-PSS, or an EC token
 * offering only `CKM_ECDSA`, advertises exactly what it can do. Both ECDSA encodings are offered wherever ECDSA works
 * because the DER/P1363 conversion is done here, not on the token.
 */
private fun KeySpec.signatureAlgorithms(session: Pkcs11Session): Set<SignatureAlgorithm> = when (this) {
    is KeySpec.Ec -> {
        val digest = curve.ecdsaDigest() ?: return emptySet()
        if (!session.supportsEcdsa) emptySet()
        else EcdsaSignatureEncoding.entries.mapTo(mutableSetOf()) { SignatureAlgorithm.Ecdsa(digest, it) }
    }

    is KeySpec.Rsa -> rsaDigests.flatMapTo(mutableSetOf()) { digest ->
        buildList {
            if (session.supportsSignature("${digest.jcaDigest()}withRSA")) add(SignatureAlgorithm.RsaPkcs1(digest))
            // SunPKCS11 registers RSASSA-PSS only when the token implements CKM_RSA_PKCS_PSS.
            if (session.supportsSignature("RSASSA-PSS")) {
                add(SignatureAlgorithm.RsaPss(digest, saltLengthBytes = requireNotNull(digest.outputSizeBytes)))
            }
        }
    }

    else -> emptySet()
}

/**
 * ECDSA is offered when the token implements raw `CKM_ECDSA`, which is the mechanism every PKCS#11 token provides and
 * the only one used here. The combined `CKM_ECDSA_SHA*` mechanisms are deliberately ignored even when SunPKCS11
 * registers them: SoftHSMv2 registers `SHA256withECDSA` and signing with it fails, so treating registration as proof
 * of support would advertise an algorithm that does not work. The digest is computed on this side either way, which is
 * also why support does not depend on the digest.
 */
private val Pkcs11Session.supportsEcdsa: Boolean get() = supportsSignature("NONEwithECDSA")

private fun EcCurve.ecdsaDigest(): DigestAlgorithm? = when (this) {
    EcCurve.P256 -> DigestAlgorithm.SHA_256
    EcCurve.P384 -> DigestAlgorithm.SHA_384
    EcCurve.P521 -> DigestAlgorithm.SHA_512
    else -> null
}

private fun SignatureAlgorithm.jcaSignature(session: Pkcs11Session): Signature = when (this) {
    is SignatureAlgorithm.RsaPkcs1 -> Signature.getInstance("${digest.jcaDigest()}withRSA", session.provider)
    is SignatureAlgorithm.RsaPss -> Signature.getInstance("RSASSA-PSS", session.provider).apply {
        require(mgfDigest == digest && saltLengthBytes == digest.outputSizeBytes) {
            "PKCS11 RSA-PSS requires matching digest, MGF digest, and salt length"
        }
        setParameter(
            PSSParameterSpec(digest.jcaName(), "MGF1", digest.mgfSpec(), requireNotNull(saltLengthBytes), 1)
        )
    }

    else -> throw IllegalArgumentException("Unsupported PKCS11 signature algorithm: $this")
}

/** @see supportsEcdsa for why ECDSA always pre-hashes here and uses raw `CKM_ECDSA`. */
private fun SignatureAlgorithm.sign(
    session: Pkcs11Session,
    key: PrivateKey,
    message: ByteArray,
): ByteArray = if (this is SignatureAlgorithm.Ecdsa) {
    val digested = MessageDigest.getInstance(digest.jcaName()).digest(message)
    Signature.getInstance("NONEwithECDSA", session.provider).apply {
        initSign(key)
        update(digested)
    }.sign()
} else {
    jcaSignature(session).apply {
        initSign(key)
        update(message)
    }.sign()
}

/**
 * Software verification. Uses the platform's default provider and the combined digest-and-sign algorithm names, which
 * every software provider implements - so unlike the token path there is no raw-mechanism fallback to make.
 */
private fun SignatureAlgorithm.verifyInSoftware(
    key: PublicKey,
    message: ByteArray,
    signature: ByteArray,
): Boolean {
    val jca = when (this) {
        is SignatureAlgorithm.Ecdsa -> Signature.getInstance("${digest.jcaDigest()}withECDSA")
        is SignatureAlgorithm.RsaPkcs1 -> Signature.getInstance("${digest.jcaDigest()}withRSA")
        is SignatureAlgorithm.RsaPss -> Signature.getInstance("RSASSA-PSS").apply {
            setParameter(
                PSSParameterSpec(digest.jcaName(), "MGF1", digest.mgfSpec(), requireNotNull(saltLengthBytes), 1)
            )
        }

        else -> throw IllegalArgumentException("Unsupported PKCS11 signature algorithm: $this")
    }
    return jca.apply {
        initVerify(key)
        update(message)
    }.verify(signature)
}

private fun Pkcs11Session.privateKey(alias: String): PrivateKey =
    keyStore.getKey(alias, null) as? PrivateKey
        ?: throw IllegalArgumentException("PKCS11 alias has no private key: $alias")

/** Derives the key specification from a token-resident public key, for the attach-existing-key path. */
private fun PublicKey.toKeySpec(): KeySpec = when (this) {
    is java.security.interfaces.ECPublicKey -> when (params.curve.field.fieldSize) {
        256 -> KeySpec.Ec(EcCurve.P256)
        384 -> KeySpec.Ec(EcCurve.P384)
        521 -> KeySpec.Ec(EcCurve.P521)
        else -> throw IllegalArgumentException("Unsupported PKCS11 EC field size: ${params.curve.field.fieldSize}")
    }

    is java.security.interfaces.RSAPublicKey -> KeySpec.Rsa(modulus.bitLength())
    else -> throw IllegalArgumentException("Unsupported PKCS11 public key algorithm: $algorithm")
}

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
    DigestAlgorithm.SHA_256 -> "SHA-256"
    DigestAlgorithm.SHA_384 -> "SHA-384"
    DigestAlgorithm.SHA_512 -> "SHA-512"
    else -> throw IllegalArgumentException("Unsupported PKCS11 digest: $name")
}

private fun DigestAlgorithm.mgfSpec(): MGF1ParameterSpec = when (this) {
    DigestAlgorithm.SHA_256 -> MGF1ParameterSpec.SHA256
    DigestAlgorithm.SHA_384 -> MGF1ParameterSpec.SHA384
    DigestAlgorithm.SHA_512 -> MGF1ParameterSpec.SHA512
    else -> throw IllegalArgumentException("Unsupported PKCS11 digest: $name")
}
