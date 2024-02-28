package id.walt.crypto.keys

import JWK
import KeyLike
import crypto
import id.walt.crypto.utils.ArrayUtils.toByteArray
import id.walt.crypto.utils.JwsUtils.jwsAlg
import id.walt.crypto.utils.PromiseUtils.await
import io.ktor.utils.io.core.*
import jose
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import love.forte.plugin.suspendtrans.annotation.JsPromise
import org.khronos.webgl.Uint8Array
import kotlin.js.json

@OptIn(ExperimentalJsExport::class)
@JsExport
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

    @JsPromise
    @JsExport.Ignore
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
    }

    @JsName("localKeyUsingKeyLike")
    constructor(key: KeyLike) : this(null) {
        _internalKey = key
    }

    @JsName("localKeyUsingKeyLikeAndJWK")
    constructor(key: KeyLike, jwk: JWK) : this(null) {
        _internalKey = key
        _internalJwk = jwk
    }

    @JsName("localKeyUsingJWK")
    constructor(jwk: JWK) : this(null) {
        _internalJwk = jwk
    }

    @JsPromise
    @JsExport.Ignore
    actual override suspend fun exportJWK(): String = JSON.stringify(_internalJwk)

    @JsPromise
    @JsExport.Ignore
    override suspend fun exportJWKPretty(): String = JSON.stringify(_internalJwk, null, 4)

    @JsPromise
    @JsExport.Ignore
    actual override suspend fun exportJWKObject(): JsonObject {
        return Json.parseToJsonElement(exportJWK()).jsonObject
    }

    @JsPromise
    @JsExport.Ignore
    actual override suspend fun exportPEM(): String {
        return when {
            hasPrivateKey -> await(jose.exportPKCS8(_internalKey))
            else -> await(jose.exportSPKI(_internalKey))
        }
    }

    @JsPromise
    @JsExport.Ignore
    actual override suspend fun signRaw(plaintext: ByteArray): ByteArray {
        check(hasPrivateKey) { "No private key is attached to this key!" }
        return crypto.sign(
            when (keyType) {
                KeyType.Ed25519 -> null
                else -> "sha256"
            },
            plaintext,
            exportPEM()
        )
    }

    /**
     * Signs as a JWS: Signs a message using this private key (with the algorithm this key is based on)
     * @exception IllegalArgumentException when this is not a private key
     * @param plaintext data to be signed
     * @return signed (JWS)
     */
    @JsPromise
    @JsExport.Ignore
    actual override suspend fun signJws(plaintext: ByteArray, headers: Map<String, String>): String {
        check(hasPrivateKey) { "No private key is attached to this key!" }

        val headerEntries = headers.entries.toTypedArray().map { it.toPair() }.toTypedArray()

        return await(
            jose.CompactSign(Uint8Array(plaintext.toTypedArray()))
                .setProtectedHeader(json("alg" to keyType.jwsAlg(), *headerEntries))
                .sign(_internalKey)
        )
    }

    @JsPromise
    @JsExport.Ignore
    actual override suspend fun verifyRaw(signed: ByteArray, detachedPlaintext: ByteArray?): Result<ByteArray> {
        return runCatching {
            val verified = crypto.verify(
                when (keyType) {
                    KeyType.Ed25519 -> null
                    else -> "sha256"
                },
                detachedPlaintext ?: signed,
                getPublicKey().exportPEM(),
                signed
            )
            if (verified) {
                "true".toByteArray()
            } else {
                throw IllegalArgumentException("Signature verification failed")
            }
        }
    }

    /**
     * Verifies JWS: Verifies a signed message using this public key
     * @param signed signed
     * @return Result wrapping the plaintext; Result failure when the signature fails
     */
    @JsPromise
    @JsExport.Ignore
    actual override suspend fun verifyJws(signedJws: String): Result<JsonObject> =
        runCatching {
            Json.parseToJsonElement(
                await(jose.compactVerify(signedJws, _internalKey)).payload.toByteArray().decodeToString()
            ).jsonObject
        }

    @JsPromise
    @JsExport.Ignore
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

    @JsPromise
    @JsExport.Ignore
    actual override suspend fun getPublicKeyRepresentation(): ByteArray {
        return getPublicKey().exportPEM().toByteArray()
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
        get() = check(this::_internalKey.isInitialized) { "_internalKey of LocalKey.js.kt is not initialized (tried to to private key operation?) - has init() be called on key?" }
            .run { _internalKey.type == "private" }


    @JsPromise
    @JsExport.Ignore
    actual override suspend fun getKeyId(): String = _internalJwk.kid ?: getThumbprint()

    @JsPromise
    @JsExport.Ignore
    actual override suspend fun getThumbprint(): String = await(jose.calculateJwkThumbprint(JSON.parse(exportJWK())))

    actual companion object : LocalKeyCreator {
        @JsPromise
        @JsExport.Ignore
        actual override suspend fun generate(type: KeyType, metadata: LocalKeyMetadata): LocalKey =
            JsLocalKeyCreator.generate(type, metadata)

        @JsPromise
        @JsExport.Ignore
        actual override suspend fun importRawPublicKey(type: KeyType, rawPublicKey: ByteArray, metadata: LocalKeyMetadata): Key =
            JsLocalKeyCreator.importRawPublicKey(type, rawPublicKey, metadata)

        @JsPromise
        @JsExport.Ignore
        actual override suspend fun importJWK(jwk: String): Result<LocalKey> = JsLocalKeyCreator.importJWK(jwk)

        @JsPromise
        @JsExport.Ignore
        actual override suspend fun importPEM(pem: String): Result<LocalKey> = JsLocalKeyCreator.importPEM(pem)
    }

}
