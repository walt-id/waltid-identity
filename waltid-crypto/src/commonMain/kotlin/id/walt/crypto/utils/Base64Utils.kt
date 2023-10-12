package id.walt.crypto.utils

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
object Base64Utils {

    fun String.base64toBase64Url() = this.replace("+", "-").replace("/", "_").dropLastWhile { it == '='  }
    fun String.base64UrlToBase64() = this.replace("-", "+").replace("_", "/")

    fun ByteArray.encodeToBase64Url() = Base64.UrlSafe.encode(this).dropLastWhile { it == '=' }

    fun String.base64UrlDecode() = Base64.UrlSafe.decode(this)

    fun String.base64Decode() = Base64.decode(this)
}
