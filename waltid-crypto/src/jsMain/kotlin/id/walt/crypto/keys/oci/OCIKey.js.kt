package id.walt.crypto.keys.oci

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.OciKeyMeta
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import love.forte.plugin.suspendtrans.annotation.JsPromise

@JsExport
@Serializable
@SerialName("oci")
actual class OCIKey actual constructor(
    actual val id: String,
    actual val config: OCIsdkMetadata,
    val _publicKey: String?,
     val _keyType: KeyType?
) : Key() {
    @Transient
    actual override var keyType: KeyType
        get() = TODO("Not yet implemented")
        set(value) {}
    actual override val hasPrivateKey: Boolean
        get() = TODO("Not yet implemented")


    actual override fun toString(): String {
        TODO("Not yet implemented")
    }

    @JsPromise
    @JsExport.Ignore
    actual override suspend fun getKeyId(): String {
        TODO("Not yet implemented")
    }

    @JsPromise
    @JsExport.Ignore
    actual override suspend fun getThumbprint(): String {
        TODO("Not yet implemented")
    }

    @JsPromise
    @JsExport.Ignore
    actual override suspend fun exportJWK(): String {
        TODO("Not yet implemented")
    }

    @JsPromise
    @JsExport.Ignore
    actual override suspend fun exportJWKObject(): JsonObject {
        TODO("Not yet implemented")
    }

    @JsPromise
    @JsExport.Ignore
    actual override suspend fun exportPEM(): String {
        TODO("Not yet implemented")
    }

    @JsPromise
    @JsExport.Ignore
    actual override suspend fun signRaw(plaintext: ByteArray): ByteArray {
        TODO("Not yet implemented")
    }

    @JsPromise
    @JsExport.Ignore
    actual override suspend fun signJws(
        plaintext: ByteArray,
        headers: Map<String, String>
    ): String {
        TODO("Not yet implemented")
    }

    @JsPromise
    @JsExport.Ignore
    actual override suspend fun verifyRaw(
        signed: ByteArray,
        detachedPlaintext: ByteArray?
    ): Result<ByteArray> {
        TODO("Not yet implemented")
    }

    @JsPromise
    @JsExport.Ignore
    actual override suspend fun verifyJws(signedJws: String): Result<JsonElement> {
        TODO("Not yet implemented")
    }

    @JsPromise
    @JsExport.Ignore
    actual override suspend fun getPublicKey(): Key {
        TODO("Not yet implemented")
    }

    @JsPromise
    @JsExport.Ignore
    actual override suspend fun getPublicKeyRepresentation(): ByteArray {
        TODO("Not yet implemented")
    }

    @JsPromise
    @JsExport.Ignore
    actual override suspend fun getMeta(): OciKeyMeta {
        TODO("Not yet implemented")
    }

    actual companion object {
        actual val DEFAULT_KEY_LENGTH: Int
            get() = TODO("Not yet implemented")

        @JsPromise
        @JsExport.Ignore
        actual suspend fun generateKey(config: OCIsdkMetadata): OCIKey {
            TODO("Not yet implemented")
        }


    }


}