package id.walt.crypto2.serialization

import id.walt.crypto2.keys.StoredKey
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object StoredKeyCodec {
    private val json = Json {
        encodeDefaults = false
        explicitNulls = true
        ignoreUnknownKeys = false
        isLenient = false
    }

    fun encodeToString(key: StoredKey): String = json.encodeToString(key)

    fun encodeToByteArray(key: StoredKey): ByteArray = encodeToString(key).encodeToByteArray()

    fun decodeFromString(encoded: String): StoredKey {
        val element = json.parseToJsonElement(encoded)
        val version = element.jsonObject["version"]?.jsonPrimitive?.int
            ?: throw SerializationException("Stored key version is missing")
        if (version != StoredKey.CURRENT_VERSION) {
            throw SerializationException("Unsupported stored key version: $version")
        }
        return json.decodeFromJsonElement(element)
    }

    fun decodeFromByteArray(encoded: ByteArray): StoredKey = decodeFromString(encoded.decodeToString())
}
