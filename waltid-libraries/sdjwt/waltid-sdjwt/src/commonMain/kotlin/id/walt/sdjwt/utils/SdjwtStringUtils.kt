package id.walt.sdjwt.utils

import kotlin.io.encoding.Base64

object SdjwtStringUtils {

    private val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

    internal fun String.decodeFromBase64Url() = base64Url.decode(this)
}
