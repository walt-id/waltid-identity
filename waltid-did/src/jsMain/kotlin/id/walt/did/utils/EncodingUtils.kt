package id.walt.did.utils

import kotlin.js.js

actual object EncodingUtils {
    actual fun urlEncode(path: String): String = js("encodeURIComponent")(path)

    actual fun urlDecode(path: String): String = js("decodeURIComponent")(path)

    actual fun base64Encode(data: ByteArray): String = js("btoa")(data)

    actual fun base64Decode(data: String): ByteArray = js("atob")(data)
}