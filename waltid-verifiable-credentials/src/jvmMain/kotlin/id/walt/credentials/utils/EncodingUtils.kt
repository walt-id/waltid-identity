package id.walt.credentials.utils

import java.net.URLDecoder
import java.net.URLEncoder

actual object EncodingUtils {
    actual fun urlEncode(path: String): String = URLEncoder.encode(path, Charsets.UTF_8)

    actual fun urlDecode(path: String): String = URLDecoder.decode(path, Charsets.UTF_8)
}
