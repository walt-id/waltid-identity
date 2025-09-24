package id.walt.crypto.utils

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class, ExperimentalEncodingApi::class)
@JsExport
object Base64Utils {

    val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
    private val base64 = Base64

    fun String.base64toBase64Url() = this.replace("+", "-").replace("/", "_").trimEnd('=')
    fun String.base64UrlToBase64() = this.replace("-", "+").replace("_", "/")

    fun ByteArray.encodeToBase64() = base64.encode(this)
    fun String.decodeFromBase64() = base64.decode(this)


    fun ByteArray.encodeToBase64Url() = base64Url.encode(this).trimEnd('=')
    fun String.decodeFromBase64Url() = base64Url.decode(this)

    fun String.base64UrlDecode() = base64Url.decode(this)
    fun String.base64Decode() = base64.decode(this)


    /**
     * Checks performed:
     * 1. Empty string will be considered *INVALID*.
     * 2. Contains only valid Base64URL characters (A-Z, a-z, 0-9, -, _)
     *    plus optional padding (=) at the end.
     * 3. Padding (=) only appears at the end, if at all.
     * 4. Padding length is at most 2 characters (==).
     * 5. If padding is present, the total string length must be a multiple of 4.
     * 6. The length of the data part (before padding) modulo 4 is not 1.
     */
    fun String.matchesBase64Url(): Boolean {
        // Base64 encoding of empty data is an empty string
        // However, 100% of our use-cases we explicitly do not want to match empty Base64 data.
        if (isEmpty()) {
            return false
        }

        // --- Character Set and Padding Position Check ---
        var paddingCount = 0
        var firstPaddingIndex = -1

        forEachIndexed { i, char ->
            val isPaddingChar = (char == '=')
            val isValidDataChar = (char in 'A'..'Z') ||
                    (char in 'a'..'z') ||
                    (char in '0'..'9') ||
                    (char == '-') ||
                    (char == '_')

            if (isPaddingChar) {
                if (firstPaddingIndex == -1) {
                    firstPaddingIndex = i // Mark where padding starts
                }
                paddingCount++
            } else if (!isValidDataChar) {
                // Invalid character found anywhere
                return false
            } else if (firstPaddingIndex != -1) {
                // Found a valid data character *after* padding started
                return false
            }
        }

        // --- Padding Length and Total Length Check ---
        if (paddingCount > 2) {
            // Max 2 padding characters allowed
            return false
        }
        if (paddingCount > 0 && length % 4 != 0) {
            // If padding exists, total length must be a multiple of 4
            return false
        }

        // --- Data Length Check ---
        val dataLength = if (firstPaddingIndex == -1) {
            length // No padding found
        } else {
            firstPaddingIndex // Length of the part before padding
        }

        // The length of the data part (before padding) modulo 4 cannot be 1.
        // (Corresponds to Base64 encoding rules: 1 byte -> 2 chars, 2 bytes -> 3 chars, 3 bytes -> 4 chars)
        if (dataLength % 4 == 1) {
            return false
        }

        // If all checks passed, it's potentially Base64URL
        return true
    }
}
