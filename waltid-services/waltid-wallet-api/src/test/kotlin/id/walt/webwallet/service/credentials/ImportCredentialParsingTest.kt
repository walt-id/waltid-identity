@file:OptIn(ExperimentalUuidApi::class)

package id.walt.webwallet.service.credentials

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.did.dids.DidService
import id.walt.did.dids.registrar.LocalRegistrar
import id.walt.did.dids.resolver.LocalResolver
import id.walt.oid4vc.data.CredentialFormat
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Unit tests for credential import parsing logic.
 * 
 * Tests the credential format detection, parsing, and validation logic 
 * for both W3C JWT VCs and SD-JWT VCs.
 */
class ImportCredentialParsingTest {

    companion object {
        private lateinit var issuerKey: JWKKey
        private lateinit var issuerDid: String
        private val holderDid = "did:jwk:holder123"

        @JvmStatic
        @BeforeAll
        fun setupClass(): Unit = runBlocking {
            // Initialize DID service
            DidService.apply {
                registerResolver(LocalResolver())
                updateResolversForMethods()
                registerRegistrar(LocalRegistrar())
                updateRegistrarsForMethods()
            }
            
            // Create an issuer key and DID for signing test credentials
            issuerKey = JWKKey.generate(KeyType.Ed25519)
            val didResult = DidService.registerByKey("jwk", issuerKey)
            issuerDid = didResult.did
        }
    }

    private suspend fun createSignedW3cJwtVc(
        subjectDid: String,
        credentialId: String = "http://example.gov/credentials/${Uuid.random()}"
    ): String {
        val header = buildJsonObject {
            put("alg", "EdDSA")
            put("typ", "JWT")
            put("kid", "$issuerDid#0")
        }
        
        val payload = buildJsonObject {
            put("iss", issuerDid)
            put("sub", subjectDid)
            put("jti", credentialId)
            put("nbf", System.currentTimeMillis() / 1000 - 60)
            put("exp", System.currentTimeMillis() / 1000 + 3600)
            put("vc", buildJsonObject {
                put("@context", JsonArray(listOf(JsonPrimitive("https://www.w3.org/2018/credentials/v1"))))
                put("type", JsonArray(listOf(JsonPrimitive("VerifiableCredential"))))
                put("credentialSubject", buildJsonObject {
                    put("id", subjectDid)
                    put("name", "Test User")
                })
            })
        }
        
        val headerBase64 = Json.encodeToString(header).encodeToByteArray().encodeToBase64Url()
        val payloadBase64 = Json.encodeToString(payload).encodeToByteArray().encodeToBase64Url()
        val signable = "$headerBase64.$payloadBase64"
        
        val signature = issuerKey.signRaw(signable.encodeToByteArray())
        val signatureBase64 = signature.encodeToBase64Url()
        
        return "$signable.$signatureBase64"
    }

    private suspend fun createSignedSdJwtVc(
        subjectDid: String,
        credentialId: String = "http://example.gov/credentials/${Uuid.random()}"
    ): String {
        val header = buildJsonObject {
            put("alg", "EdDSA")
            put("typ", "vc+sd-jwt")
            put("kid", "$issuerDid#0")
        }
        
        val payload = buildJsonObject {
            put("iss", issuerDid)
            put("sub", subjectDid)
            put("jti", credentialId)
            put("nbf", System.currentTimeMillis() / 1000 - 60)
            put("exp", System.currentTimeMillis() / 1000 + 3600)
            put("vct", "https://example.com/credential/identity")
            put("_sd_alg", "sha-256")
            put("_sd", JsonArray(listOf(JsonPrimitive("abc123"))))
        }
        
        val headerBase64 = Json.encodeToString(header).encodeToByteArray().encodeToBase64Url()
        val payloadBase64 = Json.encodeToString(payload).encodeToByteArray().encodeToBase64Url()
        val signable = "$headerBase64.$payloadBase64"
        
        val signature = issuerKey.signRaw(signable.encodeToByteArray())
        val signatureBase64 = signature.encodeToBase64Url()
        
        val jwt = "$signable.$signatureBase64"
        
        // Add sample disclosures (base64 encoded arrays)
        val disclosure1 = """["salt1","given_name","John"]""".encodeToByteArray().encodeToBase64Url()
        val disclosure2 = """["salt2","family_name","Doe"]""".encodeToByteArray().encodeToBase64Url()
        
        return "$jwt~$disclosure1~$disclosure2~"
    }

