package id.walt.iso18013.annexc.cbor

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
object Base64UrlNoPad {
    private val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

    fun encode(data: ByteArray): String = base64Url.encode(data).trimEnd('=')
    fun decode(text: String): ByteArray = base64Url.decode(text)
}

