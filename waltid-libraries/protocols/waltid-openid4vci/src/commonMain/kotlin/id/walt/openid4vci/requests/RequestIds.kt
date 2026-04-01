package id.walt.openid4vci.requests

import org.kotlincrypto.random.CryptoRand
import kotlin.io.encoding.Base64

internal fun generateRequestId(): String {
    val bytes = ByteArray(16)
    CryptoRand.nextBytes(bytes)
    return Base64.UrlSafe.encode(bytes)
}
