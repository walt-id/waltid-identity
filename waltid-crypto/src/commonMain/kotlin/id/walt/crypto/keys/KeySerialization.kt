package id.walt.crypto.keys

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

object KeySerialization {

    private val keySerializationModule = SerializersModule {
        polymorphic(Key::class) {
            subclass(LocalKey::class)
            subclass(TSEKey::class)
        }
    }

    private val keySerializationJson = Json { serializersModule =
        keySerializationModule
    }

    fun serializeKey(key: Key): String = keySerializationJson.encodeToString(key)
    fun deserializeKey(json: String): Result<Key> = runCatching { keySerializationJson.decodeFromString<Key>(json) }
    fun deserializeKey(json: JsonObject): Result<Key> = runCatching { keySerializationJson.decodeFromJsonElement<Key>(json) }
}
