package id.walt.policies2.vc

import id.walt.policies2.vc.policies.ETSITrustListPolicy
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Unit tests for ETSITrustListPolicy.
 * 
 * Note: These tests validate the policy configuration and error handling.
 * Full integration tests with real credentials and a running trust-registry
 * service should be added separately.
 */
class ETSITrustListPolicyTest {

    @Test
    fun `test policy requires trustRegistryUrl`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            ETSITrustListPolicy(
                trustRegistryUrl = ""
            )
        }
        assertTrue(exception.message!!.contains("trustRegistryUrl"))
    }

    @Test
    fun `test policy requires http or https url`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            ETSITrustListPolicy(
                trustRegistryUrl = "ftp://example.com"
            )
        }
        assertTrue(exception.message!!.contains("http"))
    }

    @Test
    fun `test policy accepts http url`() {
        val policy = ETSITrustListPolicy(
            trustRegistryUrl = "http://localhost:7000"
        )
        assertEquals("etsi-trust-list", policy.id)
        assertEquals("http://localhost:7000", policy.trustRegistryUrl)
    }

    @Test
    fun `test policy accepts https url`() {
        val policy = ETSITrustListPolicy(
            trustRegistryUrl = "https://trust.example.com"
        )
        assertEquals("etsi-trust-list", policy.id)
        assertEquals("https://trust.example.com", policy.trustRegistryUrl)
    }

    @Test
    fun `test policy with all options`() {
        val policy = ETSITrustListPolicy(
            trustRegistryUrl = "https://trust.example.com",
            expectedEntityType = "PID_PROVIDER",
            expectedServiceType = "QCert",
            allowStaleSource = true,
            requireAuthenticated = true
        )
        
        assertEquals("etsi-trust-list", policy.id)
        assertEquals("https://trust.example.com", policy.trustRegistryUrl)
        assertEquals("PID_PROVIDER", policy.expectedEntityType)
        assertEquals("QCert", policy.expectedServiceType)
        assertTrue(policy.allowStaleSource)
        assertTrue(policy.requireAuthenticated)
    }

    @Test
    fun `test policy serialization round-trip`() {
        val originalPolicy = ETSITrustListPolicy(
            trustRegistryUrl = "https://trust.example.com",
            expectedEntityType = "PID_PROVIDER",
            allowStaleSource = true
        )
        
        val json = Json.encodeToString(ETSITrustListPolicy.serializer(), originalPolicy)
        // Note: The "policy" discriminator only appears in polymorphic serialization 
        // (when serializing as CredentialVerificationPolicy2), not when serializing directly
        assertTrue(json.contains("\"trustRegistryUrl\":\"https://trust.example.com\""))
        assertTrue(json.contains("\"expectedEntityType\":\"PID_PROVIDER\""))
        assertTrue(json.contains("\"allowStaleSource\":true"))
        
        val deserializedPolicy = Json.decodeFromString(ETSITrustListPolicy.serializer(), json)
        assertEquals(originalPolicy, deserializedPolicy)
    }

    // Note: The following tests would require either:
    // 1. A running trust-registry-service instance
    // 2. Mocking the HTTP client (which requires restructuring the policy)
    // 
    // For now, they are commented out and should be added as integration tests.
    
    // @Test
    // fun `test verify with trusted certificate`() = runTest {
    //     // Requires running trust-registry and valid mDoc credential
    // }
    
    // @Test
    // fun `test verify with untrusted certificate`() = runTest {
    //     // Requires running trust-registry and mDoc credential with untrusted issuer
    // }
}
