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
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.util.io.pem.PemObject
import org.bouncycastle.util.io.pem.PemWriter
import java.io.ByteArrayOutputStream
import java.security.*
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
            val value = it.value
            when (value) {
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

    private fun getECPublicKeyBytes(key: java.security.interfaces.ECPublicKey): ByteArray {
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
        else -> TODO("Not yet supported: $keyType")
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
