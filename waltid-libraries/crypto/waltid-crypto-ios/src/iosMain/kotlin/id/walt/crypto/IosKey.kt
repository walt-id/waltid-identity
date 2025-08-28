package id.walt.crypto

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyMeta
import id.walt.crypto.keys.KeyType
import id.walt.target.ios.keys.Ed25519
import id.walt.target.ios.keys.P256
import id.walt.target.ios.keys.RSA
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Suppress("unused")
class IosKey private constructor(
    private val options: Options,
    override val hasPrivateKey: Boolean = false
) : Key() {

    class Options @OptIn(ExperimentalUuidApi::class) constructor(
        val kid: String = Uuid.random().toString(),
        val keyType: KeyType,
        val inSecureElement: Boolean = false
    ) {
        init {
            if (inSecureElement) {
                require(keyType == KeyType.secp256r1) { "kid: $kid, Error: Only KeyType.secp256r1 can be stored in secure element." }
            }
        }
    }

    companion object {
        @Throws(Exception::class)
        fun create(options: Options): Key = when (options.keyType) {
            KeyType.secp256r1 -> P256.PrivateKey.createInKeychain(
                options.kid,
                options.inSecureElement
            )

            KeyType.Ed25519 -> Ed25519.PrivateKey.createInKeychain(options.kid)
            KeyType.RSA -> RSA.PrivateKey.createInKeychain(options.kid, 2048u)
            else -> error("Not implemented key type ${options.keyType}")
        }.let { _ -> IosKey(options, true) }

        @Throws(Exception::class)
        fun load(options: Options): Key = when (options.keyType) {
            KeyType.secp256r1 -> P256.PrivateKey.loadFromKeychain(
                options.kid,
                options.inSecureElement
            )

            KeyType.Ed25519 -> Ed25519.PrivateKey.loadFromKeychain(options.kid)
            KeyType.RSA -> RSA.PrivateKey.loadFromKeychain(options.kid)
            else -> error("Not implemented key type ${options.keyType}")
        }.let { _ -> IosKey(options, true) }

        @Throws(Exception::class)
        fun delete(kid: String, type: KeyType): Unit = when (type) {
            KeyType.secp256r1 -> P256.PrivateKey.deleteFromKeychain(kid)
            KeyType.Ed25519 -> Ed25519.PrivateKey.deleteFromKeychain(kid)
            KeyType.RSA -> RSA.PrivateKey.deleteFromKeychain(kid)
            else -> error("Not implemented")
        }
    }

    override val keyType
        get() = options.keyType

    override suspend fun getKeyId(): String = when (options.keyType) {
        KeyType.secp256r1 -> P256.PrivateKey.loadFromKeychain(options.kid, options.inSecureElement)
        KeyType.Ed25519 -> Ed25519.PrivateKey.loadFromKeychain(options.kid)
        KeyType.RSA -> RSA.PrivateKey.loadFromKeychain(options.kid)
        else -> error("Not implemented key type ${options.keyType}")

    }.kid()!!

    override suspend fun getThumbprint(): String = when (options.keyType) {
        KeyType.secp256r1 -> P256.PrivateKey.loadFromKeychain(options.kid, options.inSecureElement)
            .let {
                if (hasPrivateKey) {
                    it
                } else {
                    it.publicKey()
                }
            }

        KeyType.Ed25519 -> Ed25519.PrivateKey.loadFromKeychain(options.kid).let {
            if (hasPrivateKey) {
                it
            } else {
                it.publicKey()
            }
        }

        KeyType.RSA -> RSA.PrivateKey.loadFromKeychain(options.kid).let {
            if (hasPrivateKey) {
                it
            } else {
                it.publicKey()
            }
        }

        else -> error("Not implemented key type ${options.keyType}")

    }.thumbprint()

    override suspend fun exportJWK(): String = exportJWKObject().toString()


    override suspend fun exportJWKObject(): JsonObject = when (options.keyType) {
        KeyType.secp256r1 -> P256.PrivateKey.loadFromKeychain(options.kid, options.inSecureElement)
            .let {
                if (hasPrivateKey) {
                    it
                } else {
                    it.publicKey()
                }
            }

        KeyType.Ed25519 -> Ed25519.PrivateKey.loadFromKeychain(options.kid).let {
            if (hasPrivateKey) {
                it
            } else {
                it.publicKey()
            }
        }

        KeyType.RSA -> RSA.PrivateKey.loadFromKeychain(options.kid).let {
            if (hasPrivateKey) {
                it
            } else {
                it.publicKey()
            }
        }

        else -> error("Not implemented key type ${options.keyType}")

    }.jwk()

    override suspend fun exportPEM(): String = when (options.keyType) {
        KeyType.secp256r1 -> P256.PrivateKey.loadFromKeychain(options.kid, options.inSecureElement)
            .let {
                if (hasPrivateKey) {
                    it
                } else {
                    it.publicKey()
                }
            }

        KeyType.Ed25519 -> Ed25519.PrivateKey.loadFromKeychain(options.kid).let {
            if (hasPrivateKey) {
                it
            } else {
                it.publicKey()
            }
        }

        KeyType.RSA -> RSA.PrivateKey.loadFromKeychain(options.kid).let {
            if (hasPrivateKey) {
                it
            } else {
                it.publicKey()
            }
        }

        else -> error("Not implemented key type ${options.keyType}")
    }.pem()

    override suspend fun signRaw(plaintext: ByteArray, customSignatureAlgorithm: String?): Any {
        check(hasPrivateKey) { "Only private key can do signing." }

        return when (options.keyType) {
            KeyType.secp256r1 -> P256.PrivateKey.loadFromKeychain(options.kid, options.inSecureElement)
            KeyType.Ed25519 -> Ed25519.PrivateKey.loadFromKeychain(options.kid)
            KeyType.RSA -> RSA.PrivateKey.loadFromKeychain(options.kid)
            else -> error("Not implemented key type ${options.keyType}")
        }.signRaw(plaintext)
    }

    override suspend fun signJws(plaintext: ByteArray, headers: Map<String, JsonElement>): String {
        check(hasPrivateKey) { "Only private key can do signing." }

        return when (options.keyType) {
                KeyType.secp256r1 -> P256.PrivateKey.loadFromKeychain(options.kid, options.inSecureElement)
                KeyType.Ed25519 -> Ed25519.PrivateKey.loadFromKeychain(options.kid)
                KeyType.RSA -> RSA.PrivateKey.loadFromKeychain(options.kid)
                else -> error("Not implemented key type ${options.keyType}")

        }.signJws(plaintext, headers)
    }

    override suspend fun verifyRaw(
        signed: ByteArray, detachedPlaintext: ByteArray?,
        customSignatureAlgorithm: String?
    ): Result<ByteArray> = when (options.keyType) {
            KeyType.secp256r1 -> P256.PrivateKey.loadFromKeychain(options.kid, options.inSecureElement).publicKey()
            KeyType.Ed25519 -> Ed25519.PrivateKey.loadFromKeychain(options.kid).publicKey()
            KeyType.RSA -> RSA.PrivateKey.loadFromKeychain(options.kid).publicKey()
            else -> error("Not implemented key type ${options.keyType}")
    }.verifyRaw(signed, detachedPlaintext!!)


    override suspend fun verifyJws(signedJws: String): Result<JsonElement> = when (options.keyType) {
            KeyType.secp256r1 -> P256.PrivateKey.loadFromKeychain(options.kid, options.inSecureElement).publicKey()
            KeyType.Ed25519 -> Ed25519.PrivateKey.loadFromKeychain(options.kid).publicKey()
            KeyType.RSA -> RSA.PrivateKey.loadFromKeychain(options.kid).publicKey()
            else -> error("Not implemented key type ${options.keyType}")

    }.verifyJws(signedJws)


    override suspend fun getPublicKey(): Key = IosKey(options, false)

    override suspend fun getPublicKeyRepresentation(): ByteArray = when (options.keyType) {
            KeyType.secp256r1 -> P256.PrivateKey.loadFromKeychain(options.kid, options.inSecureElement).publicKey()
            KeyType.Ed25519 -> Ed25519.PrivateKey.loadFromKeychain(options.kid).publicKey()
            KeyType.RSA -> RSA.PrivateKey.loadFromKeychain(options.kid).publicKey()
            else -> error("Not implemented key type ${options.keyType}")

    }.externalRepresentation()

    override suspend fun getMeta(): KeyMeta {
        error("Not yet implemented")
    }

    override suspend fun deleteKey(): Boolean = kotlin.runCatching {
        when (options.keyType) {
            KeyType.secp256r1 -> P256.PrivateKey.deleteFromKeychain(options.kid)
            KeyType.Ed25519 -> Ed25519.PrivateKey.deleteFromKeychain(options.kid)
            KeyType.RSA -> RSA.PrivateKey.deleteFromKeychain(options.kid)
            else -> error("Not implemented")
        }
    }.isSuccess
}

// utility functions for swift
@Suppress("unused")
fun String.ExportedToByteArray(
    startIndex: Int, endIndex: Int, throwOnInvalidSequence: Boolean
): ByteArray {
    return this.encodeToByteArray()
}

@Suppress("unused")
fun ByteArray.ExportedToString(
): String {
    return this.decodeToString()
}

@Suppress("unused")
fun dictionaryToHeaders(input: Map<String, String>): Map<String, JsonElement> =
    input.mapValues { (_, v) -> JsonPrimitive(v) }

