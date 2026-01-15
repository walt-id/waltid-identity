package id.walt.iso18013.annexc

import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.interfaces.ECPrivateKey
import java.security.spec.ECGenParameterSpec

object SkrGenerator {
    private val p256Order =
        BigInteger("ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551", 16)

    fun generateSkRHex(): String {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1")) // aka P-256
        val kp = kpg.generateKeyPair()
        val priv = kp.private as ECPrivateKey

        // ECPrivateKey.s is the private scalar (BigInteger)
        val d = priv.s.toByteArray()

        return normalizeToP256Scalar(d).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    /**
     * Deterministically derives a valid P-256 private scalar for use in test vectors.
     *
     * Note: This is for scaffolding/unit tests only; it does not correspond to any real captured session key.
     */
    fun generateSkRHexForTestVector(testVectorId: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(testVectorId.toByteArray(Charsets.UTF_8))
        val x = BigInteger(1, digest)
        val d = x.mod(p256Order.subtract(BigInteger.ONE)).add(BigInteger.ONE)
        return normalizeToP256Scalar(d.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun normalizeToP256Scalar(d: ByteArray): ByteArray {
        // Normalize to 32 bytes (BigInteger may add a leading 0x00)
        return when {
            d.size == 32 -> d
            d.size == 33 && d[0] == 0.toByte() -> d.copyOfRange(1, 33)
            d.size < 32 -> ByteArray(32 - d.size) + d
            else -> error("Unexpected private scalar length: ${d.size}")
        }
    }
}
