package id.walt.cose

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

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

}
