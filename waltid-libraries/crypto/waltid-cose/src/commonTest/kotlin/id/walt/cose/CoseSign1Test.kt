package id.walt.cose

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.encodeToHexString
import kotlin.test.*

@OptIn(ExperimentalSerializationApi::class)
class CoseSign1Test {

    @Test
    fun `1 - CoseHeaders serializer should enforce canonical order`() {
        println("-- Start: 1 - CoseHeaders serializer should enforce canonical order --")
        // Build headers in a non-canonical order
        val headers = CoseHeaders(
            algorithm = Cose.Algorithm.ES256, // label 1
            contentType = CoseContentType.AsString("application/json"), // label 3
            kid = "test-key-01".encodeToByteArray() // label 4
        )
        println("Headers: $headers")

        // Serialize to bytes
        val encoded = Cbor.CoseCompliant.encodeToHexString(headers)
        println("Encoded: $encoded")

        // a3: map of 3 pairs
        // 01: key 'alg' (1) -> 26: value -7
        // 03: key 'contentType' (3) -> 70: tstr(16) -> "application/json"
        // 04: key 'kid' (4) -> 4b: bstr(11) -> "test-key-01"
        val expectedHex = "a3012603706170706c69636174696f6e2f6a736f6e044b746573742d6b65792d3031"

        assertEquals(expectedHex, encoded, "Headers were not serialized into the correct canonical CBOR representation.")

        println("-- End: 1 - CoseHeaders serializer should enforce canonical order --")
    }

    @Test
    fun `2 - Should create and verify a signature successfully`() = runTest {
        println("-- Start: 2 - Should create and verify a signature successfully --")
        val protectedHeaders = CoseHeaders(algorithm = Cose.Algorithm.ES256)
        val payload = "This is a test payload.".encodeToByteArray()

        val coseSign1 = CoseSign1.createAndSign(
            protectedHeaders = protectedHeaders,
            payload = payload,
            signer = CoseTestFixtures.signer
        )
        println("Created CoseSign1: $coseSign1")

        println("Verification should succeed with the correct key and payload")
        val isValid = coseSign1.verify(CoseTestFixtures.verifier)
        println("Is valid: $isValid (expected: true)")
        assertTrue(isValid, "Signature verification should succeed.")

        println("-- End: 2 - Should create and verify a signature successfully --")
    }

    @Test
    fun `3 - Should fail verification if payload is tampered`() = runTest {
        println("-- Start: 3 - Should fail verification if payload is tampered --")
        val protectedHeaders = CoseHeaders(algorithm = Cose.Algorithm.ES256)
        val payload = "Original payload.".encodeToByteArray()

        val coseSign1 = CoseSign1.createAndSign(
            protectedHeaders = protectedHeaders,
            payload = payload,
            signer = CoseTestFixtures.signer
        )
        println("Created CoseSign1: $coseSign1")

        println("Tamper with the payload before verification")
        val tamperedCose = coseSign1.copy(payload = "Tampered payload.".encodeToByteArray())
        println("Tampered CoseSign1: $tamperedCose")

        val isValid = tamperedCose.verify(CoseTestFixtures.verifier)
        println("Is valid: $isValid (expected: false)")
        assertFalse(isValid, "Signature verification should fail for a tampered payload.")

        println("-- End: 3 - Should fail verification if payload is tampered --")
    }

    @Test
    fun `4 - Should fail verification if protected headers are tampered`() = runTest {
        println("-- Start: 4 - Should fail verification if protected headers are tampered --")
        val originalHeaders = CoseHeaders(algorithm = Cose.Algorithm.ES256)
        val payload = "Payload.".encodeToByteArray()

        val coseSign1 = CoseSign1.createAndSign(
            protectedHeaders = originalHeaders,
            payload = payload,
            signer = CoseTestFixtures.signer
        )
        println("Created CoseSign1: $coseSign1")

        println("Create tampered headers and re-encode them")
        val tamperedHeaders = CoseHeaders(algorithm = Cose.Algorithm.ES384)
        val tamperedProtectedBytes = coseCompliantCbor.encodeToByteArray(tamperedHeaders)

        println("Tamper with the protected header bytes")
        val tamperedCose = coseSign1.copy(protected = tamperedProtectedBytes)
        println("Tampered CoseSign1: $tamperedCose")

        val isValid = tamperedCose.verify(CoseTestFixtures.verifier)
        println("Is valid: $isValid (expected: false)")
        assertFalse(isValid, "Signature verification should fail for tampered protected headers.")

        println("-- End: 4 - Should fail verification if protected headers are tampered --")
    }

