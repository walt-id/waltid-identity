package id.walt.crypto.keys

import id.walt.crypto.keys.AndroidLocalKeyGenerator.TRANSFORMATION
import kotlinx.serialization.json.JsonObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.SecretKey

actual class LocalKey actual constructor(jwk: String?) : Key() {

    override val keyType: KeyType
        get() = TODO("Not yet implemented")

    actual override val hasPrivateKey: Boolean
        get() = TODO("Not yet implemented")

    private val encryptCipher get() = Cipher.getInstance(TRANSFORMATION).apply {
        init(Cipher.ENCRYPT_MODE, getKey())
    }

    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }

    actual override suspend fun getKeyId(): String {
        TODO("Not yet implemented")
    }

    actual override suspend fun getThumbprint(): String {
        TODO("Not yet implemented")
    }

    actual override suspend fun exportJWK(): String {
        TODO("Not yet implemented")
    }

    actual override suspend fun exportJWKObject(): JsonObject {
        TODO("Not yet implemented")
    }

    actual override suspend fun exportPEM(): String {
        TODO("Not yet implemented")
    }

    /**
     * Signs as a JWS: Signs a message using this private key (with the algorithm this key is based on)
     * @exception IllegalArgumentException when this is not a private key
     * @param plaintext data to be signed
     * @return signed (JWS)
     */
    actual override suspend fun signRaw(plaintext: ByteArray): ByteArray {
        return encryptCipher.doFinal(plaintext)
    }

    actual override suspend fun signJws(plaintext: ByteArray, headers: Map<String, String>): String {
        TODO("Not yet implemented")
    }

    /**
     * Verifies JWS: Verifies a signed message using this public key
     * @param signed signed
     * @return Result wrapping the plaintext; Result failure when the signature fails
     */
    actual override suspend fun verifyRaw(signed: ByteArray, detachedPlaintext: ByteArray?): Result<ByteArray> {
        TODO("Not yet implemented")
    }

    actual override suspend fun verifyJws(signedJws: String): Result<JsonObject> {
        TODO("Not yet implemented")
    }

    actual override suspend fun getPublicKey(): LocalKey {
        TODO("Not yet implemented")
    }

    actual override suspend fun getPublicKeyRepresentation(): ByteArray {
        TODO("Not yet implemented")
    }

    private fun getKey(): SecretKey {
        val existingKey = keyStore.getEntry(AndroidLocalKeyGenerator.KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        val secretKey = existingKey?.secretKey
        println("key - $existingKey")
        println("secret key - $secretKey")
        checkNotNull(secretKey) { "No key exists in KeyStore" }
        return secretKey
    }

    actual companion object : LocalKeyCreator {
        actual override suspend fun generate(
            type: KeyType,
            metadata: LocalKeyMetadata
        ): LocalKey = AndroidLocalKeyGenerator.generate(type, metadata)

        actual override suspend fun importRawPublicKey(
            type: KeyType,
            rawPublicKey: ByteArray,
            metadata: LocalKeyMetadata
        ): Key {
            TODO("Not yet implemented")
        }

        actual override suspend fun importJWK(jwk: String): Result<LocalKey> {
            TODO("Not yet implemented")
        }

        actual override suspend fun importPEM(pem: String): Result<LocalKey> {
            TODO("Not yet implemented")
        }

    }


}