package id.walt.crypto.keys

import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.keys.oci.OCIKey
import id.walt.crypto.keys.oci.OCIKeyRestApi
import id.walt.crypto.keys.tse.TSEKey
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport


@OptIn(ExperimentalJsExport::class)
@JsExport
object KeySerialization {

    private val keySerializationModule = SerializersModule {
        polymorphic(Key::class) {
            subclass(JWKKey::class)
            subclass(TSEKey::class)
            subclass(OCIKeyRestApi::class)
            subclass(OCIKey::class)
        }
    }

    private val keySerializationJson = Json {
        serializersModule =
            keySerializationModule
    }

    fun serializeKey(key: Key): String = keySerializationJson.encodeToString(key)
    fun serializeKeyToJson(key: Key): JsonElement = keySerializationJson.encodeToJsonElement(key)

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun deserializeKey(json: String): Result<Key> =
        runCatching { keySerializationJson.decodeFromString<Key>(json).apply { init() } }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun deserializeKey(json: JsonObject): Result<Key> =
        runCatching { keySerializationJson.decodeFromJsonElement<Key>(json).apply { init() } }
}
