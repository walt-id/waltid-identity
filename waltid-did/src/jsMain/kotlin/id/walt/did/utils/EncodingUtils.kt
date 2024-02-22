package id.walt.did.utils

import bs58
import id.walt.crypto.utils.ArrayUtils.toByteArray
import org.khronos.webgl.Uint8Array
import kotlin.js.js

@ExperimentalJsExport
@JsExport
actual object EncodingUtils {
    actual fun urlEncode(path: String): String = js("encodeURIComponent")(path)

    actual fun urlDecode(path: String): String = js("decodeURIComponent")(path)

    actual fun base64Encode(data: ByteArray): String = js("btoa")(data)

    actual fun base64Decode(data: String): ByteArray = js("atob")(data)
    actual fun base58Encode(byteArray: ByteArray): String {
        return bs58.encode(Uint8Array(byteArray.toTypedArray()))
    }

    actual fun base58Decode(base58String: String): ByteArray {
        return bs58.decode(base58String).toByteArray()
    }

    actual fun fromHexString(hexString: String): ByteArray =
        hexString.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    actual fun toHexString(byteArray: ByteArray): String {
        TODO("Not yet implemented")
    }
}