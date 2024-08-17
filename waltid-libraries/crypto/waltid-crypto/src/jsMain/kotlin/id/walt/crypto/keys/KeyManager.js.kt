package id.walt.crypto.keys

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

// don't
@OptIn(DelicateCoroutinesApi::class)
actual fun resolveSerializedKeyBlocking(json: JsonObject): Key {
    var resolved: Key? = null
    GlobalScope.launch {
        resolved = KeyManager.resolveSerializedKey(json)
    }
    while (resolved == null) {

    }

    return resolved!!
}
