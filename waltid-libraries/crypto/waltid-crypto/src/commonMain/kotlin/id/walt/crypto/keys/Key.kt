package id.walt.crypto.keys

import id.walt.crypto.utils.JsonUtils.prettyJson
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

object DirectKeySerializer : KSerializer<DirectSerializedKey> {

    override val descriptor: SerialDescriptor = JsonObject.serializer().descriptor
    override fun deserialize(decoder: Decoder): DirectSerializedKey =
        DirectSerializedKey(resolveSerializedKeyBlocking(decoder.decodeSerializableValue(JsonObject.serializer())))

    override fun serialize(encoder: Encoder, value: DirectSerializedKey) =
        encoder.encodeSerializableValue(JsonElement.serializer(), KeySerialization.serializeKeyToJson(value.key))
}

@Serializable(with = DirectKeySerializer::class)
data class DirectSerializedKey(val key: Key)

@JsExport
@OptIn(ExperimentalSerializationApi::class, ExperimentalJsExport::class)
@Serializable
@JsonClassDiscriminator("type")
abstract class Key {

    abstract val keyType: KeyType
    //abstract val metadata: KeyMetadata

    abstract val hasPrivateKey: Boolean

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    @Throws(Exception::class)
    abstract suspend fun getKeyId(): String

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    @Throws(Exception::class)
    abstract suspend fun getThumbprint(): String


    /**
     * export this key as a JWK if possible (check documentation if this algorithm / key type is supported by the JWK spec)
     * @return JWK
     */
    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    @Throws(Exception::class)
    abstract suspend fun exportJWK(): String

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    @Throws(Exception::class)
    open suspend fun exportJWKPretty(): String = prettyJson.encodeToString(Json.parseToJsonElement(exportJWK()))


    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    @Throws(Exception::class)
    abstract suspend fun exportJWKObject(): JsonObject

    /**
     * export this key as a PEM if supported
     * yoy can validate at: https://8gwifi.org/PemParserFunctions.jsp various formats
     * @return encoded PEM
     */
    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    @Throws(Exception::class)
    abstract suspend fun exportPEM(): String

    /**
     * signs a message using this private key (with the algorithm this key is based on)
     * @exception IllegalArgumentException when this is not a private key
     * @param plaintext data to be signed
     * @return raw signature
     */
    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    @Throws(Exception::class)
    abstract suspend fun signRaw(plaintext: ByteArray, customSignatureAlgorithm: String? = null): Any

    /**
     * signs a message using this private key (with the algorithm this key is based on)
     * @exception IllegalArgumentException when this is not a private key
     * @param plaintext data to be signed
     * @return JWS
     */
    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    @Throws(Exception::class)
    abstract suspend fun signJws(plaintext: ByteArray, headers: Map<String, JsonElement> = emptyMap()): String

    /**
     * verifies a signed message using this public key
     * @param signed signed
     * @return Result wrapping the plaintext; Result failure when the signature fails
     */
    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    @Throws(Exception::class)
    abstract suspend fun verifyRaw(
        signed: ByteArray,
        detachedPlaintext: ByteArray? = null,
        customSignatureAlgorithm: String? = null
    ): Result<ByteArray>

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    @Throws(Exception::class)
    abstract suspend fun verifyJws(signedJws: String): Result<JsonElement>

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

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    @Throws(Exception::class)
    abstract suspend fun getPublicKey(): Key

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    @Throws(Exception::class)
    abstract suspend fun getPublicKeyRepresentation(): ByteArray

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    @Throws(Exception::class)
    abstract suspend fun getMeta(): KeyMeta


    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    @Throws(Exception::class)
    abstract suspend fun deleteKey(): Boolean


    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore

    override fun toString() = "[walt.id crypto ${if (hasPrivateKey) "private" else "public"} $keyType key]"

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    open suspend fun init() {
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Key) return false
        if (keyType != other.keyType) return false
        return KeySerialization.serializeKeyToJson(this) == KeySerialization.serializeKeyToJson(other)
    }

    override fun hashCode(): Int =
        31 * keyType.hashCode() + KeySerialization.serializeKeyToJson(this).hashCode()

}