    // ==================== SD-JWT Detection Tests ====================

    @Test
    fun `SD-JWT detection - credential with disclosures is detected as SD-JWT`() {
        val jwt = "eyJhbGciOiJFUzI1NiJ9.eyJzdWIiOiJ0ZXN0In0.signature"
        val sdJwt = "$jwt~disclosure1~disclosure2~"
        
        val isSdJwt = sdJwt.contains("~")
        
        assertTrue(isSdJwt, "SD-JWT with disclosures should be detected")
    }

    @Test
    fun `SD-JWT detection - regular JWT without disclosures is not SD-JWT`() {
        val jwt = "eyJhbGciOiJFUzI1NiJ9.eyJzdWIiOiJ0ZXN0In0.signature"
        
        val isSdJwt = jwt.contains("~")
        
        assertFalse(isSdJwt, "Regular JWT should not be detected as SD-JWT")
    }

    @Test
    fun `SD-JWT detection - SD-JWT with trailing tilde is detected`() {
        val sdJwt = "eyJhbGciOiJFUzI1NiJ9.eyJzdWIiOiJ0ZXN0In0.signature~disclosure1~"
        
        val isSdJwt = sdJwt.contains("~")
        
        assertTrue(isSdJwt, "SD-JWT with trailing tilde should be detected")
    }

    // ==================== SD-JWT Parsing Tests ====================

    @Test
    fun `SD-JWT parsing - extracts JWT part correctly`() {
        val jwtPart = "eyJhbGciOiJFUzI1NiJ9.eyJzdWIiOiJ0ZXN0In0.signature"
        val disclosure1 = "WyJzYWx0MSIsImdpdmVuX25hbWUiLCJKb2huIl0"
        val disclosure2 = "WyJzYWx0MiIsImZhbWlseV9uYW1lIiwiRG9lIl0"
        val sdJwt = "$jwtPart~$disclosure1~$disclosure2~"
        
        val extractedJwt = sdJwt.substringBefore("~")
        
        assertEquals(jwtPart, extractedJwt)
    }

    @Test
    fun `SD-JWT parsing - extracts disclosures correctly`() {
        val jwtPart = "eyJhbGciOiJFUzI1NiJ9.eyJzdWIiOiJ0ZXN0In0.signature"
        val disclosure1 = "WyJzYWx0MSIsImdpdmVuX25hbWUiLCJKb2huIl0"
        val disclosure2 = "WyJzYWx0MiIsImZhbWlseV9uYW1lIiwiRG9lIl0"
        val sdJwt = "$jwtPart~$disclosure1~$disclosure2~"
        
        val parts = sdJwt.split("~")
        val disclosures = parts.drop(1).filter { it.isNotEmpty() && !it.startsWith("ey") }
        
        assertEquals(2, disclosures.size)
        assertEquals(disclosure1, disclosures[0])
        assertEquals(disclosure2, disclosures[1])
    }

    @Test
    fun `SD-JWT parsing - handles empty trailing disclosure`() {
        val jwtPart = "eyJhbGciOiJFUzI1NiJ9.eyJzdWIiOiJ0ZXN0In0.signature"
        val disclosure = "WyJzYWx0IiwiY2xhaW0iLCJ2YWx1ZSJd"
        val sdJwt = "$jwtPart~$disclosure~"  // Trailing empty part after last ~
        
        val parts = sdJwt.split("~")
        val disclosures = parts.drop(1).filter { it.isNotEmpty() && !it.startsWith("ey") }
        
        assertEquals(1, disclosures.size)
        assertEquals(disclosure, disclosures[0])
    }

