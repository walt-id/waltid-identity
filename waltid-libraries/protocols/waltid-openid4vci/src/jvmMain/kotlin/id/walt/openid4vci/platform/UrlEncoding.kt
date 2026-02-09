package id.walt.openid4vci.platform

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

actual fun urlEncode(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8)
