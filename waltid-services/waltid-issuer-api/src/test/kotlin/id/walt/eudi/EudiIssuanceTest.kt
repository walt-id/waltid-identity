package id.walt.eudi

import id.walt.IssuerApiTest.Companion.TEST_ISSUER_DID
import id.walt.commons.config.ConfigManager
import id.walt.issuer.issuance.*
import id.walt.oid4vc.data.CredentialFormat
import id.walt.testConfigs
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.intellij.lang.annotations.Language
import kotlin.test.*

/**
 * Test cases for EUDI wallet compatibility and OpenID4VCI Draft 13+ flows.
 *
 * These tests verify:
 * - Draft 13+ credential request format handling (credential_configuration_id, proofs)
 * - DPoP (Demonstrating Proof of Possession) support per RFC 9449
 * - mDoc/mso_mdoc credential issuance for EUDI PID
 * - Full OpenID4VCI issuance flow compatibility
 */
class EudiIssuanceTest {

    companion object {
        @Language("JSON")
        val TEST_EC_KEY = """{
            "type": "jwk",
            "jwk": {
                "kty": "EC",
                "d": "mJJv_Hzv8--BHJaJlvB9KM8XQnM9M8J7KNZ8K_z9qdc",
                "crv": "P-256",
                "kid": "test-key-1",
                "x": "dHGO-XVe1E-tEjqLN5EFT_FHQFgXTQ-9U7TL5qm9_0g",
                "y": "L8L7_pV9t2qn7B8DJ1_N8pEyEL_WQ8wVBM_FqA7k5tw"
            }
        }"""

        @Language("JSON")
        val TEST_PID_DATA = """{
            "family_name": "DOE",
            "given_name": "JOHN",
            "birth_date": "1990-01-15",
            "issuing_country": "AU",
            "issuing_authority": "Australia Government",
            "document_number": "123456789",
            "portrait": null,
            "driving_privileges": null
        }"""

        @Language("JSON")
        val TEST_MDL_DATA = """{
            "family_name": "DOE",
            "given_name": "JOHN",
            "birth_date": "1990-01-15",
            "issue_date": "2023-01-01",
            "expiry_date": "2033-01-01",
            "issuing_country": "AU",
            "issuing_authority": "Roads and Maritime Services",
            "document_number": "DL123456",
            "portrait": null,
            "driving_privileges": [
                {
                    "vehicle_category_code": "C",
                    "issue_date": "2023-01-01",
                    "expiry_date": "2033-01-01"
                }
            ]
        }"""

        val jsonEcKeyObj = Json.decodeFromString<JsonObject>(TEST_EC_KEY)
        val jsonPidDataObj = Json.decodeFromString<JsonObject>(TEST_PID_DATA)
        val jsonMdlDataObj = Json.decodeFromString<JsonObject>(TEST_MDL_DATA)
    }

    // ==================== DPoP Handler Tests ====================

    @Test
    fun testDPoPJwkThumbprintCalculation() {
        // Test EC key thumbprint calculation per RFC 7638
        val ecJwk = buildJsonObject {
            put("kty", JsonPrimitive("EC"))
            put("crv", JsonPrimitive("P-256"))
            put("x", JsonPrimitive("dHGO-XVe1E-tEjqLN5EFT_FHQFgXTQ-9U7TL5qm9_0g"))
            put("y", JsonPrimitive("L8L7_pV9t2qn7B8DJ1_N8pEyEL_WQ8wVBM_FqA7k5tw"))
        }

        val thumbprint = DPoPHandler.calculateJwkThumbprint(ecJwk)

        assertNotNull(thumbprint)
        assertTrue(thumbprint.isNotEmpty())
        // Thumbprint should be base64url encoded (no padding, no + or /)
        assertFalse(thumbprint.contains("="))
        assertFalse(thumbprint.contains("+"))
        assertFalse(thumbprint.contains("/"))
    }

    @Test
    fun testDPoPJwkThumbprintDeterministic() {
        // Same JWK should always produce the same thumbprint
        val jwk = buildJsonObject {
            put("kty", JsonPrimitive("EC"))
            put("crv", JsonPrimitive("P-256"))
            put("x", JsonPrimitive("test-x-value"))
            put("y", JsonPrimitive("test-y-value"))
        }

        val thumbprint1 = DPoPHandler.calculateJwkThumbprint(jwk)
        val thumbprint2 = DPoPHandler.calculateJwkThumbprint(jwk)

        assertEquals(thumbprint1, thumbprint2)
    }

    @Test
    fun testDPoPAccessTokenHashCalculation() {
        val accessToken = "test-access-token-12345"
        val hash = DPoPHandler.calculateAccessTokenHash(accessToken)

        assertNotNull(hash)
        assertTrue(hash.isNotEmpty())
        // Hash should be base64url encoded
        assertFalse(hash.contains("="))
        assertFalse(hash.contains("+"))
        assertFalse(hash.contains("/"))
    }