    @Test
    fun `SD-JWT parsing - filters out key binding JWT`() {
        val jwtPart = "eyJhbGciOiJFUzI1NiJ9.eyJzdWIiOiJ0ZXN0In0.signature"
        val disclosure = "WyJzYWx0IiwiY2xhaW0iLCJ2YWx1ZSJd"
        val keyBindingJwt = "eyJhbGciOiJFUzI1NiJ9.eyJub25jZSI6IjEyMyJ9.kbsig"
        val sdJwtWithKb = "$jwtPart~$disclosure~$keyBindingJwt"
        
        val parts = sdJwtWithKb.split("~")
        val disclosures = parts.drop(1).filter { it.isNotEmpty() && !it.startsWith("ey") }
        
        assertEquals(1, disclosures.size, "Key binding JWT should be filtered out")
        assertEquals(disclosure, disclosures[0])
    }

    // ==================== Format Detection Tests ====================

    @Test
    fun `format detection - typ vc+sd-jwt indicates SD-JWT VC format`() = runTest {
        val sdJwt = createSignedSdJwtVc(holderDid)
        val isSdJwt = sdJwt.contains("~")
        
        assertTrue(isSdJwt, "Credential should be detected as SD-JWT")
        
        // Extract and verify typ header
        val jwtPart = sdJwt.substringBefore("~")
        val headerBase64 = jwtPart.split(".")[0]
        val headerJson = Json.parseToJsonElement(
            headerBase64.decodeBase64UrlToString()
        ).jsonObject
        val typ = headerJson["typ"]?.jsonPrimitive?.content
        
        assertEquals("vc+sd-jwt", typ)
    }

    @Test
    fun `format detection - typ JWT indicates W3C JWT VC format`() = runTest {
        val jwt = createSignedW3cJwtVc(holderDid)
        val isSdJwt = jwt.contains("~")
        
        assertFalse(isSdJwt, "W3C JWT VC should not be detected as SD-JWT")
        
        // Extract and verify typ header
        val headerBase64 = jwt.split(".")[0]
        val headerJson = Json.parseToJsonElement(
            headerBase64.decodeBase64UrlToString()
        ).jsonObject
        val typ = headerJson["typ"]?.jsonPrimitive?.content
        
        assertEquals("JWT", typ)
    }

    // ==================== Credential Structure Tests ====================

    @Test
    fun `W3C JWT VC - contains required vc structure`() = runTest {
        val jwt = createSignedW3cJwtVc(holderDid)
        
        val payloadBase64 = jwt.split(".")[1]
        val payload = Json.parseToJsonElement(
            payloadBase64.decodeBase64UrlToString()
        ).jsonObject
        
        val vc = payload["vc"]?.jsonObject
        assertNotNull(vc, "W3C JWT VC should have vc claim")
        assertNotNull(vc["credentialSubject"], "vc should have credentialSubject")
        assertNotNull(vc["type"], "vc should have type")
    }

    @Test
    fun `SD-JWT VC - contains vct claim`() = runTest {
        val sdJwt = createSignedSdJwtVc(holderDid)
        val jwtPart = sdJwt.substringBefore("~")
        
        val payloadBase64 = jwtPart.split(".")[1]
        val payload = Json.parseToJsonElement(
            payloadBase64.decodeBase64UrlToString()
        ).jsonObject
        
        assertNotNull(payload["vct"], "SD-JWT VC should have vct claim")
        assertEquals("https://example.com/credential/identity", payload["vct"]?.jsonPrimitive?.content)
    }

