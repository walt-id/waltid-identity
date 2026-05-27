package id.walt.crypto.utils

import org.kotlincrypto.hash.sha2.SHA256
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
private val base64UrlNoPad = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

object ShaUtils {

    // Helper to calculate SHA-256 and then Base64URL encode from a String
    fun calculateSha256Base64Url(input: String): String =
        base64UrlNoPad.encode(SHA256().digest(input.encodeToByteArray()))

    // Helper to calculate SHA-256 and then Base64URL encode from a ByteArray
    fun sha256Base64Url(input: ByteArray): String =
        base64UrlNoPad.encode(SHA256().digest(input))

}