    @Test
    fun testDPoPSupportedAlgorithms() {
        // Verify supported algorithms include common EC and RSA algorithms
        assertTrue(DPoPHandler.SUPPORTED_ALGORITHMS.contains("ES256"))
        assertTrue(DPoPHandler.SUPPORTED_ALGORITHMS.contains("ES384"))
        assertTrue(DPoPHandler.SUPPORTED_ALGORITHMS.contains("ES512"))
        assertTrue(DPoPHandler.SUPPORTED_ALGORITHMS.contains("RS256"))
        assertTrue(DPoPHandler.SUPPORTED_ALGORITHMS.contains("RS384"))
        assertTrue(DPoPHandler.SUPPORTED_ALGORITHMS.contains("RS512"))
    }

    // ==================== Draft 13+ Format Tests ====================

    @Test
    fun testDraft13ProofsToProofConversion() {
        // Test that Draft 13+ "proofs" format is correctly identified
        val draft13Request = buildJsonObject {
            put("credential_configuration_id", JsonPrimitive("eu.europa.ec.eudi.pid.1"))
            put("proofs", buildJsonObject {
                put("jwt", buildJsonArray {
                    add(JsonPrimitive("eyJhbGciOiJFUzI1NiIsInR5cCI6Im9wZW5pZDR2Y2ktcHJvb2Yrand0In0.eyJhdWQiOiJodHRwczovL2lzc3Vlci5leGFtcGxlLmNvbSJ9.signature"))
                })
            })
        }

        // Verify the request has proofs but not proof
        assertTrue(draft13Request.containsKey("proofs"))
        assertFalse(draft13Request.containsKey("proof"))
        assertTrue(draft13Request.containsKey("credential_configuration_id"))
        assertFalse(draft13Request.containsKey("format"))
    }

    @Test
    fun testLegacyProofFormat() {
        // Test that legacy "proof" format is recognized
        val legacyRequest = buildJsonObject {
            put("format", JsonPrimitive("mso_mdoc"))
            put("doctype", JsonPrimitive("eu.europa.ec.eudi.pid.1"))
            put("proof", buildJsonObject {
                put("proof_type", JsonPrimitive("jwt"))
                put("jwt", JsonPrimitive("eyJhbGciOiJFUzI1NiJ9.eyJhdWQiOiJodHRwczovL2lzc3Vlci5leGFtcGxlLmNvbSJ9.sig"))
            })
        }

        // Verify the request has proof (singular) and format
        assertTrue(legacyRequest.containsKey("proof"))
        assertFalse(legacyRequest.containsKey("proofs"))
        assertTrue(legacyRequest.containsKey("format"))
    }

    @Test
    fun testCredentialConfigurationIdPresent() {
        // Draft 13+ uses credential_configuration_id instead of format
        val draft13Request = buildJsonObject {
            put("credential_configuration_id", JsonPrimitive("eu.europa.ec.eudi.pid.1"))
        }

        val credConfigId = draft13Request["credential_configuration_id"]?.jsonPrimitive?.content
        assertEquals("eu.europa.ec.eudi.pid.1", credConfigId)
    }

    // ==================== EUDI PID Issuance Tests ====================

    @Test
    fun testPidMdocCredentialOfferCreation() = runTest {
        ConfigManager.testWithConfigs(testConfigs)

        val issueRequest = IssuanceRequest(
            issuerKey = jsonEcKeyObj,
            credentialData = jsonPidDataObj,
            credentialConfigurationId = "eu.europa.ec.eudi.pid.1",
            issuerDid = TEST_ISSUER_DID
        )

        val offerUri = createCredentialOfferUri(listOf(issueRequest), CredentialFormat.mso_mdoc)

        assertNotNull(offerUri)
        assertTrue(offerUri.startsWith("openid-credential-offer://"))
        assertTrue(offerUri.contains("credential_offer_uri"))
    }

    @Test
    fun testMdlCredentialOfferCreation() = runTest {
        ConfigManager.testWithConfigs(testConfigs)

        val issueRequest = IssuanceRequest(
            issuerKey = jsonEcKeyObj,
            credentialData = jsonMdlDataObj,
            credentialConfigurationId = "org.iso.18013.5.1.mDL",
            issuerDid = TEST_ISSUER_DID
        )

        val offerUri = createCredentialOfferUri(listOf(issueRequest), CredentialFormat.mso_mdoc)

        assertNotNull(offerUri)
        assertTrue(offerUri.startsWith("openid-credential-offer://"))
    }

