package id.walt.crypto.utils

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class, ExperimentalEncodingApi::class)
@JsExport
object Base64Utils {

    private val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
    private val base64 = Base64

    fun String.base64toBase64Url() = this.replace("+", "-").replace("/", "_").trimEnd('=')
    fun String.base64UrlToBase64() = this.replace("-", "+").replace("_", "/")

    fun ByteArray.encodeToBase64() = base64.encode(this)
    fun String.decodeFromBase64() = base64.decode(this)


    fun ByteArray.encodeToBase64Url() = base64Url.encode(this).trimEnd('=')
    fun String.decodeFromBase64Url() = base64Url.decode(this)

    fun String.base64UrlDecode() = base64Url.decode(this)
    fun String.base64Decode() = base64.decode(this)
}
