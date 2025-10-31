package id.walt.policies.policies

import kotlin.io.encoding.Base64

object Base64Utils {
    private val base64Encoding = Base64.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
    fun decode(base64: String): ByteArray = base64Encoding.decode(base64)
}