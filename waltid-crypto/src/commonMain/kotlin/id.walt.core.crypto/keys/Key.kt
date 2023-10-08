package id.walt.core.crypto.keys

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonObject

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
abstract class Key {

    abstract val keyType: KeyType
    //abstract val metadata: KeyMetadata

    abstract val hasPrivateKey: Boolean

    abstract suspend fun getKeyId(): String
    abstract suspend fun getThumbprint(): String


    /**
     * export this key as a JWK if possible (check documentation if this algorithm / key type is supported by the JWK spec)
     * @return JWK
     */
    abstract suspend fun exportJWK(): String
    abstract suspend fun exportJWKObject(): JsonObject

    /**
     * export this key as a PEM if supported
     * @return encoded PEM
     */
    abstract suspend fun exportPEM(): String

    /**
     * signs a message using this private key (with the algorithm this key is based on)
     * @exception IllegalArgumentException when this is not a private key
     * @param plaintext data to be signed
     * @return raw signature
     */
    abstract suspend fun signRaw(plaintext: ByteArray): Any
    /**
     * signs a message using this private key (with the algorithm this key is based on)
     * @exception IllegalArgumentException when this is not a private key
     * @param plaintext data to be signed
     * @return JWS
     */
    abstract suspend fun signJws(plaintext: ByteArray, headers: Map<String, String> = emptyMap()): String

    /**
     * verifies a signed message using this public key
     * @param signed signed
     * @return Result wrapping the plaintext; Result failure when the signature fails
     */
    abstract suspend fun verifyRaw(signed: ByteArray, detachedPlaintext: ByteArray? = null): Result<ByteArray>

    abstract suspend fun verifyJws(signedJws: String): Result<JsonObject>

    /*/**
     * encrypts a message using this public key (with the algorithm this key is based on)
     * @exception IllegalArgumentException when this is not a private key, when this algorithm does not support encryption
     * @param plaintext data to be encrypted
     * @return encrypted (type dependent on key)
     */
    abstract suspend fun encrypt(plaintext: ByteArray): Any

    /**
     * decrypts an encrypted message using this private key
     * @param encrypted encrypted
     * @return Result wrapping the plaintext; Result failure when the decryption fails
     */
    abstract suspend fun decrypt(encrypted: ByteArray): Result<ByteArray>
     */

    abstract suspend fun getPublicKey(): Key
    abstract suspend fun getPublicKeyRepresentation(): ByteArray

    override fun toString() = "[walt.id CoreCrypto ${if (hasPrivateKey) "private" else "public"} $keyType key]"

}
