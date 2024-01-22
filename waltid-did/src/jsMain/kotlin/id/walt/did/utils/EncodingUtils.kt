package id.walt.did.utils

import kotlin.js.js

actual object EncodingUtils {
    actual fun urlEncode(path: String): String = js("encodeURIComponent")(path)

    actual fun urlDecode(path: String): String = js("decodeURIComponent")(path)

    actual fun base64Encode(data: ByteArray): String = js("btoa")(data)

    actual fun base64Decode(data: String): ByteArray = js("atob")(data)
    actual fun base58Encode(byteArray: ByteArray): String {
        TODO("Not yet implemented")
    }

    actual fun base58Decode(base58String: String): ByteArray {
        TODO("Not yet implemented")
    }

    actual fun fromHexString(hexString: String): ByteArray {
        TODO("Not yet implemented")
    }

    actual fun toHexString(byteArray: ByteArray): String {
        TODO("Not yet implemented")
    }
}