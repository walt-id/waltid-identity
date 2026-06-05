package id.walt.crypto.utils

import org.kotlincrypto.hash.sha1.SHA1
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

    // Raw SHA-256 digest of a ByteArray
    fun sha256(input: ByteArray): ByteArray = SHA256().digest(input)

    // Raw SHA-1 digest of a ByteArray
    fun sha1(input: ByteArray): ByteArray = SHA1().digest(input)

    // SHA-1 as uppercase hex with ':' separator (e.g. "AB:CD:EF...")
    fun sha1HexColon(input: ByteArray): String =
        sha1(input).joinToString(":") { it.toUByte().toString(16).padStart(2, '0').uppercase() }

}