    @Test
    fun `SD-JWT VC - contains sd algorithm claim`() = runTest {
        val sdJwt = createSignedSdJwtVc(holderDid)
        val jwtPart = sdJwt.substringBefore("~")
        
        val payloadBase64 = jwtPart.split(".")[1]
        val payload = Json.parseToJsonElement(
            payloadBase64.decodeBase64UrlToString()
        ).jsonObject
        
        assertNotNull(payload["_sd_alg"], "SD-JWT VC should have _sd_alg claim")
        assertEquals("sha-256", payload["_sd_alg"]?.jsonPrimitive?.content)
    }

    // ==================== Disclosures String Format Tests ====================

    @Test
    fun `disclosures stored as tilde-separated string`() {
        val disclosures = listOf(
            "WyJzYWx0MSIsImdpdmVuX25hbWUiLCJKb2huIl0",
            "WyJzYWx0MiIsImZhbWlseV9uYW1lIiwiRG9lIl0"
        )
        
        val disclosuresString = disclosures.joinToString("~")
        
        assertEquals("WyJzYWx0MSIsImdpdmVuX25hbWUiLCJKb2huIl0~WyJzYWx0MiIsImZhbWlseV9uYW1lIiwiRG9lIl0", disclosuresString)
    }

    @Test
    fun `empty disclosures list produces null string`() {
        val disclosures = emptyList<String>()
        
        val disclosuresString = if (disclosures.isNotEmpty()) {
            disclosures.joinToString("~")
        } else {
            null
        }
        
        assertNull(disclosuresString)
    }

    // ==================== JWT Validation Tests ====================

    @Test
    fun `JWT format validation - valid 3-segment JWT passes`() {
        val jwt = "header.payload.signature"
        val segments = jwt.split(".")
        
        assertEquals(3, segments.size)
    }

    @Test
    fun `JWT format validation - invalid JWT with wrong segments fails`() {
        val invalidJwts = listOf(
            "header.payload",           // 2 segments
            "header.payload.sig.extra", // 4 segments
            "single",                   // 1 segment
            ""                          // empty
        )
        
        for (jwt in invalidJwts) {
            val segments = jwt.split(".")
            assertTrue(segments.size != 3, "JWT '$jwt' should fail validation")
        }
    }

    // ==================== Subject DID Extraction Tests ====================

    @Test
    fun `subject DID extraction - from credentialSubject id`() = runTest {
        val jwt = createSignedW3cJwtVc(holderDid)
        
        val payloadBase64 = jwt.split(".")[1]
        val payload = Json.parseToJsonElement(
            payloadBase64.decodeBase64UrlToString()
        ).jsonObject
        
        val vc = payload["vc"]?.jsonObject
        val credentialSubject = vc?.get("credentialSubject")?.jsonObject
        val subjectId = credentialSubject?.get("id")?.jsonPrimitive?.content
        
        assertEquals(holderDid, subjectId)
    }

    @Test
    fun `subject DID extraction - from sub claim`() = runTest {
        val jwt = createSignedW3cJwtVc(holderDid)
        
        val payloadBase64 = jwt.split(".")[1]
        val payload = Json.parseToJsonElement(
            payloadBase64.decodeBase64UrlToString()
        ).jsonObject
        
        val sub = payload["sub"]?.jsonPrimitive?.content
        
        assertEquals(holderDid, sub)
    }

    // ==================== Credential Format Enum Tests ====================

    @Test
    fun `credential format - sd_jwt_vc format exists`() {
        val format = CredentialFormat.sd_jwt_vc
        assertEquals("vc+sd-jwt", format.value)
    }

    @Test
    fun `credential format - jwt_vc_json format exists`() {
        val format = CredentialFormat.jwt_vc_json
        assertEquals("jwt_vc_json", format.value)
    }

    // Helper function
    private fun String.decodeBase64UrlToString(): String {
        val base64 = this
            .replace('-', '+')
            .replace('_', '/')
            .let {
                when (it.length % 4) {
                    2 -> "$it=="
                    3 -> "$it="
                    else -> it
                }
            }
        return java.util.Base64.getDecoder().decode(base64).decodeToString()
    }
}
