package id.walt.certificate.x509.dn

internal object RelativeDistinguishedNameEscapingUtil {

    fun unescapeString(escapedString: String): String =
        escapedString.replace("\\", "")

    /**
     * Escape string in accordance with RFC 4514 section 2.4
     *
     *  - a space (' ' U+0020) or number sign ('#' U+0023) occurring at
     *    the beginning of the string;
     *
     * - a space (' ' U+0020) character occurring at the end of the string;
     *
     * - one of the characters '"', '+', ',', ';', '<', '>',  or '\'
     *   (U+0022, U+002B, U+002C, U+003B, U+003C, U+003E, or U+005C,
     *   respectively);
     */
    fun escapeString(unescapedString: String): String {
        var escapedString = unescapedString
        charsToEscape.forEach {
            escapedString = escapedString.replace(it, "\\$it")
        }
        if (escapedString.startsWith(" ") || escapedString.startsWith("#")) {
            escapedString = "\\$escapedString"
        }
        if (escapedString.endsWith(" ")) {
            escapedString = "${escapedString.substring(0, escapedString.length - 1)}\\ "
        }
        return escapedString
    }


    private val charsToEscape = listOf(
        0x5C.toChar().toString(), // '\' needs to be escaped first to prevent escaping it multiple times
        0x22.toChar().toString(),
        0x2B.toChar().toString(),
        0x2C.toChar().toString(),
        0x3B.toChar().toString(),
        0x3C.toChar().toString(),
        0x3E.toChar().toString()
    )
}