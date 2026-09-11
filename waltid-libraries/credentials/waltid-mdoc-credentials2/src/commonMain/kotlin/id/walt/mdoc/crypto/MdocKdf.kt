package id.walt.mdoc.crypto

import org.kotlincrypto.macs.hmac.sha2.HmacSHA256

/** RFC 5869 HKDF-SHA-256 used by ISO mdoc session and device-MAC key derivation. */
object MdocKdf {
    private const val HASH_BYTES = 32

    fun deriveSha256(
        inputKeyMaterial: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        length: Int,
    ): ByteArray {
        require(length in 1..(255 * HASH_BYTES)) { "HKDF output length is outside RFC 5869 limits" }
        val pseudoRandomKey = HmacSHA256(salt).doFinal(inputKeyMaterial)
        val output = ByteArray(length)
        var previous = ByteArray(0)
        var written = 0
        var block = 1
        try {
            while (written < length) {
                val next = HmacSHA256(pseudoRandomKey).doFinal(previous + info + block.toByte())
                previous.fill(0)
                previous = next
                val count = minOf(previous.size, length - written)
                previous.copyInto(output, destinationOffset = written, endIndex = count)
                written += count
                block++
            }
            return output
        } finally {
            pseudoRandomKey.fill(0)
            previous.fill(0)
        }
    }
}
