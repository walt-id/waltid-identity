package id.walt.certificate.der

import kotlinx.io.bytestring.ByteString
import kotlin.io.encoding.Base64

internal object ByteArrayUtil {

    fun byteArrayToHex(bytes: ByteArray): String {
        val result = bytes.asUByteArray()
            .joinToString("") { it.toString(16).padStart(2, '0') }
        return result
    }

    fun byteArrayToBase64(bytes: ByteArray): String =
        Base64.encode(bytes)

    fun byteStringToBase64Pem(bytes: ByteString, type: String): String =
        byteArrayToBase64Pem(bytes.toByteArray(), type)

    fun byteArrayToBase64Pem(bytes: ByteArray, type: String): String {
        val rawBase64 = byteArrayToBase64(bytes)
        val body = rawBase64.chunked(64).joinToString(separator = "\n")
        return "-----BEGIN $type-----\n$body\n-----END $type-----"
    }

    fun byteArrayOfBase64(base64: String) =
        Base64.decode(base64)

    fun byteArrayOfHex(hexString: String): ByteArray {
        check(hexString.length % 2 == 0) { "Must have an even length" }
        return hexString.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }


    // Helper extension to view bytes as Hex
    fun ByteArray.toHex(): String = byteArrayToHex(this)

    fun ByteArray.toBase64(): String = byteArrayToBase64(this)
}

