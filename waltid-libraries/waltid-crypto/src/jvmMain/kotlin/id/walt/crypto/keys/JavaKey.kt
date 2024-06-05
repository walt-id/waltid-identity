package id.walt.crypto.keys

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

abstract class JavaKey : Key() {

    abstract fun javaGetKeyType(): KeyType
    override val keyType: KeyType
        get() = javaGetKeyType()

    abstract fun javaHasPrivateKey(): Boolean
    override val hasPrivateKey: Boolean
        get() = javaHasPrivateKey()

    abstract fun javaGetKeyId(): String
    override suspend fun getKeyId(): String = runBlocking { javaGetKeyId() }

    abstract fun javaGetThumbprint(): String
    override suspend fun getThumbprint(): String = runBlocking { javaGetThumbprint() }

    abstract fun javaExportJWK(): String
    override suspend fun exportJWK(): String = runBlocking { javaExportJWK() }

    abstract fun javaExportJWKObject(): JsonObject
    override suspend fun exportJWKObject(): JsonObject = javaExportJWKObject()

    abstract fun javaExportPEM(): String
    override suspend fun exportPEM(): String = javaExportPEM()

    abstract fun javaSignRaw(plaintext: ByteArray): Any
    override suspend fun signRaw(plaintext: ByteArray): Any = javaSignRaw(plaintext)

    abstract fun javaSignJws(plaintext: ByteArray, headers: Map<String, String>): String
    override suspend fun signJws(plaintext: ByteArray, headers: Map<String, String>): String = javaSignJws(plaintext, headers)

    abstract fun javaVerifyRaw(): ByteArray
    override suspend fun verifyRaw(signed: ByteArray, detachedPlaintext: ByteArray?): Result<ByteArray> =
        runBlocking { runCatching { javaVerifyRaw() } }


    abstract fun javaVerifyJws(): JsonElement
    override suspend fun verifyJws(signedJws: String): Result<JsonElement> =
        runBlocking { runCatching { javaVerifyJws() } }

    abstract fun javaGetPublicKey(): Key
    override suspend fun getPublicKey(): Key = runBlocking { javaGetPublicKey() }

    abstract fun javaGetPublicKeyRepresentation(): ByteArray
    override suspend fun getPublicKeyRepresentation(): ByteArray = javaGetPublicKeyRepresentation()

    abstract fun javaGetMeta(): KeyMeta
    override suspend fun getMeta(): KeyMeta = javaGetMeta()
}
