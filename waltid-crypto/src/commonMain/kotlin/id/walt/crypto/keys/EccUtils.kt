package id.walt.crypto.keys

object EccUtils {

    private fun ByteArray.padStart(length: Int): ByteArray {
        if (this.size >= length) return this
        val padded = ByteArray(length)
        this.copyInto(padded, length - this.size)
        return padded
    }

    /**
     * Primitive helper function to convert DER encoded
     * signature formats to IEEE P1363 encoded formats,
     * e.g., for use in JWTs
     *
     * for EC: Secp256r1 & Secp256k1
     */
    fun convertDERtoIEEEP1363(sig: ByteArray): ByteArray {
        val rLength = sig[3].toInt()
        val r = sig.copyOfRange(4, 4 + rLength)
        val s = sig.copyOfRange(4 + rLength + 2, sig.size)

        // Ensure r and s are always 32 bytes
        val rPadded = r.padStart(32)
        val sPadded = s.padStart(32)

        return rPadded + sPadded
    }

}
