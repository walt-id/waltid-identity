package id.walt.crypto.keys

import id.walt.crypto.utils.JsonUtils.prettyJson
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

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
    abstract suspend fun getKeyId(): String

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    abstract suspend fun getThumbprint(): String


    /**
     * export this key as a JWK if possible (check documentation if this algorithm / key type is supported by the JWK spec)
     * @return JWK
     */
    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    abstract suspend fun exportJWK(): String

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    open suspend fun exportJWKPretty(): String = prettyJson.encodeToString(Json.parseToJsonElement(exportJWK()))


    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    abstract suspend fun exportJWKObject(): JsonObject

    /**
     * export this key as a PEM if supported
     * @return encoded PEM
     */
    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
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
    abstract suspend fun signRaw(plaintext: ByteArray): Any

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
    abstract suspend fun signJws(plaintext: ByteArray, headers: Map<String, String> = emptyMap()): String

    /**
     * verifies a signed message using this public key
     * @param signed signed
     * @return Result wrapping the plaintext; Result failure when the signature fails
     */
    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    abstract suspend fun verifyRaw(signed: ByteArray, detachedPlaintext: ByteArray? = null): Result<ByteArray>

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
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
    abstract suspend fun getPublicKey(): Key

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    abstract suspend fun getPublicKeyRepresentation(): ByteArray

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    abstract suspend fun getMeta(): KeyMeta

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override fun toString() = "[walt.id CoreCrypto ${if (hasPrivateKey) "private" else "public"} $keyType key]"

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    open suspend fun init() {
    }
}
