package id.walt.crypto.keys

import com.nimbusds.jose.*
import com.nimbusds.jose.crypto.*
import com.nimbusds.jose.crypto.bc.BouncyCastleProviderSingleton
import com.nimbusds.jose.jwk.*
import com.nimbusds.jose.jwk.KeyType.*
import com.nimbusds.jose.util.Base64URL
import id.walt.crypto.utils.JsonUtils.toJsonElement
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.*
import org.bouncycastle.asn1.ASN1BitString
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.edec.EdECObjectIdentifiers
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import java.security.*
import java.security.spec.PKCS8EncodedKeySpec
import java.util.*

@Serializable
@SerialName("local")
actual class LocalKey actual constructor(
    @Suppress("CanBeParameter", "RedundantSuppression")
    var jwk: String?
) : Key() {

    @Transient
    private lateinit var _internalJwk: JWK

    init {
        if (jwk != null) {
            _internalJwk = JWK.parse(jwk)
        }
    }


    constructor(jwkObject: JWK) : this(null) {
        _internalJwk = jwkObject
        jwk = _internalJwk.toJSONString()
    }

    actual override suspend fun getPublicKeyRepresentation(): ByteArray = when (keyType) {
        KeyType.Ed25519 -> _internalJwk.toOctetKeyPair().decodedX
        KeyType.RSA -> getRsaPublicKeyBytes(_internalJwk.toRSAKey().toPublicKey())
        KeyType.secp256k1, KeyType.secp256r1 -> _internalJwk.toECKey().toPublicKey().encoded
        else -> TODO("Not yet implemented for: $keyType")
    }

    actual override suspend fun exportJWK(): String = _internalJwk.toJSONString()
    actual override suspend fun exportJWKObject(): JsonObject =
        JsonObject(_internalJwk.toJSONObject().mapValues { JsonPrimitive(it.value as String) })

    actual override suspend fun exportPEM(): String {
        TODO("Not yet implemented")
    }

    private val _internalSigner: JWSSigner by lazy {
        when (keyType) {
            KeyType.Ed25519 -> Ed25519Signer(_internalJwk as OctetKeyPair)

            KeyType.secp256k1 -> ECDSASigner(_internalJwk as ECKey).apply {
                jcaContext.provider = BouncyCastleProviderSingleton.getInstance()
            }

            KeyType.secp256r1 -> ECDSASigner(_internalJwk as ECKey)

            KeyType.RSA -> RSASSASigner(_internalJwk as RSAKey)
        }
    }

    private val _internalVerifier: JWSVerifier by lazy {
        when (keyType) {
            KeyType.Ed25519 -> Ed25519Verifier((_internalJwk as OctetKeyPair).toPublicJWK())

            KeyType.secp256k1 -> ECDSAVerifier(_internalJwk as ECKey).apply {
                jcaContext.provider = BouncyCastleProviderSingleton.getInstance()
            }

            KeyType.secp256r1 -> ECDSAVerifier(_internalJwk as ECKey)

            KeyType.RSA -> RSASSAVerifier(_internalJwk as RSAKey)
        }
    }

    private val _internalJwsAlgorithm by lazy {
        when (keyType) {
            KeyType.Ed25519 -> JWSAlgorithm.EdDSA
            KeyType.secp256k1 -> JWSAlgorithm.ES256K
            KeyType.secp256r1 -> JWSAlgorithm.ES256
            KeyType.RSA -> JWSAlgorithm.RS256 // TODO: RS384 RS512
        }
    }

    actual override suspend fun signRaw(plaintext: ByteArray): ByteArray {
        check(hasPrivateKey) { "No private key is attached to this key!" }
        val signature = getSignature()
        signature.initSign(getPrivateKey())
        signature.update(plaintext)
        val sig = signature.sign()
        return sig
    }

    /**
     * Signs as a JWS: Signs a message using this private key (with the algorithm this key is based on)
     * @exception IllegalArgumentException when this is not a private key
     * @param plaintext data to be signed
     * @return signed (JWS)
     */
    actual override suspend fun signJws(plaintext: ByteArray, headers: Map<String, String>): String {
        check(hasPrivateKey) { "No private key is attached to this key!" }
        val jwsObject = JWSObject(
            JWSHeader.Builder(_internalJwsAlgorithm).customParams(headers).build(),
            Payload(plaintext)
        )

        jwsObject.sign(_internalSigner)
        return jwsObject.serialize()
    }

    actual override suspend fun verifyRaw(signed: ByteArray, detachedPlaintext: ByteArray?): Result<ByteArray> {
        TODO("Not yet implemented")
    }

    /**
     * Verifies JWS: Verifies a signed message using this public key
     * @param signed signed
     * @return Result wrapping the plaintext; Result failure when the signature fails
     */
    actual override suspend fun verifyJws(signedJws: String): Result<JsonObject> = runCatching {
        val jwsObject = JWSObject.parse(signedJws)

        check(jwsObject.verify(_internalVerifier)) { "Signature check failed." }

        val objectElements = jwsObject.payload.toJSONObject()
            .mapValues { it.value.toJsonElement() }
        JsonObject(objectElements)
    }

    /**
     * Verifies JWS: Verifies a signed message using this public key
     * @param signed signed
     * @return Result wrapping the plaintext; Result failure when the signature fails
     */

    actual override suspend fun getPublicKey(): LocalKey = LocalKey(_internalJwk.toPublicJWK())

    override val keyType: KeyType by lazy {
        when (_internalJwk.keyType) {
            RSA -> KeyType.RSA
            EC -> {
                when (_internalJwk.toECKey().curve) {
                    Curve.P_256 -> KeyType.secp256r1
                    Curve.SECP256K1 -> KeyType.secp256k1
                    else -> throw IllegalArgumentException("EC key with curve ${_internalJwk.toECKey().curve} not suppoerted")
                }
            }

            OKP -> {
                when (_internalJwk.toOctetKeyPair().curve) {
                    Curve.Ed25519 -> KeyType.Ed25519
                    else -> throw IllegalArgumentException("OKP key with curve ${_internalJwk.toOctetKeyPair().curve} not supported")
                }
            }

            else -> {
                throw IllegalArgumentException("Unknown key type: ${_internalJwk.keyType}")
            }
        }
    }

    actual override val hasPrivateKey: Boolean
        get() = _internalJwk.isPrivate

    actual override suspend fun getKeyId(): String = _internalJwk.keyID ?: getThumbprint()

    actual override suspend fun getThumbprint(): String = _internalJwk.computeThumbprint().toString()

    private fun getRsaPublicKeyBytes(key: PublicKey): ByteArray {
        val pubPrim = ASN1Sequence.fromByteArray(key.encoded) as ASN1Sequence
        return (pubPrim.getObjectAt(1) as ASN1BitString).octets
    }

    private fun getPrivateKey() = when (keyType) {
        KeyType.secp256r1, KeyType.secp256k1 -> _internalJwk.toECKey().toPrivateKey()
        KeyType.Ed25519 -> decodeEd25519RawPrivKey(_internalJwk.toOctetKeyPair().d.toString(), getKeyFactory())
        KeyType.RSA -> _internalJwk.toRSAKey().toPrivateKey()
    }

    private fun getSignature(): Signature = when (keyType) {
        KeyType.secp256k1, KeyType.secp256r1 -> Signature.getInstance("SHA256withECDSA")
        KeyType.Ed25519 -> Signature.getInstance("Ed25519")
        KeyType.RSA -> Signature.getInstance("SHA256withRSA")
    }

    private fun getKeyFactory() = when (keyType) {
        KeyType.secp256r1, KeyType.secp256k1 -> KeyFactory.getInstance("ECDSA")
        KeyType.Ed25519 -> KeyFactory.getInstance("Ed25519")
        KeyType.RSA -> KeyFactory.getInstance("RSA")
    }

    private fun decodeEd25519RawPrivKey(base64: String, kf: KeyFactory): PrivateKey {
        val privKeyInfo =
            PrivateKeyInfo(
                AlgorithmIdentifier(EdECObjectIdentifiers.id_Ed25519),
                DEROctetString(Base64URL.from(base64).decode())
            )
        val pkcs8KeySpec = PKCS8EncodedKeySpec(privKeyInfo.encoded)
        return kf.generatePrivate(pkcs8KeySpec)
    }

    actual companion object : LocalKeyCreator {
        actual override suspend fun generate(type: KeyType, metadata: LocalKeyMetadata): LocalKey = JvmLocalKeyCreator.generate(type, metadata)
        actual override suspend fun importJWK(jwk: String): Result<LocalKey> = JvmLocalKeyCreator.importJWK(jwk)
        actual override suspend fun importPEM(pem: String): Result<LocalKey> = JvmLocalKeyCreator.importPEM(pem)
        actual override suspend fun importRawPublicKey(
            type: KeyType,
            rawPublicKey: ByteArray,
            metadata: LocalKeyMetadata
        ): Key =
            JvmLocalKeyCreator.importRawPublicKey(type, rawPublicKey, metadata)
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
