package id.walt.crypto.keys.jwk

import id.walt.crypto.keys.JwkKeyMeta
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

expect class JWKKey(jwk: String?, _keyId: String? = null) : Key {
    override val keyType: KeyType

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
    override suspend fun signRaw(plaintext: ByteArray, customSignatureAlgorithm: String?): ByteArray
    override suspend fun signJws(plaintext: ByteArray, headers: Map<String, JsonElement>): String

    /**
     * Verifies JWS: Verifies a signed message using this public key
     * @param signed signed
     * @return Result wrapping the plaintext; Result failure when the signature fails
     */
    override suspend fun verifyRaw(signed: ByteArray, detachedPlaintext: ByteArray?, customSignatureAlgorithm: String?): Result<ByteArray>
    override suspend fun verifyJws(signedJws: String): Result<JsonElement>

    suspend fun decryptJwe(jweString: String): ByteArray
    suspend fun encryptJwe(plaintext: ByteArray): String

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
     * @param encrypted data
     * @return Result wrapping the plaintext; Result failure when the decryption fails
     */
    override suspend fun decrypt(encrypted: ByteArray): Result<ByteArray>
     */

    override suspend fun getPublicKey(): JWKKey
    override suspend fun getPublicKeyRepresentation(): ByteArray
    override suspend fun getMeta(): JwkKeyMeta
    override suspend fun deleteKey(): Boolean


    override val hasPrivateKey: Boolean


    companion object : JWKKeyCreator {

        override suspend fun generate(type: KeyType, metadata: JwkKeyMeta?): JWKKey
        override suspend fun importRawPublicKey(type: KeyType, rawPublicKey: ByteArray, metadata: JwkKeyMeta?): Key

        override suspend fun importJWK(jwk: String): Result<JWKKey>

        override suspend fun importPEM(pem: String): Result<JWKKey>
    }

}

object JWKKeyJsonFieldSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor
    override fun deserialize(decoder: Decoder): String =
        Json.encodeToString(decoder.decodeSerializableValue(JsonElement.serializer()))

    override fun serialize(encoder: Encoder, value: String?) = encoder.encodeSerializableValue(JsonElement.serializer(),
        value?.let { Json.decodeFromString<JsonElement>(it) } ?: JsonNull)
}
