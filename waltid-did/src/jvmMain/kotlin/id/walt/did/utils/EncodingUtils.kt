package id.walt.did.utils

import io.ipfs.multibase.Base58
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

actual object EncodingUtils {
    actual fun urlEncode(path: String): String = URLEncoder.encode(path, StandardCharsets.UTF_8)

    actual fun urlDecode(path: String): String = URLDecoder.decode(path, StandardCharsets.UTF_8)

    actual fun base64Encode(data: ByteArray): String = java.util.Base64.getEncoder().encodeToString(data)

    actual fun base64Decode(data: String): ByteArray = java.util.Base64.getDecoder().decode(data)
    actual fun base58Encode(byteArray: ByteArray): String = Base58.encode(byteArray)

    actual fun base58Decode(base58String: String): ByteArray = Base58.decode(base58String)
}
