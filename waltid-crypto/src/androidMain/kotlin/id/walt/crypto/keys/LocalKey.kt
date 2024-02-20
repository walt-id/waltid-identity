package id.walt.crypto.keys

import kotlinx.serialization.json.JsonObject
import java.security.Signature

actual class LocalKey actual constructor(jwk: String?) : Key() {

    override val keyType: KeyType
        get() = TODO("Not yet implemented")

    actual override val hasPrivateKey: Boolean
        get() = TODO("Not yet implemented")

    actual override suspend fun getKeyId(): String = TODO("Not yet implemented")

    actual override suspend fun getThumbprint(): String = TODO("Not yet implemented")

    actual override suspend fun exportJWK(): String = TODO("Not yet implemented")

    actual override suspend fun exportJWKObject(): JsonObject = TODO("Not yet implemented")

    actual override suspend fun exportPEM(): String = TODO("Not yet implemented")

    actual override suspend fun signRaw(plaintext: ByteArray): ByteArray = TODO("Not yet implemented")

    actual override suspend fun signJws(plaintext: ByteArray, headers: Map<String, String>): String = TODO("Not yet implemented")

    actual override suspend fun verifyRaw(signed: ByteArray, detachedPlaintext: ByteArray?): Result<ByteArray> = TODO("Not yet implemented")

    actual override suspend fun verifyJws(signedJws: String): Result<JsonObject> = TODO("Not yet implemented")

    actual override suspend fun getPublicKey(): LocalKey = TODO("Not yet implemented")

    actual override suspend fun getPublicKeyRepresentation(): ByteArray = TODO("Not yet implemented")

    private fun getSignature(): Signature = TODO("Not yet implemented")

    actual companion object : LocalKeyCreator {
        actual override suspend fun generate(
            type: KeyType, metadata: LocalKeyMetadata
        ): LocalKey = TODO("Not yet implemented")

        actual override suspend fun importRawPublicKey(
            type: KeyType, rawPublicKey: ByteArray, metadata: LocalKeyMetadata
        ): Key = TODO("Not yet implemented")

        actual override suspend fun importJWK(jwk: String): Result<LocalKey> = TODO("Not yet implemented")

        actual override suspend fun importPEM(pem: String): Result<LocalKey> = TODO("Not yet implemented")

    }
}