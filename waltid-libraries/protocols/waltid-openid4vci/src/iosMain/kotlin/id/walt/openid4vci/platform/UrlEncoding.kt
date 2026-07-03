package id.walt.openid4vci.platform

actual fun urlEncode(value: String): String = buildString {
    for (byte in value.encodeToByteArray()) {
        val unsigned = byte.toInt() and 0xff
        val char = unsigned.toChar()
        if (char.isUrlUnreserved()) {
            append(char)
        } else {
            append('%')
            append(unsigned.toString(16).uppercase().padStart(2, '0'))
        }
    }
}

private fun Char.isUrlUnreserved(): Boolean =
    this in 'A'..'Z' ||
        this in 'a'..'z' ||
        this in '0'..'9' ||
        this == '-' ||
        this == '_' ||
        this == '.' ||
        this == '~'
