package id.walt.sdjwt.utils

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
object SdjwtStringUtils {

    private val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

    internal fun String.decodeFromBase64Url() = base64Url.decode(this)
}