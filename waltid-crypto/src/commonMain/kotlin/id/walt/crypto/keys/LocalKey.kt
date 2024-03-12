package id.walt.crypto.keys

import kotlinx.serialization.json.JsonObject

expect class LocalKey(jwk: String?) : Key {

    override suspend fun getKeyId(): String

    override suspend fun getThumbprint(): String

    override suspend fun exportJWK(): String

    override suspend fun exportJWKObject(): JsonObject

    override suspend fun exportPEM(): String

    /**
     * Signs as a JWS: Signs a message using this private key (with the algorithm this key is based on)
     * @exception IllegalArgumentException when this is not a private key
     * @param plaintext data to be signed
     * @return signed (JWS)
     */
    override suspend fun signRaw(plaintext: ByteArray): ByteArray
    override suspend fun signJws(plaintext: ByteArray, headers: Map<String, String>): String

    /**
     * Verifies JWS: Verifies a signed message using this public key
     * @param signed signed
     * @return Result wrapping the plaintext; Result failure when the signature fails
     */
    override suspend fun verifyRaw(signed: ByteArray, detachedPlaintext: ByteArray?): Result<ByteArray>
    override suspend fun verifyJws(signedJws: String): Result<JsonObject>
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

    override suspend fun getPublicKey(): LocalKey
    override suspend fun getPublicKeyRepresentation(): ByteArray


    override val hasPrivateKey: Boolean


    companion object : LocalKeyCreator {

        override suspend fun generate(type: KeyType, metadata: LocalKeyMetadata): LocalKey
        override suspend fun importRawPublicKey(type: KeyType, rawPublicKey: ByteArray, metadata: LocalKeyMetadata): Key

        override suspend fun importJWK(jwk: String): Result<LocalKey>

        override suspend fun importPEM(pem: String): Result<LocalKey>
    }

}
