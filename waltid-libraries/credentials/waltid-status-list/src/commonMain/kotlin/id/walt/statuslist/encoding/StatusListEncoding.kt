package id.walt.statuslist.encoding

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private const val MULTIBASE_BASE64_URL_PREFIX = 'u'

enum class StatusListStringEncoding {
    Base64,
    Base64Url,
    MultibaseBase64Url,
}

@OptIn(ExperimentalEncodingApi::class)
object StatusListEncoding {
    private val base64 = Base64.Default
    private val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

    fun encode(data: ByteArray, encoding: StatusListStringEncoding): String = when (encoding) {
        StatusListStringEncoding.Base64 -> base64.encode(data)
        StatusListStringEncoding.Base64Url -> encodeBase64Url(data)
        StatusListStringEncoding.MultibaseBase64Url -> MULTIBASE_BASE64_URL_PREFIX + encodeBase64Url(data)
    }

    fun decode(encoded: String, encoding: StatusListStringEncoding): ByteArray = when (encoding) {
        StatusListStringEncoding.Base64 -> base64.decode(encoded)
        StatusListStringEncoding.Base64Url -> decodeBase64Url(encoded, requireMultibase = false)
        StatusListStringEncoding.MultibaseBase64Url -> decodeBase64Url(encoded, requireMultibase = true)
    }

    fun encodeBase64Url(data: ByteArray): String = base64Url.encode(data).trimEnd('=')

    fun decodeBase64Url(encoded: String, requireMultibase: Boolean): ByteArray {
        val isMultibase = encoded.startsWith(MULTIBASE_BASE64_URL_PREFIX)
        require(isMultibase == requireMultibase) {
            if (requireMultibase) "Expecting multibase base64-url, got regular base64-url: $encoded"
            else "Expecting regular base64-url, got: $encoded"
        }
        val payload = if (isMultibase) encoded.drop(1) else encoded
        return base64Url.decode(payload)
    }
}
