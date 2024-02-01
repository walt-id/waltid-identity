package id.walt.crypto.keys

import JWK
import KeyLike
import id.walt.crypto.utils.ArrayUtils.toByteArray
import id.walt.crypto.utils.JwsUtils.jwsAlg
import id.walt.crypto.utils.PromiseUtils.await
import jose
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.khronos.webgl.Uint8Array
import kotlin.js.json

@Serializable
@SerialName("local")
actual class LocalKey actual constructor(
    var jwk: String?
) : Key() {

    @Transient
    private lateinit var _internalKey: KeyLike

    @Transient
    private lateinit var _internalJwk: JWK

    @Transient
    private var jwkToInit: String? = null

    init {
        if (jwk != null) {
            jwkToInit = jwk
            _internalJwk = JSON.parse(jwk!!)
        }
    }

    override suspend fun init() {
        if (!this::_internalKey.isInitialized) {
            if (jwkToInit != null) {
                _internalKey = await(jose.importJWK(JSON.parse(jwkToInit!!)))
            } else if (this::_internalJwk.isInitialized) {
                _internalKey = await(jose.importJWK(_internalJwk))
            }
        }

        if (!this::_internalJwk.isInitialized) {
            if (jwkToInit != null) {
                _internalJwk = JSON.parse(jwkToInit!!)
                if (_internalJwk.kid == null) {
                    _internalJwk.kid = getThumbprint()
                }
            } else {
                _internalJwk = await(jose.exportJWK(_internalKey))

                if (jwk == null) {
                    jwk = JSON.stringify(_internalJwk)
                }
            }
        }

        if (jwkToInit != null) {
            jwkToInit = null
        }

        println("-- key init --")
        println("JWK: $jwk")
        println("jwkToInit: $jwkToInit")
        println("_internalJwk: $_internalJwk")
        println("_internalKey: $_internalKey")

    }

    constructor(key: KeyLike) : this(null) {
        _internalKey = key
    }

    constructor(key: KeyLike, jwk: JWK) : this(null) {
        _internalKey = key
        _internalJwk = jwk
    }

    constructor(jwk: JWK) : this(null) {
        _internalJwk = jwk
    }

    actual override suspend fun exportJWK(): String =
        JSON.stringify(_internalJwk)

    actual override suspend fun exportJWKObject(): JsonObject {
        return Json.parseToJsonElement(exportJWK()).jsonObject
    }

    actual override suspend fun exportPEM(): String {
        //await(jose.exportSPKI())
        //await(jose.exportPKCS8(_internalKey))
        TODO("Not yet implemented")
    }

    actual override suspend fun signRaw(plaintext: ByteArray): ByteArray {
        TODO("Not yet implemented")
    }

    /**
     * Signs as a JWS: Signs a message using this private key (with the algorithm this key is based on)
     * @exception IllegalArgumentException when this is not a private key
     * @param plaintext data to be signed
     * @return signed (JWS)
     */
    actual override suspend fun signJws(plaintext: ByteArray, headers: Map<String, String>): String {
        check(this::_internalKey.isInitialized) { "_internalKey of LocalKey.js.kt is not initialized (tried to to sign operation) - has init() be called on key?" }
        check(hasPrivateKey) { "No private key is attached to this key!" }

        val headerEntries = headers.entries.toTypedArray().map { it.toPair() }.toTypedArray()

        return await(
            jose.CompactSign(Uint8Array(plaintext.toTypedArray()))
                .setProtectedHeader(json("alg" to keyType.jwsAlg(), *headerEntries))
                .sign(_internalKey)
        )
    }

    actual override suspend fun verifyRaw(signed: ByteArray, detachedPlaintext: ByteArray?): Result<ByteArray> {
        TODO("Not yet implemented")
    }

    /**
     * Verifies JWS: Verifies a signed message using this public key
     * @param signed signed
     * @return Result wrapping the plaintext; Result failure when the signature fails
     */
    actual override suspend fun verifyJws(signedJws: String): Result<JsonObject> =
        runCatching {
            Json.parseToJsonElement(
                await(jose.compactVerify(signedJws, _internalKey)).payload.toByteArray().decodeToString()
            ).jsonObject
        }

    actual override suspend fun getPublicKey(): LocalKey =
        LocalKey(
            JSON.parse<JWK>(JSON.stringify(_internalJwk)).apply {
                d = undefined
                p = undefined
                q = undefined
                dp = undefined
                dq = undefined
                qi = undefined
                k = undefined
            }
        ).apply { init() }

    actual override suspend fun getPublicKeyRepresentation(): ByteArray {
        TODO("Not yet implemented")
    }

    override val keyType: KeyType
        get() {
            val k = _internalKey.asDynamic()
            return when {
                k.asymmetricKeyType != undefined -> { // KeyObject (node)
                    when (k.asymmetricKeyType) {
                        "rsa", "rsa-pss" -> KeyType.RSA // see k.asymmetricKeyDetails.modulusLength 2048, 4096 ...; mgf1HashAlgorithm (Name of the message digest used by MGF1 (RSA-PSS))
                        "ec" -> {
                            when (k.asymmetricKeyDetails.namedCurve) {
                                "prime256v1" -> KeyType.secp256r1
                                "secp256k1" -> KeyType.secp256k1
                                else -> TODO("Unsupported EC curve: ${k.asymmetricKeyDetails.namedCurve}")
                            }
                        }

                        "ed25519" -> KeyType.Ed25519

                        "x448", "x25519", "ed448" -> TODO("Unsupported asymmetricKeyType: ${k.asymmetricKeyType}")
                        else -> throw IllegalArgumentException("Unknown asymmetricKeyType: ${k.asymmetricKeyType}")
                    }
                }

                k.algorithm != undefined -> when (k.algorithm.name as String) { // CryptoKey (web)
                    "RSASSA-PKCS1-v1_5", "RSA-PSS", "RSA-OAEP" -> KeyType.RSA // TODO: check k.algorithm.modulusLength: 2048, 4096, ...; k.algorithm.hash: SHA-256, SHA-384, SHA-512
                    "ECDSA", "ECDH" -> KeyType.secp256r1 // TODO: check k.algorithm.namedCurve: P-256, P-384, P-521
                    // other available: AES, HMAC
                    else -> throw IllegalArgumentException("Unsupported algorithm for CryptoKey (web): ${k.algorithm.name}")
                }

                else -> throw IllegalArgumentException("Unable to determine type of KeyLike")
            }
        }

    actual override val hasPrivateKey: Boolean
        get() = _internalKey.type == "private"

    actual override suspend fun getKeyId(): String = _internalJwk.kid ?: getThumbprint()

    actual override suspend fun getThumbprint(): String = await(jose.calculateJwkThumbprint(JSON.parse(exportJWK())))

    actual companion object : LocalKeyCreator {
        actual override suspend fun generate(type: KeyType, metadata: LocalKeyMetadata): LocalKey =
            JsLocalKeyCreator.generate(type, metadata)

        actual override suspend fun importRawPublicKey(type: KeyType, rawPublicKey: ByteArray, metadata: LocalKeyMetadata): Key =
            JsLocalKeyCreator.importRawPublicKey(type, rawPublicKey, metadata)

        actual override suspend fun importJWK(jwk: String): Result<LocalKey> = JsLocalKeyCreator.importJWK(jwk)
        actual override suspend fun importPEM(pem: String): Result<LocalKey> = JsLocalKeyCreator.importPEM(pem)
    }

}
