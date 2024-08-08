package id.walt.crypto.keys

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject

actual fun resolveSerializedKeyBlocking(json: JsonObject): Key = runBlocking { KeyManager.resolveSerializedKey(json) }