    @Test
    fun `5_1 - Should successfully roundtrip serialize and deserialize int contentType`() = runTest {
        println("-- Start: 5.1 - Should successfully roundtrip (serialize and deserialize) for int content type --")
        val protectedHeaders = CoseHeaders(
            algorithm = Cose.Algorithm.ES256,
            kid = "test-key-01".encodeToByteArray()
        )
        val unprotectedHeaders = CoseHeaders(contentType = CoseContentType.AsInt(11))
        val payload = "Roundtrip test.".encodeToByteArray()

        val originalCose = CoseSign1.createAndSign(
            protectedHeaders = protectedHeaders,
            unprotectedHeaders = unprotectedHeaders,
            payload = payload,
            signer = CoseTestFixtures.signer
        )
        println("Original CoseSign1: $originalCose")

        val cborBytes = originalCose.toTagged()
        val decodedCose = CoseSign1.fromTagged(cborBytes)
        println("Decoded CoseSign1: $decodedCose")

        // Check if the decoded object is valid
        assertTrue(decodedCose.verify(CoseTestFixtures.verifier), "Decoded object should be verifiable.")
        assertEquals(originalCose, decodedCose)

        println("-- End: 5.1 - Should successfully roundtrip (serialize and deserialize) for int content type --")
    }

    @Test
    fun `5_2 - Should successfully roundtrip serialize and deserialize string contentType`() = runTest {
        println("-- Start: 5 - Should successfully roundtrip (serialize and deserialize) for string content type --")
        val protectedHeaders = CoseHeaders(
            algorithm = Cose.Algorithm.ES256,
            kid = "test-key-01".encodeToByteArray()
        )
        val unprotectedHeaders = CoseHeaders(contentType = CoseContentType.AsString("text/plain"))
        val payload = "Roundtrip test.".encodeToByteArray()

        val originalCose = CoseSign1.createAndSign(
            protectedHeaders = protectedHeaders,
            unprotectedHeaders = unprotectedHeaders,
            payload = payload,
            signer = CoseTestFixtures.signer
        )
        println("Original CoseSign1: $originalCose")

        val cborBytes = originalCose.toTagged()
        val decodedCose = CoseSign1.fromTagged(cborBytes)
        println("Decoded CoseSign1: $decodedCose")

        // Check if the decoded object is valid
        assertTrue(decodedCose.verify(CoseTestFixtures.verifier), "Decoded object should be verifiable.")
        assertEquals(originalCose, decodedCose)

        println("-- End: 5 - Should successfully roundtrip (serialize and deserialize) for string content type --")
    }

    @Test
    fun `6 - Should handle null payload correctly`() = runTest {
        println("-- Start: 6 - Should handle null payload --")
        val protectedHeaders = CoseHeaders(algorithm = Cose.Algorithm.ES256)

        val coseSign1 = CoseSign1.createAndSign(
            protectedHeaders = protectedHeaders,
            payload = null, // Null payload
            signer = CoseTestFixtures.signer
        )
        println("Created CoseSign1: $coseSign1")

        assertNull(coseSign1.payload, "Payload should be null in the created object.")

        val isValid = coseSign1.verify(CoseTestFixtures.verifier)
        println("Is valid: $isValid (expected: true)")
        assertTrue(isValid, "Signature with null payload should be verifiable.")
        println("-- End: 6 - Should handle null payload --")
    }

    @Test
    fun `7 - Should handle external authenticated data AAD correctly`() = runTest {
        println("-- Start: 7 - Should handle external authenticated data (AAD) correctly --")
        val protectedHeaders = CoseHeaders(algorithm = Cose.Algorithm.ES256)
        val payload = "Payload.".encodeToByteArray()
        val externalAad = "External Authenticated Data".encodeToByteArray()

        val coseSign1 = CoseSign1.createAndSign(
            protectedHeaders = protectedHeaders,
            payload = payload,
            signer = CoseTestFixtures.signer,
            externalAad = externalAad
        )
        println("Created CoseSign1: $coseSign1")

        println("Verification must succeed with the same AAD")
        assertTrue(
            coseSign1.verify(CoseTestFixtures.verifier, externalAad = externalAad),
            "Verification should succeed with correct AAD."
        )

        println("Verification must fail with different AAD")
        assertFalse(
            coseSign1.verify(CoseTestFixtures.verifier, externalAad = "Different AAD".encodeToByteArray()),
            "Verification should fail with incorrect AAD."
        )

        println("-- End: 7 - Should handle external authenticated data (AAD) correctly --")
    }
}
