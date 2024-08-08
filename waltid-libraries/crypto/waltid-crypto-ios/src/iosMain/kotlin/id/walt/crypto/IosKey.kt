package id.walt.crypto

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyMeta
import id.walt.crypto.keys.KeyType
import id.walt.target.ios.keys.Ed25519
import id.walt.target.ios.keys.P256
import id.walt.target.ios.keys.RSA
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Suppress("unused")
class IosKey(
    override val keyType: KeyType, override val hasPrivateKey: Boolean, private val kid: String
) : Key() {
    companion object {
        @Throws(Exception::class)
        fun create(kid: String, type: KeyType): Key = when (type) {
            KeyType.secp256r1 -> P256.PrivateKey.createInKeychain(kid)
            KeyType.Ed25519 -> Ed25519.PrivateKey.createInKeychain(kid)
            KeyType.RSA -> RSA.PrivateKey.createInKeychain(kid, 2048u)

            else -> error("Not implemented")
        }.let { key -> IosKey(type, true, kid) }

        @Throws(Exception::class)
        fun load(kid: String, type: KeyType): Key = when (type) {
            KeyType.secp256r1 -> P256.PrivateKey.loadFromKeychain(kid)
            KeyType.Ed25519 -> Ed25519.PrivateKey.loadFromKeychain(kid)
            KeyType.RSA -> RSA.PrivateKey.loadFromKeychain(kid)
            else -> error("Not implemented")
        }.let { key -> IosKey(type, true, kid) }

        @Throws(Exception::class)
        fun delete(kid: String, type: KeyType): Unit = when (type) {
            KeyType.secp256r1 -> P256.PrivateKey.deleteFromKeychain(kid)
            KeyType.Ed25519 -> Ed25519.PrivateKey.deleteFromKeychain(kid)
            KeyType.RSA -> RSA.PrivateKey.deleteFromKeychain(kid)
            else -> error("Not implemented")
        }
    }

    override suspend fun getKeyId(): String = when (keyType) {
        KeyType.secp256r1 -> P256.PrivateKey.loadFromKeychain(kid)
        KeyType.Ed25519 -> Ed25519.PrivateKey.loadFromKeychain(kid)
        KeyType.RSA -> RSA.PrivateKey.loadFromKeychain(kid)
        else -> error("Not implemented")
    }.kid()!!


    override suspend fun getThumbprint(): String = when (keyType) {
        KeyType.secp256r1 -> if (hasPrivateKey) P256.PrivateKey.loadFromKeychain(kid)
        else P256.PrivateKey.loadFromKeychain(kid).publicKey()

        KeyType.Ed25519 -> if (hasPrivateKey) Ed25519.PrivateKey.loadFromKeychain(kid)
        else Ed25519.PrivateKey.loadFromKeychain(kid).publicKey()

        KeyType.RSA -> if (hasPrivateKey) RSA.PrivateKey.loadFromKeychain(kid) else
            RSA.PrivateKey.loadFromKeychain(kid).publicKey()

        else -> error("Not implemented")
    }.thumbprint()

    override suspend fun exportJWK(): String = exportJWKObject().toString()


    override suspend fun exportJWKObject(): JsonObject = when (keyType) {
        KeyType.secp256r1 -> if (hasPrivateKey) P256.PrivateKey.loadFromKeychain(kid)
        else P256.PrivateKey.loadFromKeychain(kid).publicKey()

        KeyType.Ed25519 -> if (hasPrivateKey) Ed25519.PrivateKey.loadFromKeychain(kid)
        else Ed25519.PrivateKey.loadFromKeychain(kid).publicKey()

        KeyType.RSA -> if (hasPrivateKey) RSA.PrivateKey.loadFromKeychain(kid)
        else RSA.PrivateKey.loadFromKeychain(kid).publicKey()

        else -> error("Not implemented")
    }.jwk()

    override suspend fun exportPEM(): String = when (keyType) {
        KeyType.secp256r1 -> if (hasPrivateKey) P256.PrivateKey.loadFromKeychain(kid)
        else P256.PrivateKey.loadFromKeychain(kid).publicKey()

        KeyType.Ed25519 -> if (hasPrivateKey) Ed25519.PrivateKey.loadFromKeychain(kid)
        else Ed25519.PrivateKey.loadFromKeychain(kid).publicKey()

        KeyType.RSA -> if (hasPrivateKey) RSA.PrivateKey.loadFromKeychain(kid)
        else RSA.PrivateKey.loadFromKeychain(kid).publicKey()

        else -> error("Not implemented")
    }.pem()

    override suspend fun signRaw(plaintext: ByteArray): Any {
        check(hasPrivateKey) { "Only private key can do signing." }

        return when (keyType) {
            KeyType.secp256r1 -> P256.PrivateKey.loadFromKeychain(kid)
            KeyType.Ed25519 -> Ed25519.PrivateKey.loadFromKeychain(kid)
            KeyType.RSA -> RSA.PrivateKey.loadFromKeychain(kid)
            else -> error("Not implemented")
        }.signRaw(plaintext)
    }

    override suspend fun signJws(plaintext: ByteArray, headers: Map<String, String>): String {
        check(hasPrivateKey) { "Only private key can do signing." }

        return when (keyType) {
            KeyType.secp256r1 -> P256.PrivateKey.loadFromKeychain(kid)
            KeyType.Ed25519 -> Ed25519.PrivateKey.loadFromKeychain(kid)
            KeyType.RSA -> RSA.PrivateKey.loadFromKeychain(kid)
            else -> error("Not implemented")
        }.signJws(plaintext, headers)
    }

    override suspend fun verifyRaw(
        signed: ByteArray, detachedPlaintext: ByteArray?
    ): Result<ByteArray> {
        return when (keyType) {
            KeyType.secp256r1 -> P256.PrivateKey.loadFromKeychain(kid).publicKey()
            KeyType.Ed25519 -> Ed25519.PrivateKey.loadFromKeychain(kid).publicKey()
            KeyType.RSA -> RSA.PrivateKey.loadFromKeychain(kid).publicKey()
            else -> error("Not implemented")
        }.verifyRaw(signed, detachedPlaintext!!)
    }

    override suspend fun verifyJws(signedJws: String): Result<JsonElement> {
        return when (keyType) {
            KeyType.secp256r1 -> P256.PrivateKey.loadFromKeychain(kid).publicKey()
            KeyType.Ed25519 -> Ed25519.PrivateKey.loadFromKeychain(kid).publicKey()
            KeyType.RSA -> RSA.PrivateKey.loadFromKeychain(kid).publicKey()
            else -> error("Not implemented")
        }.verifyJws(signedJws)
    }

    override suspend fun getPublicKey(): Key = IosKey(keyType, false, kid)

    override suspend fun getPublicKeyRepresentation(): ByteArray {
        return when (keyType) {
            KeyType.secp256r1 -> P256.PrivateKey.loadFromKeychain(kid).publicKey()
            KeyType.Ed25519 -> Ed25519.PrivateKey.loadFromKeychain(kid).publicKey()
            KeyType.RSA -> RSA.PrivateKey.loadFromKeychain(kid).publicKey()
            else -> error("Not implemented")
        }.externalRepresentation()
    }

    override suspend fun getMeta(): KeyMeta {
        error("Not yet implemented")
    }
}

// utility functions for swift
fun String.ExportedToByteArray(
    startIndex: Int, endIndex: Int, throwOnInvalidSequence: Boolean
): ByteArray {
    return this.encodeToByteArray()
}

fun ByteArray.ExportedToString(
): String {
    return this.decodeToString()
}