package id.walt.crypto.keys.aws

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyMeta
import id.walt.crypto.keys.KeyType
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

private val logger = KotlinLogging.logger { }

class AWSKey(override val keyType: KeyType, override val hasPrivateKey: Boolean) : Key() {
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

    override suspend fun signRaw(plaintext: ByteArray): Any {
        TODO("Not yet implemented")
    }

    override suspend fun signJws(
        plaintext: ByteArray,
        headers: Map<String, JsonElement>
    ): String {
        TODO("Not yet implemented")
    }

    override suspend fun verifyRaw(
        signed: ByteArray,
        detachedPlaintext: ByteArray?
    ): Result<ByteArray> {
        TODO("Not yet implemented")
    }

    override suspend fun verifyJws(signedJws: String): Result<JsonElement> {
        TODO("Not yet implemented")
    }

    override suspend fun getPublicKey(): Key {
        TODO("Not yet implemented")
    }

    override suspend fun getPublicKeyRepresentation(): ByteArray {
        TODO("Not yet implemented")
    }

    override suspend fun getMeta(): KeyMeta {
        TODO("Not yet implemented")
    }

}