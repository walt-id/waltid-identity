package id.walt.crypto.keys

import id.walt.crypto.keys.aws.AWSKeyRestAPI
import id.walt.crypto.keys.azure.AzureKey
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.keys.oci.OCIKeyRestApi
import id.walt.crypto.keys.tse.TSEKey
import id.walt.crypto.utils.JsonUtils.toJsonElement
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.json.*
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import kotlinx.serialization.serializer
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.reflect.KClass

// TODO: Deprecate this in favour of KeyManager

@OptIn(ExperimentalJsExport::class)
@JsExport
object KeySerialization {

    private var keySerializationModule = SerializersModule {
        polymorphic(Key::class) {
            subclass(JWKKey::class)
            subclass(TSEKey::class)
            subclass(OCIKeyRestApi::class)
            subclass(AWSKeyRestAPI::class)
            subclass(AzureKey::class)
        }

    }

    private var keySerializationJson = Json {
        serializersModule =
            keySerializationModule
    }

    @OptIn(InternalSerializationApi::class)
    fun <T : Key> registerExternalKeyType(keyClass: KClass<T>) {
        keySerializationModule = SerializersModule {
            polymorphic(Key::class) {
                subclass(JWKKey::class)
                subclass(TSEKey::class)
                subclass(OCIKeyRestApi::class)
                subclass(AWSKeyRestAPI::class)
                subclass(AzureKey::class)
                subclass(keyClass, keyClass.serializer())
            }
        }

        keySerializationJson = Json {
            serializersModule = keySerializationModule
        }
    }

    fun serializeKey(key: Key): String = keySerializationJson.encodeToString(key)

    @Suppress("NON_EXPORTABLE_TYPE")
    fun serializeKeyToJson(key: Key): JsonElement = keySerializationJson.encodeToJsonElement(key)

    @Deprecated("Will not handle externally implemented Keys, replace with KeyManager.resolveSerializedKey")
    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun deserializeKey(json: String): Result<Key> =
        runCatching { keySerializationJson.decodeFromString<Key>(json).apply { init() } }

    @Deprecated("Will not handle externally implemented Keys, replace with KeyManager.resolveSerializedKey")
    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun deserializeKeyObject(json: JsonObject): Result<Key> =
        runCatching {
            keySerializationJson.decodeFromJsonElement<Key>(json).apply { init() }
        }

    @Deprecated("Will not handle externally implemented Keys, replace KeySerialization here with KeyManager")
    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun deserializeJWTKey(json: JsonObject): Result<Key> =
        runCatching {
            keySerializationJson.decodeFromJsonElement<Key>(json.mapValues {
                if (it.value is JsonPrimitive) it.value.jsonPrimitive.content else it.value.toString()
            }.toJsonElement()).apply { init() }
        }
}
