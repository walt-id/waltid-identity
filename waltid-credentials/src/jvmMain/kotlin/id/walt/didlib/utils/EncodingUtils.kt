package id.walt.didlib.utils

import java.net.URLDecoder
import java.net.URLEncoder

actual object EncodingUtils {
    actual fun urlEncode(path: String): String = URLEncoder.encode(path)

    actual fun urlDecode(path: String): String = URLDecoder.decode(path)
}