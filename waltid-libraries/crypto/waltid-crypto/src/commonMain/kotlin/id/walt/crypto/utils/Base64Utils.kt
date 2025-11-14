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
     * Enum to specify the Base64 character set variant.
     */
    enum class Base64Variant {
        /** Standard Base64 alphabet using '+' and '/'. See RFC 4648 Section 4. */
        STANDARD,

        /** URL-safe Base64 alphabet using '-' and '_'. See RFC 4648 Section 5. */
        URL_SAFE
    }

    fun String.matchesBase64Url(): Boolean = matchesBase64(Base64Variant.URL_SAFE)

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
    fun String.matchesBase64(variant: Base64Variant = Base64Variant.STANDARD): Boolean {
        // Base64 encoding of empty data is an empty string.
        // However, for many use-cases, we explicitly do not want to match empty Base64 data.
        if (isEmpty()) {
            return false
        }

        // --- Character Set and Padding Position Check ---
        var paddingCount = 0
        var firstPaddingIndex = -1

        forEachIndexed { i, char ->
            val isPaddingChar = (char == '=')

            val isCommonDataChar = (char in 'A'..'Z') ||
                    (char in 'a'..'z') ||
                    (char in '0'..'9')

            // Check for variant-specific characters ('+' and '/' for STANDARD, '-' and '_' for URL_SAFE)
            val isValidDataChar = isCommonDataChar || when (variant) {
                Base64Variant.STANDARD -> char == '+' || char == '/'
                Base64Variant.URL_SAFE -> char == '-' || char == '_'
            }

            if (isPaddingChar) {
                if (firstPaddingIndex == -1) {
                    firstPaddingIndex = i // Mark where padding starts
                }
                paddingCount++
            } else if (!isValidDataChar) {
                // Invalid character found anywhere in the string
                return false
            } else if (firstPaddingIndex != -1) {
                // Found a valid data character *after* padding started
                return false
            }
        }

        // --- Padding Length and Total Length Check ---
        if (paddingCount > 2) {
            // A maximum of 2 padding characters is allowed
            return false
        }
        if (paddingCount > 0 && length % 4 != 0) {
            // If padding exists, the total length must be a multiple of 4
            return false
        }

        // --- Data Length Check ---
        val dataLength = if (firstPaddingIndex == -1) {
            length // No padding found
        } else {
            firstPaddingIndex // Length of the part before padding
        }

        // The length of the data part (before padding) modulo 4 cannot be 1.
        // This is because Base64 encodes data in 3-byte chunks into 4-character chunks.
        if (dataLength % 4 == 1) {
            return false
        }

        // If all checks have passed, it's a valid Base64 string
        return true
    }
}
