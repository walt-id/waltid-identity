package id.walt.eudi

import id.walt.commons.testing.E2ETest
import id.walt.commons.testing.utils.ServiceTestUtils.loadResource
import id.walt.issuer.issuance.IssuanceRequest
import id.walt.oid4vc.data.CredentialFormat
import id.walt.oid4vc.data.OpenIDProviderMetadata
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * E2E test for EUDI PID issuance in mso_mdoc format.
 *
 * Tests the full OpenID4VCI flow:
 * 1. Create credential offer via Issuer API
 * 2. Resolve the offer like EUDI wallet would
 * 3. Fetch issuer metadata
 * 4. Request access token with pre-authorized code
 * 5. Request credential with Draft 13+ format
 * 6. Validate the returned mDoc credential
 */
class EudiPidMdocE2ETest(
    private val e2e: E2ETest,
    private val client: HttpClient
) {

    companion object {
        private val issuerKey = loadResource("issuance/key.json")
        private val issuerDid = loadResource("issuance/did.txt")

        val pidMdocIssuanceRequest = IssuanceRequest(
            issuerKey = Json.decodeFromString<JsonElement>(issuerKey).jsonObject,
            issuerDid = issuerDid,
            credentialConfigurationId = "eu.europa.ec.eudi.pid.1",
            credentialData = buildJsonObject {
                put("family_name", "DOE")
                put("given_name", "JOHN")
                put("birth_date", "1990-01-15")
                put("issuing_country", "AU")
                put("issuing_authority", "Australia Government")
                put("document_number", "123456789")
            }
        )
    }

    /**
     * Tests PID issuance in mso_mdoc format following the EUDI wallet flow.
     */
    suspend fun testPidMdocIssuance() = e2e.test("EUDI PID mDoc issuance flow") {
        val walletClient = EudiWalletClient()

        // Step 1: Create credential offer via Issuer API
        val offerUri = createCredentialOffer(pidMdocIssuanceRequest)
        assertNotNull(offerUri, "Should receive credential offer URI")
        assertTrue(offerUri.startsWith("openid-credential-offer://"), "Should be valid offer URI")

        // Step 2: Resolve the credential offer
        val offer = walletClient.resolveCredentialOffer(offerUri)
        assertNotNull(offer, "Should resolve credential offer")
        assertNotNull(offer.credentialIssuer, "Offer should have credential issuer")

        // Verify Draft 13+ format is used
        val credConfigIds = offer.draft13?.credentialConfigurationIds
        assertNotNull(credConfigIds, "Should have credential_configuration_ids (Draft 13+)")
        assertTrue(
            credConfigIds.any { it.jsonPrimitive.content == "eu.europa.ec.eudi.pid.1" },
            "Should offer PID credential configuration"
        )

        // Step 3: Fetch issuer metadata
        val metadata = walletClient.fetchIssuerMetadata(offer.credentialIssuer!!)
        assertNotNull(metadata.credentialEndpoint, "Metadata should have credential endpoint")
        assertNotNull(metadata.tokenEndpoint, "Metadata should have token endpoint")

        // Verify PID configuration exists in metadata (Draft13 metadata)
        val draft13Metadata = metadata as? OpenIDProviderMetadata.Draft13
        assertNotNull(draft13Metadata, "Metadata should be Draft13 format")
        val pidConfig = draft13Metadata.credentialConfigurationsSupported?.get("eu.europa.ec.eudi.pid.1")
        assertNotNull(pidConfig, "Metadata should include PID configuration")

        // Step 4: Request access token
        val preAuthCode = walletClient.getPreAuthorizedCode(offer)
        assertNotNull(preAuthCode, "Should have pre-authorized code")

        val tokenResponse = walletClient.requestAccessToken(
            tokenEndpoint = metadata.tokenEndpoint!!,
            preAuthorizedCode = preAuthCode
        )
        assertNotNull(tokenResponse.accessToken, "Should receive access token")
        assertNotNull(tokenResponse.cNonce, "Should receive c_nonce for proof generation")

        // Step 5: Request the PID credential
        val credentialResponse = walletClient.requestCredential(
            credentialEndpoint = metadata.credentialEndpoint!!,
            accessToken = tokenResponse.accessToken,
            credentialConfigurationId = "eu.europa.ec.eudi.pid.1",
            cNonce = tokenResponse.cNonce!!,
            format = CredentialFormat.mso_mdoc
        )

        assertNotNull(credentialResponse.credential, "Should receive credential")

        // Step 6: Validate the mDoc credential format
        // mDoc credentials are CBOR-encoded, so we check it's not a JWT
        assertTrue(
            !credentialResponse.credential.startsWith("ey"),
            "mDoc should not be JWT format"
        )
    }

    /**
     * Tests that the issuer returns proper error for invalid credential configuration.
     */
    suspend fun testInvalidCredentialConfiguration() = e2e.test("EUDI PID mDoc - invalid config error") {
        val walletClient = EudiWalletClient()

        // Create a valid offer first
        val offerUri = createCredentialOffer(pidMdocIssuanceRequest)
        val offer = walletClient.resolveCredentialOffer(offerUri)
        val metadata = walletClient.fetchIssuerMetadata(offer.credentialIssuer!!)

        val preAuthCode = walletClient.getPreAuthorizedCode(offer)!!
        val tokenResponse = walletClient.requestAccessToken(
            tokenEndpoint = metadata.tokenEndpoint!!,
            preAuthorizedCode = preAuthCode
        )

        // Try to request a credential with invalid configuration ID
        try {
            walletClient.requestCredential(
                credentialEndpoint = metadata.credentialEndpoint!!,
                accessToken = tokenResponse.accessToken,
                credentialConfigurationId = "invalid.credential.config",
                cNonce = tokenResponse.cNonce!!,
                format = CredentialFormat.mso_mdoc
            )
            throw AssertionError("Should have thrown exception for invalid config")
        } catch (e: EudiWalletClient.CredentialRequestException) {
            assertTrue(
                e.responseBody.contains("error") || e.message?.contains("failed") == true,
                "Should return error for invalid credential configuration"
            )
        }
    }

    private suspend fun createCredentialOffer(request: IssuanceRequest): String {
        val response = client.post("/openid4vc/mdoc/issue") {
            setBody(request)
        }
        assertTrue(response.status.isSuccess(), "Issuer API should return success")
        return response.body<String>()
    }
}
