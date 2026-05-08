package id.walt.test.integration.tests

import id.walt.crypto.keys.KeyType
import id.walt.test.integration.expectSuccess
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for issuer onboarding functionality.
 * Tests the /onboard/issuer endpoint for creating issuer keypairs and DIDs.
 */
class IssuerOnboardingIntegrationTest : AbstractIntegrationTest() {

    @Test
    fun shouldOnboardIssuerWithEd25519Key() = runTest {
        val client = environment.testHttpClient()
        
        val onboardingRequest = buildJsonObject {
            put("key", buildJsonObject {
                put("backend", "jwk")
                put("keyType", KeyType.Ed25519.name)
            })
            put("did", buildJsonObject {
                put("method", "jwk")
            })
        }
        
        val response = client.post("/onboard/issuer") {
            setBody(onboardingRequest)
        }
        response.expectSuccess()
        
        val result = response.body<JsonObject>()
        assertNotNull(result["issuerKey"]?.jsonObject, "Response should contain issuerKey")
        assertNotNull(result["issuerDid"]?.jsonPrimitive?.content, "Response should contain issuerDid")
        
        val issuerDid = result["issuerDid"]!!.jsonPrimitive.content
        assertTrue(issuerDid.startsWith("did:jwk:"), "Issuer DID should be a did:jwk")
    }

    @Test
    fun shouldOnboardIssuerWithSecp256r1Key() = runTest {
        val client = environment.testHttpClient()
        
        val onboardingRequest = buildJsonObject {
            put("key", buildJsonObject {
                put("backend", "jwk")
                put("keyType", KeyType.secp256r1.name)
            })
            put("did", buildJsonObject {
                put("method", "jwk")
            })
        }
        
        val response = client.post("/onboard/issuer") {
            setBody(onboardingRequest)
        }
        response.expectSuccess()
        
        val result = response.body<JsonObject>()
        assertNotNull(result["issuerKey"]?.jsonObject, "Response should contain issuerKey")
        assertNotNull(result["issuerDid"]?.jsonPrimitive?.content, "Response should contain issuerDid")
    }

    @Test
    fun shouldOnboardIssuerWithSecp256k1Key() = runTest {
        val client = environment.testHttpClient()
        
        val onboardingRequest = buildJsonObject {
            put("key", buildJsonObject {
                put("backend", "jwk")
                put("keyType", KeyType.secp256k1.name)
            })
            put("did", buildJsonObject {
                put("method", "jwk")
            })
        }
        
        val response = client.post("/onboard/issuer") {
            setBody(onboardingRequest)
        }
        response.expectSuccess()
        
        val result = response.body<JsonObject>()
        assertNotNull(result["issuerKey"]?.jsonObject, "Response should contain issuerKey")
        assertNotNull(result["issuerDid"]?.jsonPrimitive?.content, "Response should contain issuerDid")
    }

    @Test
    fun shouldOnboardIssuerWithRsaKey() = runTest {
        val client = environment.testHttpClient()
        
        val onboardingRequest = buildJsonObject {
            put("key", buildJsonObject {
                put("backend", "jwk")
                put("keyType", KeyType.RSA.name)
            })
            put("did", buildJsonObject {
                put("method", "jwk")
            })
        }
        
        val response = client.post("/onboard/issuer") {
            setBody(onboardingRequest)
        }
        response.expectSuccess()
        
        val result = response.body<JsonObject>()
        assertNotNull(result["issuerKey"]?.jsonObject, "Response should contain issuerKey")
        assertNotNull(result["issuerDid"]?.jsonPrimitive?.content, "Response should contain issuerDid")
    }

    @Test
    fun shouldOnboardIssuerWithDidKey() = runTest {
        val client = environment.testHttpClient()
        
        val onboardingRequest = buildJsonObject {
            put("key", buildJsonObject {
                put("backend", "jwk")
                put("keyType", KeyType.Ed25519.name)
            })
            put("did", buildJsonObject {
                put("method", "key")
            })
        }
        
        val response = client.post("/onboard/issuer") {
            setBody(onboardingRequest)
        }
        response.expectSuccess()
        
        val result = response.body<JsonObject>()
        assertNotNull(result["issuerKey"]?.jsonObject, "Response should contain issuerKey")
        assertNotNull(result["issuerDid"]?.jsonPrimitive?.content, "Response should contain issuerDid")
        
        val issuerDid = result["issuerDid"]!!.jsonPrimitive.content
        assertTrue(issuerDid.startsWith("did:key:"), "Issuer DID should be a did:key")
    }

    @Test
    fun shouldReturnValidIssuerKeyStructure() = runTest {
        val client = environment.testHttpClient()
        
        val onboardingRequest = buildJsonObject {
            put("key", buildJsonObject {
                put("backend", "jwk")
                put("keyType", KeyType.Ed25519.name)
            })
            put("did", buildJsonObject {
                put("method", "jwk")
            })
        }
        
        val response = client.post("/onboard/issuer") {
            setBody(onboardingRequest)
        }
        response.expectSuccess()
        
        val result = response.body<JsonObject>()
        val issuerKey = result["issuerKey"]!!.jsonObject
        
        assertNotNull(issuerKey["type"]?.jsonPrimitive?.content, "Issuer key should have type")
        assertNotNull(issuerKey["jwk"]?.jsonObject, "Issuer key should have jwk")
        
        val jwk = issuerKey["jwk"]!!.jsonObject
        assertNotNull(jwk["kty"]?.jsonPrimitive?.content, "JWK should have kty")
        assertNotNull(jwk["kid"]?.jsonPrimitive?.content, "JWK should have kid")
    }
}
