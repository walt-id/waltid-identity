package id.walt.crypto.keys.oci

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.OciKeyMeta
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject


expect class OCIKey(
    id: String,
    config: OCIsdkMetadata,
    _publicKey: String? = null,
    _keyType: KeyType? = null
) : Key {
    override var keyType: KeyType
    override val hasPrivateKey: Boolean
    override fun toString(): String
    override suspend fun getKeyId(): String
    override suspend fun getThumbprint(): String
    override suspend fun exportJWK(): String
    override suspend fun exportJWKObject(): JsonObject
    override suspend fun exportPEM(): String
    override suspend fun signRaw(plaintext: ByteArray): ByteArray
    override suspend fun signJws(
        plaintext: ByteArray,
        headers: Map<String, String>
    ): String

    override suspend fun verifyRaw(
        signed: ByteArray,
        detachedPlaintext: ByteArray?
    ): Result<ByteArray>

    override suspend fun verifyJws(signedJws: String): Result<JsonElement>
    override suspend fun getPublicKey(): Key
    override suspend fun getPublicKeyRepresentation(): ByteArray
    override suspend fun getMeta(): OciKeyMeta

    companion object {

        // The KeyShape used for testing
        val DEFAULT_KEY_LENGTH: Int
        suspend fun generateKey(config: OCIsdkMetadata): OCIKey


    }

    val id: String
    val config: OCIsdkMetadata



}