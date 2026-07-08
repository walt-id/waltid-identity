package id.walt.openid4vci.requests

import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import org.kotlincrypto.random.CryptoRand

internal fun generateRequestId(): String {
    val bytes = ByteArray(16)
    CryptoRand.nextBytes(bytes)
    return bytes.encodeToBase64Url()
}
