package id.walt.crypto.keys.jwk

import com.google.crypto.tink.subtle.Ed25519Verify
import com.nimbusds.jose.*
import com.nimbusds.jose.crypto.*
import com.nimbusds.jose.crypto.bc.BouncyCastleProviderSingleton
import com.nimbusds.jose.jwk.*
import com.nimbusds.jose.util.Base64URL
import id.walt.crypto.keys.*
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.crypto.utils.JsonUtils.toJsonObject
import id.walt.crypto.utils.JwsUtils.decodeJws
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import org.bouncycastle.asn1.ASN1BitString
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.edec.EdECObjectIdentifiers
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.crypto.AsymmetricCipherKeyPair
import org.bouncycastle.crypto.hpke.HPKE
import org.bouncycastle.crypto.params.*
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.util.io.pem.PemObject
import org.bouncycastle.util.io.pem.PemWriter
import java.io.ByteArrayOutputStream
import java.security.*
import java.security.interfaces.ECPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import kotlin.math.max
import kotlin.math.min


private val bouncyCastleProvider = BouncyCastleProvider()
private val log = KotlinLogging.logger { }

@Serializable
@SerialName("jwk")
actual class JWKKey actual constructor(
    @Suppress("CanBeParameter", "RedundantSuppression")
    @Serializable(with = JWKKeyJsonFieldSerializer::class)
    var jwk: String?,
    val _keyId: String?
) : Key() {

    @Transient
    lateinit var _internalJwk: JWK

    init {
        if (jwk != null) {
            _internalJwk = JWK.parse(jwk)
        }

        if (bouncyCastleProvider !in Security.getProviders()) {
            Security.addProvider(bouncyCastleProvider)
        }
    }

    constructor(jwkObject: JWK) : this(null) {
        _internalJwk = jwkObject
        jwk = _internalJwk.toJSONString()
    }

    actual override suspend fun getPublicKeyRepresentation(): ByteArray = when (keyType) {
        KeyType.Ed25519 -> _internalJwk.toOctetKeyPair().decodedX
        KeyType.RSA, KeyType.RSA3072, KeyType.RSA4096 -> getRsaPublicKeyBytes(_internalJwk.toRSAKey().toPublicKey())
        KeyType.secp256k1, KeyType.secp256r1, KeyType.secp384r1, KeyType.secp521r1 -> getECPublicKeyBytes(
            _internalJwk.toECKey().toECPublicKey()
        )
    }

    actual override suspend fun getMeta(): JwkKeyMeta = JwkKeyMeta(getKeyId())
    actual override suspend fun deleteKey() = true

    actual override suspend fun exportJWK(): String = _internalJwk.toJSONString()

    actual override suspend fun exportJWKObject(): JsonObject =
        JsonObject(_internalJwk.toJSONObject().mapValues {
            when (val value = it.value) {
                is String -> JsonPrimitive(value)
                is Number -> JsonPrimitive(value)
                is ArrayList<*> -> JsonPrimitive(value.toString())
                else -> throw IllegalArgumentException("Unsupported value type: ${value::class.simpleName} in field ${it.key}")

            }
        })

    actual override suspend fun exportPEM(): String {
        val pemObjects = ArrayList<PemObject>()

        when (keyType) {
            KeyType.secp256k1, KeyType.secp256r1, KeyType.secp384r1, KeyType.secp521r1 -> _internalJwk.toECKey().let {
                if (hasPrivateKey) {
                    pemObjects.add(PemObject("PRIVATE KEY", it.toECPrivateKey().encoded))
                    pemObjects.add(
                        PemObject(
                            "PUBLIC KEY",
                            getPublicKey()._internalJwk.toECKey().toECPublicKey().encoded
                        )
                    )
                } else {
                    pemObjects.add(PemObject("PUBLIC KEY", it.toECPublicKey().encoded))
                }
            }

            KeyType.Ed25519 -> throw NotImplementedError("Ed25519 keys cannot be exported as PEM yet.")

            KeyType.RSA, KeyType.RSA3072, KeyType.RSA4096 -> _internalJwk.toRSAKey().let {
                if (hasPrivateKey) {
                    pemObjects.add(PemObject("RSA PRIVATE KEY", it.toRSAPrivateKey().encoded))
                    pemObjects.add(
                        PemObject(
                            "RSA PUBLIC KEY",
                            getPublicKey()._internalJwk.toRSAKey().toRSAPublicKey().encoded
                        )
                    )
                } else {
                    pemObjects.add(PemObject("RSA PUBLIC KEY", it.toRSAPublicKey().encoded))
                }
            }
        }

        val pem = ByteArrayOutputStream().apply {
            PemWriter(writer()).use {
                pemObjects.forEach { pemObject ->
                    it.writeObject(pemObject)
                }
            }
        }.toByteArray().toString(Charsets.UTF_8)

        return pem
    }

    private val _internalSigner: JWSSigner by lazy {
        when (keyType) {
            KeyType.Ed25519 -> Ed25519Signer(_internalJwk as OctetKeyPair)

            KeyType.secp256k1 -> ECDSASigner(_internalJwk as ECKey).apply {
                jcaContext.provider = BouncyCastleProviderSingleton.getInstance()
            }

            KeyType.secp256r1, KeyType.secp384r1, KeyType.secp521r1 -> ECDSASigner(_internalJwk as ECKey)

            KeyType.RSA, KeyType.RSA3072, KeyType.RSA4096 -> RSASSASigner(_internalJwk as RSAKey)
        }
    }

    private val _internalVerifier: JWSVerifier by lazy {
        when (keyType) {
            KeyType.Ed25519 -> Ed25519Verifier((_internalJwk as OctetKeyPair).toPublicJWK())

            KeyType.secp256k1 -> ECDSAVerifier(_internalJwk as ECKey).apply {
                jcaContext.provider = BouncyCastleProviderSingleton.getInstance()
            }

            KeyType.secp256r1, KeyType.secp384r1, KeyType.secp521r1 -> ECDSAVerifier(_internalJwk as ECKey)

            KeyType.RSA, KeyType.RSA3072, KeyType.RSA4096 -> RSASSAVerifier(_internalJwk as RSAKey)
        }
    }

    private val _internalJwsAlgorithm by lazy {
        when (keyType) {
            KeyType.Ed25519 -> JWSAlgorithm.EdDSA
            KeyType.secp256k1 -> JWSAlgorithm.ES256K
            KeyType.secp256r1 -> JWSAlgorithm.ES256
            KeyType.secp384r1 -> JWSAlgorithm.ES384
            KeyType.secp521r1 -> JWSAlgorithm.ES512
            KeyType.RSA -> JWSAlgorithm.RS256
            KeyType.RSA3072 -> JWSAlgorithm.RS384
            KeyType.RSA4096 -> JWSAlgorithm.RS512
        }
    }

    actual override suspend fun signRaw(plaintext: ByteArray, customSignatureAlgorithm: String?): ByteArray {
        check(hasPrivateKey) { "No private key is attached to this key!" }

        val signature = getSignatureAlgorithm(customSignatureAlgorithm)
        signature.initSign(getPrivateKey())
        signature.update(plaintext)
        val sig = signature.sign()
        return sig
    }

    // Helper function to convert ECDSA (DER) signature to JWS (IEEE P1363) format
    private fun convertToJWSFormat(signature: ByteArray): ByteArray {
        val derSignature = ASN1Sequence.getInstance(signature)
        val r = (derSignature.getObjectAt(0) as ASN1Integer).value
        val s = (derSignature.getObjectAt(1) as ASN1Integer).value

        val rBytes = r.toByteArray()
        val sBytes = s.toByteArray()
        val jwsSignature = ByteArray(64)

        // Pad the R and S components to 32 bytes each
        System.arraycopy(rBytes, max(0, rBytes.size - 32), jwsSignature, max(0, 32 - rBytes.size), min(32, rBytes.size))
        System.arraycopy(
            sBytes,
            max(0, sBytes.size - 32),
            jwsSignature,
            32 + max(0, 32 - sBytes.size),
            min(32, sBytes.size)
        )

        return jwsSignature
    }

    /**
     * Signs as a JWS: Signs a message using this private key (with the algorithm this key is based on)
     * @exception IllegalArgumentException when this is not a private key
     * @param plaintext data to be signed
     * @return signed (JWS)
     */
    actual override suspend fun signJws(plaintext: ByteArray, headers: Map<String, JsonElement>): String {
        check(hasPrivateKey) { "No private key is attached to this key!" }

        log.trace { "Signing JWS, Key: ${toString()}" }

        // Nimbus signature:
        val jwsObject = JWSObject(
            JWSHeader.parse(headers.toMutableMap().apply {
                put("alg", _internalJwsAlgorithm.toString().toJsonElement())
            }.toJsonObject().toString()),
            Payload(plaintext)
        )
        /*jwsObject.sign(_internalSigner)

        val nimbusJws = jwsObject.serialize()*/

        // TODO for Custom signature: check if JSON encoding of JsonObject for header & payload is correct (or 1 space is missing?)
        // Custom signature:
        /*val appendedHeader = HashMap(headers).apply {
            put("alg", _internalJwsAlgorithm.name)
        }*/

        val payloadToSign = jwsObject.header.toBase64URL().toString() + '.' + jwsObject.payload.toBase64URL().toString()
        var signed = signRaw(payloadToSign.encodeToByteArray())

        if (keyType in KeyTypes.EC_KEYS) { // Convert DER to IEEE P1363
            log.trace { "Converted DER to IEEE P1363 signature" }
            signed = EccUtils.convertDERtoIEEEP1363(signed)
        }

        val customJws = "$payloadToSign.${signed.encodeToBase64Url()}"
        log.trace { "Signed JWS: $customJws" }

        return customJws
    }

    /**
     * JWE Encryption using ECDH-ES + A128GCM.
     * This instance acts as the Recipient Public Key.
     */
    actual suspend fun encryptJwe(plaintext: ByteArray, encAlg: String): String {
        check(keyType == KeyType.secp256r1 || keyType == KeyType.secp384r1 || keyType == KeyType.secp521r1) {
            "ECDH-ES is currently only supported for EC keys (P-256, P-384, P-521). Current key type: $keyType"
        }

        // 1. Resolve Encryption Method (enc)
        val encryptionMethod = when (encAlg) {
            "A128GCM" -> EncryptionMethod.A128GCM
            "A192GCM" -> EncryptionMethod.A192GCM
            "A256GCM" -> EncryptionMethod.A256GCM
            "A128CBC-HS256" -> EncryptionMethod.A128CBC_HS256
            "A192CBC-HS384" -> EncryptionMethod.A192CBC_HS384
            "A256CBC-HS512" -> EncryptionMethod.A256CBC_HS512
            else -> throw IllegalArgumentException("Unsupported encryption algorithm: $encAlg")
        }

        // 2. Build JWE Header
        // The algorithm (alg) is fixed to ECDH-ES for this flow
        val headerBuilder = JWEHeader.Builder(JWEAlgorithm.ECDH_ES, encryptionMethod)
            .type(JOSEObjectType.JWT) // Common practice to set 'typ' to JWT

        // OpenID4VP Requirement: If the key has a Key ID, it MUST be included in the header
        _internalJwk.keyID?.let { kid ->
            headerBuilder.keyID(kid)
        }

        val header = headerBuilder.build()

        // 3. Create JWE Object
        val jweObject = JWEObject(header, Payload(plaintext))

        // 4. Create Encrypter
        // This instance (_internalJwk) represents the Verifier's Public Key.
        // Nimbus automatically generates the ephemeral key pair (EPK) internally.
        val encrypter = ECDHEncrypter(_internalJwk.toECKey())

        // 5. Encrypt
        jweObject.encrypt(encrypter)

        // 6. Serialize
        return jweObject.serialize()
    }

    /**
     * JWE Decryption using ECDH-ES + A128GCM.
     * This instance acts as the Recipient Private Key.
     */
    actual suspend fun decryptJwe(jweString: String): ByteArray {
        check(hasPrivateKey) { "Private key required for decryption." }

        // 1. Parse JWE
        val jweObject = JWEObject.parse(jweString)
        jweObject.header

        val alg = jweObject.header.algorithm
        jweObject.header.encryptionMethod
        check(alg == JWEAlgorithm.ECDH_ES)

        // 2. Validate Key Type
        // ECDH-ES requires EC keys
        check(keyType == KeyType.secp256r1 || keyType == KeyType.secp384r1 || keyType == KeyType.secp521r1) {
            "Decryption key must be an EC key (P-256, P-384, P-521)."
        }

        // 3. Create Decrypter
        // We pass our private key to unwrap the secret
        val decrypter = ECDHDecrypter(_internalJwk.toECKey())

        // 4. Decrypt
        jweObject.decrypt(decrypter)

        // 5. Return payload
        return jweObject.payload.toBytes()
    }

    actual override suspend fun verifyRaw(
        signed: ByteArray,
        detachedPlaintext: ByteArray?,
        customSignatureAlgorithm: String?
    ): Result<ByteArray> {
        check(detachedPlaintext != null) { "Detached plaintext is required." }

        if (keyType == KeyType.Ed25519) {
            val tinkVerifier = Ed25519Verify(_internalJwk.toOctetKeyPair().toPublicJWK().decodedX)
            return runCatching { tinkVerifier.verify(signed, detachedPlaintext) }.map { detachedPlaintext }
        } /*else if (keyType == KeyType.secp256r1) {
            val tinkVerifier = EcdsaVerifyJce(_internalJwk.toECKey().toECPublicKey(), Enums.HashType.SHA256, EllipticCurves.EcdsaEncoding.DER)
            return runCatching { tinkVerifier.verify(signed, detachedPlaintext) }.map { detachedPlaintext }
        }*/

        val signature = getSignatureAlgorithm(customSignatureAlgorithm)
        signature.initVerify(getInternalPublicKey())
        signature.update(detachedPlaintext)

        return runCatching {
            require(signature.verify(signed)) { "Signature verification failed" }
        }.map { detachedPlaintext }
    }


    /**
     * Verifies JWS: Verifies a signed message using this public key
     * @param signedJws signed
     * @return Result wrapping the plaintext; Result failure when the signature fails
     */
    /*actual override suspend fun verifyJws(signedJws: String): Result<JsonObject> = runCatching {
        val jwsObject = JWSObject.parse(signedJws)

        check(jwsObject.verify(_internalVerifier)) { "Signature check failed." }

        val objectElements = jwsObject.payload.toJSONObject()
            .mapValues { it.value.toJsonElement() }
        JsonObject(objectElements)
    }*/

    actual override suspend fun verifyJws(signedJws: String): Result<JsonElement> {

        // Nimbus verification (handles IEEE P1363):
        return runCatching {
            val jwsObject = JWSObject.parse(signedJws)

            check(jwsObject.verify(_internalVerifier)) { "Signature check failed." }

            val objectElements = jwsObject.payload.toJSONObject()
                .mapValues { it.value.toJsonElement() }

            JsonObject(objectElements)
        }.recoverCatching {
            // Custom verification (handles DER):
            val (header, payload, signature) = signedJws.split(".")

            log.debug { "> Signature verification: Fallback verification checking... (NIMBUS VERIFICATION FAILED) for: $signedJws" }
            val res = verifyRaw(signature.decodeFromBase64Url(), "$header.$payload".encodeToByteArray()).map {
                it.decodeToString().decodeJws().payload
            }
            res.getOrThrow()
        }
    }

    actual override suspend fun getPublicKey(): JWKKey = JWKKey(_internalJwk.toPublicJWK())

    actual override val keyType: KeyType by lazy {
        when (_internalJwk.keyType) {
            com.nimbusds.jose.jwk.KeyType.RSA -> {
                when (val bitLength = _internalJwk.toRSAKey().modulus.decodeToBigInteger().bitLength()) {
                    1024, 2048 -> KeyType.RSA
                    3072 -> KeyType.RSA3072
                    4096 -> KeyType.RSA4096
                    else -> throw IllegalArgumentException("RSA key has invalid bit size: $bitLength")
                }
            }

            com.nimbusds.jose.jwk.KeyType.EC -> {
                when (val curve = _internalJwk.toECKey().curve) {
                    Curve.P_256 -> KeyType.secp256r1
                    Curve.P_384 -> KeyType.secp384r1
                    Curve.P_521 -> KeyType.secp521r1
                    Curve.SECP256K1 -> KeyType.secp256k1
                    else -> throw IllegalArgumentException("EC key with curve ${curve} not suppoerted")
                }
            }

            com.nimbusds.jose.jwk.KeyType.OKP -> {
                when (val curve = _internalJwk.toOctetKeyPair().curve) {
                    Curve.Ed25519 -> KeyType.Ed25519
                    else -> throw IllegalArgumentException("OKP key with curve ${curve} not supported")
                }
            }

            else -> {
                throw IllegalArgumentException("Unknown key type: ${_internalJwk.keyType}")
            }
        }
    }

    actual override val hasPrivateKey: Boolean
        get() = _internalJwk.isPrivate

    actual override suspend fun getKeyId(): String = _keyId ?: _internalJwk.keyID ?: getThumbprint()

    actual override suspend fun getThumbprint(): String = _internalJwk.computeThumbprint().toString()

    private fun getRsaPublicKeyBytes(key: PublicKey): ByteArray {
        val pubPrim = ASN1Sequence.fromByteArray(key.encoded) as ASN1Sequence
        return (pubPrim.getObjectAt(1) as ASN1BitString).octets
    }

    private fun getECPublicKeyBytes(key: ECPublicKey): ByteArray {
        val curveName = Curve.forECParameterSpec(key.params).name
        return ECNamedCurveTable.getParameterSpec(curveName)
            .curve.createPoint(key.w.affineX, key.w.affineY)
            .getEncoded(true)
    }

    private fun getPrivateKey() = when (keyType) {
        KeyType.secp256k1, KeyType.secp256r1, KeyType.secp384r1, KeyType.secp521r1 -> _internalJwk.toECKey().toPrivateKey()
        KeyType.Ed25519 -> decodeEd25519RawPrivateKey(_internalJwk.toOctetKeyPair().d.toString())
        KeyType.RSA, KeyType.RSA3072, KeyType.RSA4096 -> _internalJwk.toRSAKey().toPrivateKey()
    }

    fun getInternalPublicKey(): PublicKey = when (keyType) {
        KeyType.secp256k1, KeyType.secp256r1, KeyType.secp384r1, KeyType.secp521r1 -> _internalJwk.toECKey().toECPublicKey()
        KeyType.Ed25519 -> _internalJwk.toOctetKeyPair().toPublicKey()
//        KeyType.Ed25519 -> decodeEd25519RawPublicKey(_internalJwk.toOctetKeyPair())
//        KeyType.Ed25519 -> _internalJwk.toOctetKeyPair().toPublicKey()
        KeyType.RSA, KeyType.RSA3072, KeyType.RSA4096 -> _internalJwk.toRSAKey().toRSAPublicKey()
//        else -> TODO("Not yet supported: $keyType")
    }

    private fun getSignatureAlgorithm(customSignatureAlgorithm: String? = null): Signature =
        if (customSignatureAlgorithm != null) Signature.getInstance(customSignatureAlgorithm)
        else when (keyType) {
            KeyType.secp256k1 -> Signature.getInstance("SHA256withECDSA", "BC")
            KeyType.secp256r1 -> Signature.getInstance("SHA256withECDSA")
            KeyType.secp384r1 -> Signature.getInstance("SHA384withECDSA")
            KeyType.secp521r1 -> Signature.getInstance("SHA512withECDSA")
            KeyType.Ed25519 -> Signature.getInstance("Ed25519")
            KeyType.RSA -> Signature.getInstance("SHA256withRSA") // RSASSA-PKCS1-v1_5
            KeyType.RSA3072 -> Signature.getInstance("SHA384withRSA") // RSASSA-PKCS1-v1_5
            KeyType.RSA4096 -> Signature.getInstance("SHA512withRSA") // RSASSA-PKCS1-v1_5
        }

    private fun decodeEd25519RawPrivateKey(base64: String): PrivateKey {
        val keyFactory = KeyFactory.getInstance("EdDSA")

        val privKeyInfo =
            PrivateKeyInfo(
                AlgorithmIdentifier(EdECObjectIdentifiers.id_Ed25519),
                DEROctetString(Base64URL.from(base64).decode())
            )

        val pkcs8KeySpec = PKCS8EncodedKeySpec(privKeyInfo.encoded)
        return keyFactory.generatePrivate(pkcs8KeySpec)
    }

    /*private fun decodeEd25519RawPublicKey(octetKeyPair: OctetKeyPair): PublicKey {
        val publicKeyBytes = Ed25519PublicKeyParameters(octetKeyPair.decodedX).encoded
        val publicKeySpec = X509EncodedKeySpec(publicKeyBytes)
        val keyFactory = KeyFactory.getInstance("EdDSA")
        val publicKey = keyFactory.generatePublic(publicKeySpec)

        return publicKey
    }*/

    /* private fun decodeEd25519RawPublicKey(octetKeyPair: OctetKeyPair): PublicKey {
         val publicKeyParams = Ed25519PublicKeyParameters(octetKeyPair.decodedX, 0)
         val publicKeyBytes = publicKeyParams.encoded

         val publicKeySpec = X509EncodedKeySpec(publicKeyBytes)
         val keyFactory = KeyFactory.getInstance("EdDSA")

         return keyFactory.generatePublic(publicKeySpec)
     }*/

    actual companion object : JWKKeyCreator() {

//        val prettyJson = Json { prettyPrint = true }

        @JvmBlocking
        @JvmAsync
        actual override suspend fun generate(type: KeyType, metadata: JwkKeyMeta?): JWKKey =
            JvmJWKKeyCreator.generate(type, metadata)

        @JvmBlocking
        @JvmAsync
        actual override suspend fun importJWK(jwk: String): Result<JWKKey> = JvmJWKKeyCreator.importJWK(jwk)

        @JvmBlocking
        @JvmAsync
        actual override suspend fun importPEM(pem: String): Result<JWKKey> = JvmJWKKeyCreator.importPEM(pem)

        @JvmBlocking
        @JvmAsync
        actual override suspend fun importRawPublicKey(
            type: KeyType,
            rawPublicKey: ByteArray,
            metadata: JwkKeyMeta?
        ): Key =
            JvmJWKKeyCreator.importRawPublicKey(type, rawPublicKey, metadata)
    }

    @Serializable
    data class HPKEResponseData(
        @ByteString
        @SerialName(value = "enc") val enc: ByteArray,
        @ByteString
        @SerialName(value = "cipherText") val cipherText: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is HPKEResponseData) return false

            if (!enc.contentEquals(other.enc)) return false
            if (!cipherText.contentEquals(other.cipherText)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = enc.contentHashCode()
            result = 31 * result + cipherText.contentHashCode()
            return result
        }
    }

    /**
     * Encrypts a payload using HPKE (RFC 9180) Base Mode.
     * @return A byte array containing [encapsulated_key][ciphertext]
     */
    fun encryptHpke(plaintext: ByteArray, info: ByteArray?, aad: ByteArray?): HPKEResponseData {
        // 1. Configure HPKE
        val (kem, kdf, aead) = getHpkeConfig()
        val hpke = HPKE(HPKE.mode_base, kem, kdf, aead)

        // 2. Prepare Keys
        val receiverPubKeyParams = getBcPublicKeyParams()

        // 3. Setup Base Mode (Sender)
        val senderContext = hpke.setupBaseS(receiverPubKeyParams, info ?: ByteArray(0))

        // 4. Get the Encapsulation (enc)
        val enc = senderContext.getEncapsulation()

        // 5. Encrypt (Seal)
        val ciphertext = senderContext.seal(aad ?: ByteArray(0), plaintext, 0, plaintext.size)

        // 6. Return 'enc' + 'ciphertext'
        return HPKEResponseData(enc, ciphertext)
    }

    /**
     * Decrypts a payload using HPKE (RFC 9180) Base Mode.
     * Expects input to be [encapsulated_key][ciphertext].
     */
    suspend fun decryptHpke(cipherTextWithEnc: ByteArray, info: ByteArray?, aad: ByteArray?): ByteArray {
        check(hasPrivateKey) { "Private key required for decryption." }

        // 1. Configure HPKE
        val (kem, kdf, aead) = getHpkeConfig()
        val hpke = HPKE(HPKE.mode_base, kem, kdf, aead)

        // 2. Parse Input
        val encLength = getEncLength(kem)
        check(cipherTextWithEnc.size > encLength) { "Ciphertext too short" }

        val enc = cipherTextWithEnc.copyOfRange(0, encLength)
        val ciphertext = cipherTextWithEnc.copyOfRange(encLength, cipherTextWithEnc.size)

        // 3. Prepare Receiver Key Pair (Required for setupBaseR)
        val myPrivateKeyParams = getBcPrivateKeyParams() // existing private key param
        val myPublicKeyParams = getBcPublicKeyParams()   // existing public key param

        // Bundle them into a pair - HPKE requires both for the receiver.
        val receiverKeyPair = AsymmetricCipherKeyPair(myPublicKeyParams, myPrivateKeyParams)

        // 4. Setup Base Mode (Receiver)
        // Now passing the KeyPair instead of just the private key param
        val receiverContext = hpke.setupBaseR(enc, receiverKeyPair, info ?: ByteArray(0))

        // 5. Decrypt (Open)
        return receiverContext.open(aad ?: ByteArray(0), ciphertext, 0, ciphertext.size)
    }

    // --- HPKE Helpers ---

    private fun getHpkeConfig(): Triple<Short, Short, Short> {
        // Returns (KEM, KDF, AEAD)
        // Defaulting to AES-128-GCM for P-256 and AES-256-GCM for larger curves, matching common profiles.
        return when (keyType) {
            KeyType.secp256r1 -> Triple(HPKE.kem_P256_SHA256, HPKE.kdf_HKDF_SHA256, HPKE.aead_AES_GCM128)
            KeyType.secp384r1 -> Triple(HPKE.kem_P384_SHA348, HPKE.kdf_HKDF_SHA384, HPKE.aead_AES_GCM256)
            KeyType.secp521r1 -> Triple(HPKE.kem_P521_SHA512, HPKE.kdf_HKDF_SHA512, HPKE.aead_AES_GCM256)
            KeyType.Ed25519 -> Triple(HPKE.kem_X25519_SHA256, HPKE.kdf_HKDF_SHA256, HPKE.aead_AES_GCM128)
            // Using Ed25519 keys for HPKE (encryption) usually implies converting them to X25519 or using them as X25519.
            else -> throw IllegalArgumentException("HPKE not supported for key type: $keyType")
        }
    }

    private fun getEncLength(kemId: Short): Int {
        return when (kemId) {
            HPKE.kem_P256_SHA256 -> 65 // Uncompressed point (0x04 + 32 + 32)
            HPKE.kem_P384_SHA348 -> 97 // Uncompressed point (0x04 + 48 + 48)
            HPKE.kem_P521_SHA512 -> 133 // Uncompressed point (0x04 + 66 + 66)
            HPKE.kem_X25519_SHA256 -> 32
            else -> throw IllegalArgumentException("Unknown KEM ID enc length")
        }
    }

    /**
     * Converts the internal Nimbus JWK to a Bouncy Castle Public Key Parameter.
     */
    private fun getBcPublicKeyParams(): AsymmetricKeyParameter {
        return when (keyType) {
            KeyType.secp256r1, KeyType.secp384r1, KeyType.secp521r1 -> {
                val ecKey = _internalJwk.toECKey()
                val ecSpec = ECNamedCurveTable.getParameterSpec(keyType.jwkCurve)
                val domainParams = ECDomainParameters(ecSpec.curve, ecSpec.g, ecSpec.n, ecSpec.h)

                // Decode raw X and Y from JWK
                val x = ecKey.x.decodeToBigInteger()
                val y = ecKey.y.decodeToBigInteger()
                val point = ecSpec.curve.createPoint(x, y)

                ECPublicKeyParameters(point, domainParams)
            }

            KeyType.Ed25519 -> {
                // Assuming the underlying bytes are X25519 compatible or intended for it
                val octKey = _internalJwk.toOctetKeyPair()
                X25519PublicKeyParameters(octKey.x.decode(), 0)
            }

            else -> throw IllegalArgumentException("Unsupported key type for HPKE: $keyType")
        }
    }

    /**
     * Converts the internal Nimbus JWK to a Bouncy Castle Private Key Parameter.
     */
    private fun getBcPrivateKeyParams(): AsymmetricKeyParameter {
        return when (keyType) {
            KeyType.secp256r1, KeyType.secp384r1, KeyType.secp521r1 -> {
                val ecKey = _internalJwk.toECKey()
                val ecSpec = ECNamedCurveTable.getParameterSpec(keyType.jwkCurve)
                val domainParams = ECDomainParameters(ecSpec.curve, ecSpec.g, ecSpec.n, ecSpec.h)

                // Decode 'd' (private scalar)
                val d = ecKey.d.decodeToBigInteger()

                ECPrivateKeyParameters(d, domainParams)
            }

            KeyType.Ed25519 -> {
                val octKey = _internalJwk.toOctetKeyPair()
                X25519PrivateKeyParameters(octKey.d.decode(), 0)
            }

            else -> throw IllegalArgumentException("Unsupported key type for HPKE: $keyType")
        }
    }
}

/*
object JWKSerializer : KSerializer<JWK> {
    override val descriptor = PrimitiveSerialDescriptor("JWK", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): JWK =
        JWK.parse(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: JWK) =
        encoder.encodeString(value.toJSONString())
}
*/
