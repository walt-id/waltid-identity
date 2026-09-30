package id.walt.cose

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertContentEquals

class CoseMac0Test {

    @Test
    fun `Should create and verify a COSE_Mac0 successfully`() = runTest {
        // 1. 32-byte (256-bit) shared secret key
        val secretKeyBytes = "our-secret-key-that-is-32-bytes-long".encodeToByteArray()
        val hmacKey = CoseHmacKey(secretKeyBytes)
        println("HMAC Key: $hmacKey")

        // 2. Define COSE parameters
        val protectedHeaders = CoseHeaders(algorithm = Cose.Algorithm.HMAC_256)
        val payload = "This is the content.".encodeToByteArray()

        // 3. Create CoseMac0 object
        val coseMac0 = CoseMac0.createAndMac(
            protectedHeaders = protectedHeaders,
            payload = payload,
            creator = hmacKey.toCoseMacCreator(protectedHeaders.algorithm)
        )
        println("CoseMac0: $coseMac0")

        // 4. Verify tag
        val isValid = coseMac0.verify(
            verifier = hmacKey.toCoseMacVerifier(protectedHeaders.algorithm)
        )
        println("Verified: $isValid")

        assertTrue(isValid, "COSE_Mac0 verification should succeed.")
    }

    @Test
    fun `truncated HMAC rejects tampered and incorrectly sized tags`() = runTest {
        val key = CoseHmacKey(ByteArray(131) { 0xaa.toByte() })
        val payload = "Test Using Larger Than Block-Size Key - Hash Key First".encodeToByteArray()
        val creator = key.toCoseMacCreator(Cose.Algorithm.HMAC_256_64)
        val verifier = key.toCoseMacVerifier(Cose.Algorithm.HMAC_256_64)
        val tag = creator.mac(payload)
        // First eight bytes of RFC 4231 test case 6.
        assertContentEquals(byteArrayOf(0x60, 0xe4.toByte(), 0x31, 0x59, 0x1e, 0xe0.toByte(), 0xb6.toByte(), 0x7f), tag)
        assertTrue(verifier.verify(payload, tag))
        val invalidTags = listOf(
            tag.copyOf().apply { this[0] = (this[0].toInt() xor 1).toByte() },
            tag.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() },
            byteArrayOf(),
            tag.copyOf(7),
            tag + byteArrayOf(0),
        )
        invalidTags.forEach { assertFalse(verifier.verify(payload, it)) }
    }
}
