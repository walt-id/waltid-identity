package id.walt.openid4vci.requests

import korlibs.crypto.SecureRandom
import kotlin.io.encoding.Base64

internal fun generateRequestId(): String {
    val bytes = ByteArray(16)
    SecureRandom.nextBytes(bytes)
    return Base64.UrlSafe.encode(bytes)
}
