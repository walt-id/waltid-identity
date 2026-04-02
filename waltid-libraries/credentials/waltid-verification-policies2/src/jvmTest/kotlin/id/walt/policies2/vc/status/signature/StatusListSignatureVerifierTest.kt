package id.walt.policies2.vc.status.signature

import id.walt.cose.CoseSign1
import id.walt.cose.CoseHeaders
import id.walt.cose.toCoseSigner
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.did.dids.DidService
import id.walt.policies2.vc.policies.status.signature.KeyResolutionFailedException
import id.walt.policies2.vc.policies.status.signature.SignatureInvalidException
import id.walt.policies2.vc.policies.status.signature.StatusListSignatureVerifier
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@DisplayName("StatusListSignatureVerifier Tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StatusListSignatureVerifierTest {

    private lateinit var verifier: StatusListSignatureVerifier
    private lateinit var testKey: JWKKey
    private lateinit var testDid: String

    @BeforeAll
    fun setup() = runTest {
        DidService.minimalInit()
        verifier = StatusListSignatureVerifier()
        testKey = JWKKey.generate(KeyType.Ed25519)
        val didResult = DidService.registerByKey("key", testKey)
        testDid = didResult.did
    }

    @Nested
    @DisplayName("JWT Verification Tests")
    inner class JwtVerificationTests {

        @Test
        @DisplayName("Should verify valid JWT with DID kid")
        fun shouldVerifyValidJwtWithDidKid() = runTest {
            val payload = buildJsonObject {
                put("iss", testDid)
                put("sub", testDid)
                put("status_list", buildJsonObject {
                    put("bits", 1)
                    put("lst", "test")
                })
            }
            
            val jwt = createSignedJwt(testKey, testDid, payload)
            
            val result = verifier.verifyJwt(jwt)
            
            assertTrue(result.isSuccess)
            val verifiedPayload = result.getOrThrow()
            assertEquals(testDid, verifiedPayload["iss"]?.toString()?.trim('"'))
        }

        @Test
        @DisplayName("Should verify valid JWT with JWK in header")
        fun shouldVerifyValidJwtWithJwkHeader() = runTest {
            val payload = buildJsonObject {
                put("iss", "test-issuer")
                put("status_list", buildJsonObject {
                    put("bits", 1)
                    put("lst", "test")
                })
            }
            
            val jwt = createSignedJwtWithJwk(testKey, payload)
            
            val result = verifier.verifyJwt(jwt)
            
            assertTrue(result.isSuccess)
        }

        @Test
        @DisplayName("Should fail verification for tampered JWT")
        fun shouldFailForTamperedJwt() = runTest {
            val payload = buildJsonObject {
                put("iss", testDid)
                put("status_list", buildJsonObject {
                    put("bits", 1)
                    put("lst", "test")
                })
            }
            
            val jwt = createSignedJwt(testKey, testDid, payload)
            
            // Tamper with the signature
            val parts = jwt.split(".")
            val tamperedJwt = "${parts[0]}.${parts[1]}.${parts[2].reversed()}"
            
            val result = verifier.verifyJwt(tamperedJwt)
            
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is SignatureInvalidException)
        }

        @Test
        @DisplayName("Should fail for JWT with unresolvable DID")
        fun shouldFailForUnresolvableDid() = runTest {
            val payload = buildJsonObject {
                put("iss", "did:unknown:12345")
                put("status_list", buildJsonObject {
                    put("bits", 1)
                    put("lst", "test")
                })
            }
            
            // Create a JWT with an unresolvable DID in kid
            val header = buildJsonObject {
                put("alg", "EdDSA")
                put("typ", "statuslist+jwt")
                put("kid", "did:unknown:12345#key-1")
            }
            
            val headerB64 = Json.encodeToString(JsonObject.serializer(), header).encodeToByteArray().encodeToBase64Url()
            val payloadB64 = Json.encodeToString(JsonObject.serializer(), payload).encodeToByteArray().encodeToBase64Url()
            val signatureB64 = "fake_signature".encodeToByteArray().encodeToBase64Url()
            
            val jwt = "$headerB64.$payloadB64.$signatureB64"
            
            val result = verifier.verifyJwt(jwt)
            
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is KeyResolutionFailedException)
        }

        @Test
        @DisplayName("Should fail for JWT without key information")
        fun shouldFailForJwtWithoutKeyInfo() = runTest {
            val header = buildJsonObject {
                put("alg", "EdDSA")
                put("typ", "statuslist+jwt")
            }
            
            val payload = buildJsonObject {
                put("iss", "test-issuer")
            }
            
            val headerB64 = Json.encodeToString(JsonObject.serializer(), header).encodeToByteArray().encodeToBase64Url()
            val payloadB64 = Json.encodeToString(JsonObject.serializer(), payload).encodeToByteArray().encodeToBase64Url()
            val signatureB64 = "fake_signature".encodeToByteArray().encodeToBase64Url()
            
            val jwt = "$headerB64.$payloadB64.$signatureB64"
            
            val result = verifier.verifyJwt(jwt)
            
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is KeyResolutionFailedException)
        }
    }

    @Nested
    @DisplayName("CWT Verification Tests")
    inner class CwtVerificationTests {

        @Test
        @DisplayName("Should verify valid CWT with DID kid")
        fun shouldVerifyValidCwtWithDidKid() = runTest {
            val payload = "test_payload".encodeToByteArray()
            
            val cwt = createSignedCwt(testKey, testDid, payload)
            
            val result = verifier.verifyCwt(cwt)
            
            assertTrue(result.isSuccess)
            assertNotNull(result.getOrThrow())
        }

        @Test
        @DisplayName("Should verify valid CWT from hex string")
        fun shouldVerifyValidCwtFromHex() = runTest {
            val payload = "test_payload".encodeToByteArray()
            
            val cwt = createSignedCwt(testKey, testDid, payload)
            val cwtHex = cwt.joinToString("") { "%02x".format(it) }
            
            val result = verifier.verifyCwtFromHex(cwtHex)
            
            assertTrue(result.isSuccess)
        }

        @Test
        @DisplayName("Should fail verification for tampered CWT")
        fun shouldFailForTamperedCwt() = runTest {
            val payload = "test_payload".encodeToByteArray()
            
            val cwt = createSignedCwt(testKey, testDid, payload)
            
            // Tamper with the last byte (signature)
            val tamperedCwt = cwt.copyOf()
            tamperedCwt[tamperedCwt.size - 1] = (tamperedCwt[tamperedCwt.size - 1] + 1).toByte()
            
            val result = verifier.verifyCwt(tamperedCwt)
            
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is SignatureInvalidException)
        }

        @Test
        @DisplayName("Should fail for invalid hex string")
        fun shouldFailForInvalidHex() = runTest {
            val result = verifier.verifyCwtFromHex("not_valid_hex!")
            
            assertTrue(result.isFailure)
        }
    }

    private suspend fun createSignedJwt(key: JWKKey, did: String, payload: JsonObject): String {
        val header = buildJsonObject {
            put("alg", "EdDSA")
            put("typ", "statuslist+jwt")
            put("kid", "$did#${key.getKeyId()}")
        }
        
        val headerB64 = Json.encodeToString(JsonObject.serializer(), header).encodeToByteArray().encodeToBase64Url()
        val payloadB64 = Json.encodeToString(JsonObject.serializer(), payload).encodeToByteArray().encodeToBase64Url()
        
        val signable = "$headerB64.$payloadB64"
        val signature = key.signRaw(signable.encodeToByteArray()) as ByteArray
        val signatureB64 = signature.encodeToBase64Url()
        
        return "$signable.$signatureB64"
    }

    private suspend fun createSignedJwtWithJwk(key: JWKKey, payload: JsonObject): String {
        val publicKeyJwk = Json.parseToJsonElement(key.getPublicKey().exportJWK())
        
        val header = buildJsonObject {
            put("alg", "EdDSA")
            put("typ", "statuslist+jwt")
            put("jwk", publicKeyJwk)
        }
        
        val headerB64 = Json.encodeToString(JsonObject.serializer(), header).encodeToByteArray().encodeToBase64Url()
        val payloadB64 = Json.encodeToString(JsonObject.serializer(), payload).encodeToByteArray().encodeToBase64Url()
        
        val signable = "$headerB64.$payloadB64"
        val signature = key.signRaw(signable.encodeToByteArray()) as ByteArray
        val signatureB64 = signature.encodeToBase64Url()
        
        return "$signable.$signatureB64"
    }

    private suspend fun createSignedCwt(key: JWKKey, did: String, payload: ByteArray): ByteArray {
        val protectedHeaders = CoseHeaders(
            algorithm = -8, // EdDSA
            contentType = id.walt.cose.CoseContentType.AsString("statuslist+cwt")
        )
        
        val unprotectedHeaders = CoseHeaders(
            kid = "$did#${key.getKeyId()}".encodeToByteArray()
        )
        
        val signer = key.toCoseSigner()
        
        val coseSign1 = CoseSign1.createAndSign(
            protectedHeaders = protectedHeaders,
            unprotectedHeaders = unprotectedHeaders,
            payload = payload,
            signer = signer
        )
        
        return coseSign1.toTagged()
    }
}
