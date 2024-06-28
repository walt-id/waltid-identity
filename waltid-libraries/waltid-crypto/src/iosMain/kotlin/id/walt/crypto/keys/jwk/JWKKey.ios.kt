package id.walt.crypto.keys.jwk

import id.walt.crypto.keys.JwkKeyMeta
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

actual class JWKKey actual constructor(private val jwk: String?) : Key() {

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
    actual override val keyType: KeyType
        get() = TODO("Not yet implemented")

    actual override suspend fun getKeyId(): String {
        return Json.parseToJsonElement(jwk!!).jsonObject["kid"]?.jsonPrimitive?.content
            ?: throw IllegalStateException("JWK does not contain kid")
    }

    actual override suspend fun getThumbprint(): String {
        TODO("Not yet implemented")
    }

    actual override suspend fun exportJWK(): String {
        return requireNotNull(jwk){"exportJWK ios"}
    }

    actual override suspend fun exportJWKObject(): JsonObject {
        return Json.parseToJsonElement(exportJWK()).jsonObject
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
        TODO("Not yet implemented")
    }

    actual override suspend fun signJws(
        plaintext: ByteArray,
        headers: Map<String, String>
    ): String {
        TODO("Not yet implemented")
    }

    /**
     * Verifies JWS: Verifies a signed message using this public key
     * @param signed signed
     * @return Result wrapping the plaintext; Result failure when the signature fails
     */
    actual override suspend fun verifyRaw(
        signed: ByteArray,
        detachedPlaintext: ByteArray?
    ): Result<ByteArray> {
        TODO("Not yet implemented")
    }

    actual override suspend fun verifyJws(signedJws: String): Result<JsonElement> {
        TODO("Not yet implemented")
    }

    actual override suspend fun getPublicKey(): JWKKey {
        TODO("Not yet implemented")
    }

    actual override suspend fun getPublicKeyRepresentation(): ByteArray {
        TODO("Not yet implemented")
    }

    actual override suspend fun getMeta(): JwkKeyMeta {
        TODO("Not yet implemented")
    }

    actual override val hasPrivateKey: Boolean
        get() = TODO("Not yet implemented")

    actual companion object : JWKKeyCreator {
        actual override suspend fun generate(
            type: KeyType,
            metadata: JwkKeyMeta?
        ): JWKKey {
            TODO("Not yet implemented")
        }

        actual override suspend fun importRawPublicKey(
            type: KeyType,
            rawPublicKey: ByteArray,
            metadata: JwkKeyMeta?
        ): Key {
            TODO("Not yet implemented")
        }

        actual override suspend fun importJWK(jwk: String): Result<JWKKey> {
            return Result.success(JWKKey(jwk))
        }

        actual override suspend fun importPEM(pem: String): Result<JWKKey> {
            TODO("Not yet implemented")
        }

    }


}