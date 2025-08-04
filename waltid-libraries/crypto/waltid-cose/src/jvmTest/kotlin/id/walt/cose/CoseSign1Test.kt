package id.walt.cose

import id.walt.crypto.keys.KeyManager
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.encodeToHexString
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalSerializationApi::class)
class CoseSign1Test {

    private val key = KeyManager.resolveSerializedKeyBlocking(
        """{"type": "jwk", "jwk": {
                    "kty": "EC",
                    "d": "GKZgxuL71bvt-nK9zfNSUKfxPzyqPqBFgBHQYxiRbaI",
                    "use": "sig",
                    "crv": "P-256",
                    "kid": "UNq3iiAPlwafoWkGe3g39w4-slPcKFSKb4q6Z6w11ZU",
                    "x": "aJrzbXcFoDU1gkVC06luExM4ZCbu-_MvXpImV48_E6E",
                    "y": "js-Yzh4FEoyG3ZN3CesYF4nNAnSqjZjY9_NafCS48Nw",
                    "alg": "ES256"
                }}"""
    )

    private val testSignerKey = key
    suspend fun testVerifierKey() = key.getPublicKey()

    @Test
    fun `1 - CoseHeaders serializer should enforce canonical order`() {
        println("-- Start: 1 - CoseHeaders serializer should enforce canonical order --")
        // Build headers in a non-canonical order
        val headers = CoseHeaders(
            alg = Cose.Algorithm.ES256, // label 1
            contentType = "application/json", // label 3
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
        val protectedHeaders = CoseHeaders(alg = Cose.Algorithm.ES256)
        val payload = "This is a test payload.".encodeToByteArray()

        val coseSign1 = CoseSign1.createAndSign(
            protectedHeaders = protectedHeaders,
            payload = payload,
            signer = testSignerKey.toCoseSigner()
        )
        println("Created CoseSign1: $coseSign1")

        println("Verification should succeed with the correct key and payload")
        val isValid = coseSign1.verify(testVerifierKey().toCoseVerifier())
        println("Is valid: $isValid (expected: true)")
        assertTrue(isValid, "Signature verification should succeed.")

        println("-- End: 2 - Should create and verify a signature successfully --")
    }

    @Test
    fun `3 - Should fail verification if payload is tampered`() = runTest {
        println("-- Start: 3 - Should fail verification if payload is tampered --")
        val protectedHeaders = CoseHeaders(alg = Cose.Algorithm.ES256)
        val payload = "Original payload.".encodeToByteArray()

        val coseSign1 = CoseSign1.createAndSign(
            protectedHeaders = protectedHeaders,
            payload = payload,
            signer = testSignerKey.toCoseSigner()
        )
        println("Created CoseSign1: $coseSign1")

        println("Tamper with the payload before verification")
        val tamperedCose = coseSign1.copy(payload = "Tampered payload.".encodeToByteArray())
        println("Tampered CoseSign1: $tamperedCose")

        val isValid = tamperedCose.verify(testVerifierKey().toCoseVerifier())
        println("Is valid: $isValid (expected: false)")
        assertFalse(isValid, "Signature verification should fail for a tampered payload.")

        println("-- End: 3 - Should fail verification if payload is tampered --")
    }

    @Test
    fun `4 - Should fail verification if protected headers are tampered`() = runTest {
        println("-- Start: 4 - Should fail verification if protected headers are tampered --")
        val originalHeaders = CoseHeaders(alg = Cose.Algorithm.ES256)
        val payload = "Payload.".encodeToByteArray()

        val coseSign1 = CoseSign1.createAndSign(
            protectedHeaders = originalHeaders,
            payload = payload,
            signer = testSignerKey.toCoseSigner()
        )
        println("Created CoseSign1: $coseSign1")

        println("Create tampered headers and re-encode them")
        val tamperedHeaders = CoseHeaders(alg = Cose.Algorithm.ES384)
        val tamperedProtectedBytes = coseCbor.encodeToByteArray(tamperedHeaders)

        println("Tamper with the protected header bytes")
        val tamperedCose = coseSign1.copy(protected = tamperedProtectedBytes)
        println("Tampered CoseSign1: $tamperedCose")

        val isValid = tamperedCose.verify(testVerifierKey().toCoseVerifier())
        println("Is valid: $isValid (expected: false)")
        assertFalse(isValid, "Signature verification should fail for tampered protected headers.")

        println("-- End: 4 - Should fail verification if protected headers are tampered --")
    }

    @Test
    fun `5 - Should successfully roundtrip (serialize and deserialize)`() = runTest {
        println("-- Start: 5 - Should successfully roundtrip (serialize and deserialize) --")
        val protectedHeaders = CoseHeaders(
            alg = Cose.Algorithm.ES256,
            kid = "test-key-01".encodeToByteArray()
        )
        val unprotectedHeaders = CoseHeaders(contentType = "text/plain")
        val payload = "Roundtrip test.".encodeToByteArray()

        val originalCose = CoseSign1.createAndSign(
            protectedHeaders = protectedHeaders,
            unprotectedHeaders = unprotectedHeaders,
            payload = payload,
            signer = testSignerKey.toCoseSigner()
        )
        println("Original CoseSign1: $originalCose")

        val cborBytes = coseCbor.encodeToByteArray(originalCose)
        val decodedCose = coseCbor.decodeFromByteArray<CoseSign1>(cborBytes)
        println("Decoded CoseSign1: $decodedCose")

        // Check if the decoded object is valid
        assertTrue(decodedCose.verify(testVerifierKey().toCoseVerifier()), "Decoded object should be verifiable.")
        assertContentEquals(originalCose.payload, decodedCose.payload)
        assertContentEquals(originalCose.signature, decodedCose.signature)

        println("-- End: 5 - Should successfully roundtrip (serialize and deserialize) --")
    }

    @Test
    fun `6 - Should handle null payload correctly`() = runTest {
        println("-- Start: 6 - Should handle null payload --")
        val protectedHeaders = CoseHeaders(alg = Cose.Algorithm.ES256)

        val coseSign1 = CoseSign1.createAndSign(
            protectedHeaders = protectedHeaders,
            payload = null, // Null payload
            signer = testSignerKey.toCoseSigner()
        )
        println("Created CoseSign1: $coseSign1")

        assertNull(coseSign1.payload, "Payload should be null in the created object.")

        val isValid = coseSign1.verify(testVerifierKey().toCoseVerifier())
        println("Is valid: $isValid (expected: true)")
        assertTrue(isValid, "Signature with null payload should be verifiable.")
        println("-- End: 6 - Should handle null payload --")
    }

    @Test
    fun `7 - Should handle external authenticated data (AAD) correctly`() = runTest {
        println("-- Start: 7 - Should handle external authenticated data (AAD) correctly --")
        val protectedHeaders = CoseHeaders(alg = Cose.Algorithm.ES256)
        val payload = "Payload.".encodeToByteArray()
        val externalAad = "External Authenticated Data".encodeToByteArray()

        val coseSign1 = CoseSign1.createAndSign(
            protectedHeaders = protectedHeaders,
            payload = payload,
            signer = testSignerKey.toCoseSigner(),
            externalAad = externalAad
        )
        println("Created CoseSign1: $coseSign1")

        println("Verification must succeed with the same AAD")
        assertTrue(
            coseSign1.verify(testVerifierKey().toCoseVerifier(), externalAad = externalAad),
            "Verification should succeed with correct AAD."
        )

        println("Verification must fail with different AAD")
        assertFalse(
            coseSign1.verify(testVerifierKey().toCoseVerifier(), externalAad = "Different AAD".encodeToByteArray()),
            "Verification should fail with incorrect AAD."
        )

        println("-- End: 7 - Should handle external authenticated data (AAD) correctly --")
    }
}
