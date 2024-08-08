package id.walt.crypto.keys.jwk

import id.walt.crypto.keys.JwkKeyMeta
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

class AndroidJWKKey constructor(jwk: String?) : Key() {

    /*
    /**
     * Encrypts as JWE: Encrypts a message using this public key (with the algorithm this key is based on)
     * @exception IllegalArgumentException when this is not a private key, when this algorithm does not support encryption
     * @param plaintext data to be encrypted
     * @return encrypted (JWE)
     */
    override suspend fun encrypt(plaintext: ByteArray): String

    /**
     * Decrypts JWE: Decrypts an encrypted message using this private key
     * @param encrypted encrypted
     * @return Result wrapping the plaintext; Result failure when the decryption fails
     */
    override suspend fun decrypt(encrypted: ByteArray): Result<ByteArray>
     */
    override suspend fun getKeyId(): String {
        TODO("Not yet implemented")
    }

    override suspend fun getThumbprint(): String {
        TODO("Not yet implemented")
    }

    override suspend fun exportJWK(): String {
        TODO("Not yet implemented")
    }

    override suspend fun exportJWKObject(): JsonObject {
        TODO("Not yet implemented")
    }

    override suspend fun exportPEM(): String {
        TODO("Not yet implemented")
    }

    /**
     * Signs as a JWS: Signs a message using this private key (with the algorithm this key is based on)
     * @exception IllegalArgumentException when this is not a private key
     * @param plaintext data to be signed
     * @return signed (JWS)
     */
    override suspend fun signRaw(plaintext: ByteArray): ByteArray {
        TODO("Not yet implemented")
    }

    override suspend fun signJws(plaintext: ByteArray, headers: Map<String, String>): String {
        TODO("Not yet implemented")
    }

    /**
     * Verifies JWS: Verifies a signed message using this public key
     * @param signed signed
     * @return Result wrapping the plaintext; Result failure when the signature fails
     */
    override suspend fun verifyRaw(signed: ByteArray, detachedPlaintext: ByteArray?): Result<ByteArray> {
        TODO("Not yet implemented")
    }

    override suspend fun verifyJws(signedJws: String): Result<JsonElement> {
        TODO("Not yet implemented")
    }

    override suspend fun getPublicKey(): JWKKey {
        TODO("Not yet implemented")
    }

    override suspend fun getPublicKeyRepresentation(): ByteArray {
        TODO("Not yet implemented")
    }

    override suspend fun getMeta(): JwkKeyMeta {
        TODO("Not yet implemented")
    }

    override val keyType: KeyType
        get() = TODO("Not yet implemented")

    override val hasPrivateKey: Boolean
        get() = TODO("Not yet implemented")

    companion object : JWKKeyCreator {
        override suspend fun generate(
            type: KeyType,
            metadata: JwkKeyMeta?
        ): JWKKey {
            TODO("Not yet implemented")
        }

        override suspend fun importRawPublicKey(
            type: KeyType,
            rawPublicKey: ByteArray,
            metadata: JwkKeyMeta?
        ): Key {
            TODO("Not yet implemented")
        }

        override suspend fun importJWK(jwk: String): Result<JWKKey> {
            TODO("Not yet implemented")
        }

        override suspend fun importPEM(pem: String): Result<JWKKey> {
            TODO("Not yet implemented")
        }

    }


}
