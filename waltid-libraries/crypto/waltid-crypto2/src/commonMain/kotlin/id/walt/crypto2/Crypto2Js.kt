package id.walt.crypto2

import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.serialization.StoredKeyCodec

@Crypto2JsExport
object Crypto2Js {
    fun normalizeStoredKey(encoded: String): String =
        StoredKeyCodec.encodeToString(StoredKeyCodec.decodeFromString(encoded))

    fun storedKeyProvider(encoded: String): String = when (val key = StoredKeyCodec.decodeFromString(encoded)) {
        is StoredKey.Software -> "software"
        is StoredKey.Managed -> key.provider.value
    }

    fun isStoredKey(encoded: String): Boolean =
        runCatching { StoredKeyCodec.decodeFromString(encoded) }.isSuccess
}
