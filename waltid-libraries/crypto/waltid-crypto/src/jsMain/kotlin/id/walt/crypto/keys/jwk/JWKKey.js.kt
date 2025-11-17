package id.walt.crypto.keys.jwk

import JWK
import KeyLike
import WebCrypto
import crypto
import id.walt.crypto.keys.*
import id.walt.crypto.utils.ArrayUtils.toByteArray
import id.walt.crypto.utils.PromiseUtils
import io.ktor.utils.io.core.*
import jose
import kotlinx.coroutines.await
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import love.forte.plugin.suspendtrans.annotation.JsPromise
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.Uint8Array
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.js.json

@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable
@SerialName("jwk")
actual class JWKKey actual constructor(
    @Serializable(with = JWKKeyJsonFieldSerializer::class)
    var jwk: String?,
    val _keyId: String?
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
                _internalKey = PromiseUtils.await(jose.importJWK(JSON.parse(jwkToInit!!)))
            } else if (this::_internalJwk.isInitialized) {
                _internalKey = PromiseUtils.await(jose.importJWK(_internalJwk))
            }
        }

        if (!this::_internalJwk.isInitialized) {
            if (jwkToInit != null) {
                _internalJwk = JSON.parse(jwkToInit!!)
                if (_internalJwk.kid == null) {
                    _internalJwk.kid = getThumbprint()
                }
            } else {
                _internalJwk = PromiseUtils.await(jose.exportJWK(_internalKey))

                if (jwk == null) {
                    jwk = JSON.stringify(_internalJwk)
                }
            }
        }

        if (jwkToInit != null) {
            jwkToInit = null
        }
    }

    @JsName("jwkKeyUsingKeyLike")
    constructor(key: KeyLike) : this(null) {
        _internalKey = key
    }

    @JsName("jwkKeyUsingKeyLikeAndJWK")
    constructor(key: KeyLike, jwk: JWK) : this(null) {
        _internalKey = key
        _internalJwk = jwk
    }

    @JsName("jwkKeyUsingJWK")
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
            hasPrivateKey -> PromiseUtils.await(jose.exportPKCS8(_internalKey))
            else -> PromiseUtils.await(jose.exportSPKI(_internalKey))
        }
    }


    private fun getSignatureAlgorithmParams(keyType: KeyType): dynamic {
        return when (keyType) {
            // For Ed25519, the algorithm is just a string
            KeyType.Ed25519 -> "Ed25519"

            // For ECDSA, it's an object with name and curve
            KeyType.secp256r1 -> js("{ name: 'ECDSA', namedCurve: 'P-256' }")
            KeyType.secp384r1 -> js("{ name: 'ECDSA', namedCurve: 'P-384' }")
            KeyType.secp521r1 -> js("{ name: 'ECDSA', namedCurve: 'P-521' }")

            // For RSA, it's an object with name and hash
            KeyType.RSA, KeyType.RSA3072, KeyType.RSA4096 ->
                js("{ name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }")

            else -> throw IllegalArgumentException("Unsupported key type for Web Crypto: $keyType")
        }
    }

    private fun getSigningAlgorithmParams(keyType: KeyType): dynamic {
        return when (keyType) {
            // For ECDSA signing, specify the hash
            KeyType.secp256r1 -> js("{ name: 'ECDSA', hash: 'SHA-256' }")
            KeyType.secp384r1 -> js("{ name: 'ECDSA', hash: 'SHA-384' }")
            KeyType.secp521r1 -> js("{ name: 'ECDSA', hash: 'SHA-512' }")

            // Otherwise, it's the same as the import algorithm
            else -> getSignatureAlgorithmParams(keyType)
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun parsePemToBinary(pem: String): ArrayBuffer {
        // 1. Remove header, footer, and line breaks
        val pemBody = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")

        // 2. Base64 decode to a ByteArray
        val binaryDer = Base64.decode(pemBody)

        // 3. Convert to ArrayBuffer for the Web Crypto API
        return (binaryDer as Int8Array).buffer
    }

    /* FIXME: annotation @JsPromise

    error:
    e: waltid-crypto/src/jsMain/kotlin/id/walt/crypto/keys/jwk/JWKKey.js.kt:30:14 '@OptIn(...) @JsExport() @Serializable() @SerialName(...) actual class JWKKey : Key' has no corresponding members for expected class members:

    @JvmBlocking() @JvmAsync() @JsPromise() @Api4Js() fun signRawAsync(plaintext: ByteArray): Promise<out Any>

    The following declaration is incompatible because return type is different:
        @JsPromise() @Api4Js() actual fun signRawAsync(plaintext: ByteArray): Promise<out ByteArray>
     */
    @JsExport.Ignore
    actual override suspend fun signRaw(plaintext: ByteArray, customSignatureAlgorithm: String?): ByteArray {
        check(hasPrivateKey) { "No private key is attached to this key!" }
        // 1. Get the PEM-encoded private key string
        val pemString = exportPEM()

        // 2. Parse the PEM string into a raw binary format (ArrayBuffer)
        val privateKeyData = parsePemToBinary(pemString)

        // 3. Get the correct algorithm parameters for your key type
        val importAlgorithm = getSignatureAlgorithmParams(keyType)

        // 4. Import the key to get a CryptoKey object
        val cryptoKey = WebCrypto.subtle.importKey(
            "pkcs8",             // Private key format
            privateKeyData,      // The raw key data
            importAlgorithm,     // Algorithm details
            true,                // Whether the key can be exported
            arrayOf("sign")      // What we want to use the key for
        ).await()

        // 5. Sign the data using the imported CryptoKey
        val signingAlgorithm = getSigningAlgorithmParams(keyType)
        val signatureBuffer = WebCrypto.subtle.sign(
            signingAlgorithm,
            cryptoKey,
            Uint8Array(plaintext.toTypedArray())
        ).await()

        // 6. Convert the resulting ArrayBuffer back to a Kotlin ByteArray
        return Int8Array(signatureBuffer).unsafeCast<ByteArray>()
    }

    /**
     * Signs as a JWS: Signs a message using this private key (with the algorithm this key is based on)
     * @exception IllegalArgumentException when this is not a private key
     * @param plaintext data to be signed
     * @return signed (JWS)
     */
    @JsPromise
    @JsExport.Ignore
    actual override suspend fun signJws(plaintext: ByteArray, headers: Map<String, JsonElement>): String {
        check(hasPrivateKey) { "No private key is attached to this key!" }

        val headerEntries = headers.entries.toTypedArray().map { it.toPair() }.toTypedArray()

        return PromiseUtils.await(
            jose.CompactSign(Uint8Array(plaintext.toTypedArray()))
                .setProtectedHeader(json("alg" to keyType.jwsAlg, *headerEntries))
                .sign(_internalKey)
        )
    }

    private data class WebCryptoParams(val importAlgorithm: dynamic, val signAlgorithm: dynamic)


    private fun getVerificationWebCryptoParams(): WebCryptoParams {
        return when (keyType) {
            KeyType.secp256r1 -> WebCryptoParams(
                importAlgorithm = js("{ name: 'ECDSA', namedCurve: 'P-256' }"),
                signAlgorithm = js("{ name: 'ECDSA', hash: 'SHA-256' }")
            )

            KeyType.secp384r1 -> WebCryptoParams(
                importAlgorithm = js("{ name: 'ECDSA', namedCurve: 'P-384' }"),
                signAlgorithm = js("{ name: 'ECDSA', hash: 'SHA-384' }")
            )

            KeyType.secp521r1 -> WebCryptoParams(
                importAlgorithm = js("{ name: 'ECDSA', namedCurve: 'P-521' }"),
                signAlgorithm = js("{ name: 'ECDSA', hash: 'SHA-512' }")
            )

            KeyType.RSA -> WebCryptoParams(
                importAlgorithm = js("{ name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }"),
                signAlgorithm = js("{ name: 'RSASSA-PKCS1-v1_5' }")
            )

            KeyType.RSA3072 -> WebCryptoParams(
                importAlgorithm = js("{ name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-384' }"),
                signAlgorithm = js("{ name: 'RSASSA-PKCS1-v1_5' }")
            )

            KeyType.RSA4096 -> WebCryptoParams(
                importAlgorithm = js("{ name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-512' }"),
                signAlgorithm = js("{ name: 'RSASSA-PKCS1-v1_5' }")
            )

            else -> throw IllegalArgumentException("Unsupported algorithm for Javascript verification: $keyType")
        }
    }

    @JsPromise
    @JsExport.Ignore
    actual override suspend fun verifyRaw(
        signed: ByteArray,
        detachedPlaintext: ByteArray?,
        customSignatureAlgorithm: String?
    ): Result<ByteArray> {
        /*runCatching {
            require(detachedPlaintext != null) { "Detached plaintext cannot be null." }

            // Assume a method exists to get the public key in PEM format.
            val pemString = exportPEM()

            // 1. Parse the public key from PEM format to a raw binary ArrayBuffer.
            val publicKeyData = parsePemToBinary(pemString)

            // 2. Get the algorithm parameters required by the Web Crypto API.
            val jwsAlgorithm = keyType.jwsAlg
            val cryptoParams = getVerificationWebCryptoParams()

            // 3. Import the public key to create a CryptoKey object for verification.
            val cryptoKey = WebCrypto.subtle.importKey(
                "spki",              // Public key format for public keys
                publicKeyData,       // The raw key data
                cryptoParams.importAlgorithm,
                true,
                arrayOf("verify")    // Specify that this key will be used for verification.
            ).await()

            // 4. IMPORTANT: Ensure the signature is in the IEEE P1363 format required by `subtle.verify`.
            val p1363Signature = convertDERtoIEEEP1363(signed)
            val signatureBuffer = (p1363Signature as Int8Array).buffer

            // 5. Call `subtle.verify` with the key, signature, and original data.
            val isValid = WebCrypto.subtle.verify(
                cryptoParams.signAlgorithm,
                cryptoKey,
                signatureBuffer,
                Uint8Array(detachedPlaintext.toTypedArray())
            ).await()

            // 6. Check the result and return the plaintext or throw an exception.
            if (isValid) {
                detachedPlaintext
            } else {
                throw IllegalArgumentException("Signature verification failed.")
            }*/


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
     * @param signedJws signed
     * @return Result wrapping the plaintext; Result failure when the signature fails
     */
    @JsPromise
    @JsExport.Ignore
    actual override suspend fun verifyJws(signedJws: String): Result<JsonElement> =
        runCatching {
            Json.parseToJsonElement(
                PromiseUtils.await(jose.compactVerify(signedJws, _internalKey)).payload.toByteArray().decodeToString()
            ).jsonObject
        }

    /* FIXME: add annotation @JsPromise
    e: waltid-identity/waltid-libraries/waltid-crypto/src/jsMain/kotlin/id/walt/crypto/keys/jwk/JWKKey.js.kt:30:14 '@OptIn(...) @JsExport() @Serializable() @SerialName(...) actual class JWKKey : Key' has no corresponding members for expected class members:

    @JvmBlocking() @JvmAsync() @JsPromise() @Api4Js() fun getPublicKeyAsync(): Promise<out Key>

    The following declaration is incompatible because return type is different:
        @JsPromise() @Api4Js() actual fun getPublicKeyAsync(): Promise<out JWKKey>
     */
    @JsExport.Ignore
    actual override suspend fun getPublicKey(): JWKKey =
        JWKKey(
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

    /* FIXME: add annotation: @JsPromise
    e: waltid-identity/waltid-libraries/waltid-crypto/src/jsMain/kotlin/id/walt/crypto/keys/jwk/JWKKey.js.kt:30:14 '@OptIn(...) @JsExport() @Serializable() @SerialName(...) actual class JWKKey : Key' has no corresponding members for expected class members:

    @JvmBlocking() @JvmAsync() @JsPromise() @Api4Js() fun getMetaAsync(): Promise<out KeyMeta>

    The following declaration is incompatible because return type is different:
        @JsPromise() @Api4Js() actual fun getMetaAsync(): Promise<out JwkKeyMeta>
     */
    @JsExport.Ignore
    actual override suspend fun getMeta(): JwkKeyMeta = JwkKeyMeta(getKeyId())

    @JsExport.Ignore
    actual override suspend fun deleteKey() = true

    actual override val keyType: KeyType
        get() {
            val k = _internalKey.asDynamic()
            return when {
                k.asymmetricKeyType != undefined -> { // KeyObject (node)
                    when (k.asymmetricKeyType) {
                        "rsa", "rsa-pss" -> {
                            // see k.asymmetricKeyDetails.modulusLength 2048, 4096 ...; mgf1HashAlgorithm (Name of the message digest used by MGF1 (RSA-PSS))
                            when (val modulusLength = k.asymmetricKeyDetails.modulusLength) {
                                2048 -> KeyType.RSA
                                3072 -> KeyType.RSA3072
                                4096 -> KeyType.RSA4096
                                else -> error("Unknown RSA key length: $modulusLength")
                            }
                        }

                        "ec" -> {
                            when (k.asymmetricKeyDetails.namedCurve) {
                                "prime256v1", "secp256r1" -> KeyType.secp256r1
                                "prime384v1", "secp384r1" -> KeyType.secp384r1
                                "prime521v1", "secp521r1" -> KeyType.secp521r1
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
                    "ECDSA", "ECDH" -> KeyTypes.EC_KEYS.firstOrNull { it.jwkCurve == (k.algorithm.namedCurve as String) }
                        ?: throw IllegalArgumentException("Unknown ECDSA key algorithm name: ${k.algorithm.name}. Supported: ${KeyTypes.EC_KEYS.joinToString { it.jwkCurve ?: "?" }}")
                    // other available: AES, HMAC
                    else -> throw IllegalArgumentException("Unsupported algorithm for CryptoKey (web): ${k.algorithm.name}")
                }

                else -> throw IllegalArgumentException("Unable to determine type of KeyLike")
            }
        }

    actual override val hasPrivateKey: Boolean
        get() = check(this::_internalKey.isInitialized) { "_internalKey of JWKKey.js.kt is not initialized (tried to to private key operation?) - has init() be called on key?" }
            .run { _internalKey.type == "private" }


    @JsPromise
    @JsExport.Ignore
    actual override suspend fun getKeyId(): String = _keyId ?: _internalJwk.kid ?: getThumbprint()

    @JsPromise
    @JsExport.Ignore
    actual override suspend fun getThumbprint(): String =
        PromiseUtils.await(jose.calculateJwkThumbprint(JSON.parse(exportJWK())))

    actual companion object : JWKKeyCreator() {
        @JsPromise
        @JsExport.Ignore
        actual override suspend fun generate(type: KeyType, metadata: JwkKeyMeta?): JWKKey =
            JsJWKKeyCreator.generate(type, metadata)

        @JsPromise
        @JsExport.Ignore
        actual override suspend fun importRawPublicKey(
            type: KeyType,
            rawPublicKey: ByteArray,
            metadata: JwkKeyMeta?,
        ): Key =
            JsJWKKeyCreator.importRawPublicKey(type, rawPublicKey, metadata)

        @JsPromise
        @JsExport.Ignore
        actual override suspend fun importJWK(jwk: String): Result<JWKKey> = JsJWKKeyCreator.importJWK(jwk)

        @JsPromise
        @JsExport.Ignore
        actual override suspend fun importPEM(pem: String): Result<JWKKey> = JsJWKKeyCreator.importPEM(pem)
    }

    @JsPromise
    @JsExport.Ignore
    actual suspend fun decryptJwe(jweString: String): ByteArray {
        TODO("Not yet implemented")
    }

    @JsPromise
    @JsExport.Ignore
    actual suspend fun encryptJwe(plaintext: ByteArray): String {
        TODO("Not yet implemented")
    }

}
