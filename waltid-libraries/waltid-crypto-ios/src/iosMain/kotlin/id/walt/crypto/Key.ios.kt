package id.walt.crypto

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyMeta
import id.walt.crypto.keys.KeyType
import id.walt.target.ios.keys.P256
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
                .let { key -> IosKey(type, true, kid) }

            else -> error("Not implemented")
        }

        @Throws(Exception::class)
        fun load(kid: String, type: KeyType): Key = when (type) {
            KeyType.secp256r1 -> P256.PrivateKey.loadFromKeychain(kid)
                .let { key -> IosKey(type, true, kid) }

            else -> error("Not implemented")
        }

        @Throws(Exception::class)
        fun delete(kid: String, type: KeyType): Unit = when (type) {
            KeyType.secp256r1 -> P256.PrivateKey.deleteFromKeychain(kid)
            else -> error("Not implemented")
        }
    }

    override suspend fun getKeyId(): String = when (keyType) {
        KeyType.secp256r1 -> P256.PrivateKey.loadFromKeychain(kid).kid()!!
        else -> error("Not implemented")
    }


    override suspend fun getThumbprint(): String = when (keyType) {
        KeyType.secp256r1 -> if (hasPrivateKey) P256.PrivateKey.loadFromKeychain(kid)
            .thumbprint() else P256.PrivateKey.loadFromKeychain(kid).publicKey().thumbprint()

        else -> error("Not implemented")
    }

    override suspend fun exportJWK(): String = exportJWKObject().toString()


    override suspend fun exportJWKObject(): JsonObject = when (keyType) {
        KeyType.secp256r1 -> if (hasPrivateKey) P256.PrivateKey.loadFromKeychain(kid)
            .jwk() else P256.PrivateKey.loadFromKeychain(kid).publicKey().jwk()

        else -> error("Not implemented")
    }

    override suspend fun exportPEM(): String = when (keyType) {
        KeyType.secp256r1 -> if (hasPrivateKey) P256.PrivateKey.loadFromKeychain(kid)
            .pem() else P256.PrivateKey.loadFromKeychain(kid).publicKey().pem()

        else -> error("Not implemented")
    }

    override suspend fun signRaw(plaintext: ByteArray): Any {
        check(hasPrivateKey) { "Only private key can do signing." }

        return when (keyType) {
            KeyType.secp256r1 -> P256.PrivateKey.loadFromKeychain(kid).signRaw(plaintext)
            else -> error("Not implemented")
        }
    }

    override suspend fun signJws(plaintext: ByteArray, headers: Map<String, String>): String {
        check(hasPrivateKey) { "Only private key can do signing." }

        return when (keyType) {
            KeyType.secp256r1 -> P256.PrivateKey.loadFromKeychain(kid).signJws(plaintext, headers)
            else -> error("Not implemented")
        }
    }

    override suspend fun verifyRaw(
        signed: ByteArray, detachedPlaintext: ByteArray?
    ): Result<ByteArray> {
        return when (keyType) {
            KeyType.secp256r1 -> P256.PrivateKey.loadFromKeychain(kid).publicKey().verifyRaw(signed, detachedPlaintext!!)
            else -> error("Not implemented")
        }
    }

    override suspend fun verifyJws(signedJws: String): Result<JsonElement> {
        return when (keyType) {
            KeyType.secp256r1 -> P256.PrivateKey.loadFromKeychain(kid).publicKey().verifyJws(signedJws)
            else -> error("Not implemented")
        }
    }

    override suspend fun getPublicKey(): Key {
        return when (keyType) {
            KeyType.secp256r1 -> IosKey(keyType, false, kid)
            else -> error("Not implemented")
        }
    }

    override suspend fun getPublicKeyRepresentation(): ByteArray {
        return when (keyType) {
            KeyType.secp256r1 -> P256.PrivateKey.loadFromKeychain(kid).publicKey().externalRepresentation()
            else -> error("Not implemented")
        }
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