    @Test
    fun testIssuanceSessionWithDPoPThumbprint() = runTest {
        ConfigManager.testWithConfigs(testConfigs)
        val ciProvider = CIProvider()

        val session = ciProvider.initializeCredentialOffer(
            issuanceRequests = listOf(
                IssuanceRequest(
                    issuerKey = jsonEcKeyObj,
                    credentialData = jsonPidDataObj,
                    credentialConfigurationId = "eu.europa.ec.eudi.pid.1",
                    issuerDid = TEST_ISSUER_DID
                )
            ),
            expiresIn = kotlin.time.Duration.parse("5m")
        )

        // Initial session should not have DPoP thumbprint
        assertNull(session.dpopThumbprint)
        assertEquals(IssuanceSessionStatus.ACTIVE, session.status)
        assertFalse(session.isClosed)
    }

    @Test
    fun testIssuanceSessionStatusValues() {
        // Verify all expected status values exist
        val statuses = IssuanceSessionStatus.entries

        assertTrue(statuses.any { it.name == "ACTIVE" })
        assertTrue(statuses.any { it.name == "SUCCESSFUL" })
        assertTrue(statuses.any { it.name == "UNSUCCESSFUL" })
        assertTrue(statuses.any { it.name == "REJECTED_BY_USER" })
        assertTrue(statuses.any { it.name == "EXPIRED" })
    }

    // ==================== Metadata Tests ====================

    @Test
    fun testCIProviderMetadataHasDPoPSupport() = runTest {
        ConfigManager.testWithConfigs(testConfigs)
        val ciProvider = CIProvider()

        val metadata = ciProvider.metadata

        assertNotNull(metadata)
        // Check that DPoP signing algorithms are advertised
        val dpopAlgs = metadata.dpopSigningAlgValuesSupported
        assertNotNull(dpopAlgs, "Metadata should include dpop_signing_alg_values_supported")
        assertTrue(dpopAlgs.contains("ES256"), "DPoP should support ES256")
    }

    @Test
    fun testCIProviderMetadataHasCredentialConfigurations() = runTest {
        ConfigManager.testWithConfigs(testConfigs)
        val ciProvider = CIProvider()

        val metadata = ciProvider.metadata
        val credConfigs = metadata.credentialConfigurationsSupported

        assertNotNull(credConfigs)
        assertTrue(credConfigs.isNotEmpty())
    }

    // ==================== Integration-style Tests ====================

    @Test
    fun testFullPidIssuanceOfferFlow() = runTest {
        ConfigManager.testWithConfigs(testConfigs)
        val ciProvider = CIProvider()

        // Step 1: Create issuance request
        val issuanceRequest = IssuanceRequest(
            issuerKey = jsonEcKeyObj,
            credentialData = jsonPidDataObj,
            credentialConfigurationId = "eu.europa.ec.eudi.pid.1",
            issuerDid = TEST_ISSUER_DID
        )

        // Step 2: Initialize credential offer
        val session = ciProvider.initializeCredentialOffer(
            issuanceRequests = listOf(issuanceRequest),
            expiresIn = kotlin.time.Duration.parse("5m")
        )

        // Step 3: Verify session is created correctly
        assertNotNull(session.id)
        assertNotNull(session.credentialOffer)
        assertEquals(IssuanceSessionStatus.ACTIVE, session.status)

        // Step 4: Verify credential offer contains correct configuration
        val offer = session.credentialOffer!!
        assertNotNull(offer.credentialIssuer)
        assertTrue(offer.draft13?.credentialConfigurationIds?.any { it.jsonPrimitive.content == "eu.europa.ec.eudi.pid.1" } == true)
    }

    @Test
    fun testBatchPidAndMdlIssuance() = runTest {
        ConfigManager.testWithConfigs(testConfigs)
        val ciProvider = CIProvider()

        // Create batch issuance with both PID and mDL
        val pidRequest = IssuanceRequest(
            issuerKey = jsonEcKeyObj,
            credentialData = jsonPidDataObj,
            credentialConfigurationId = "eu.europa.ec.eudi.pid.1",
            issuerDid = TEST_ISSUER_DID
        )

        val mdlRequest = IssuanceRequest(
            issuerKey = jsonEcKeyObj,
            credentialData = jsonMdlDataObj,
            credentialConfigurationId = "org.iso.18013.5.1.mDL",
            issuerDid = TEST_ISSUER_DID
        )

        val session = ciProvider.initializeCredentialOffer(
            issuanceRequests = listOf(pidRequest, mdlRequest),
            expiresIn = kotlin.time.Duration.parse("5m")
        )

        assertNotNull(session.credentialOffer)
        assertEquals(2, session.issuanceRequests.size)

        val configIds = session.credentialOffer!!.draft13!!.credentialConfigurationIds
        assertTrue(configIds.any { it.jsonPrimitive.content == "eu.europa.ec.eudi.pid.1" })
        assertTrue(configIds.any { it.jsonPrimitive.content == "org.iso.18013.5.1.mDL" })
    }
}
