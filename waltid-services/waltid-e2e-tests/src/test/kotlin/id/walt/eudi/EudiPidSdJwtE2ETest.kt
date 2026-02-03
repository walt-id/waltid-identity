package id.walt.eudi

import id.walt.commons.testing.E2ETest
import id.walt.commons.testing.utils.ServiceTestUtils.loadResource
import id.walt.issuer.issuance.IssuanceRequest
import id.walt.oid4vc.data.CredentialFormat
import id.walt.oid4vc.data.OpenIDProviderMetadata
import id.walt.sdjwt.SDJwtVC
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * E2E test for EUDI PID issuance in dc+sd-jwt format (SD-JWT-VC).
 *
 * Important: EUDI uses dc+sd-jwt (Digital Credentials SD-JWT), not vc+sd-jwt.
 * This test validates:
 * - Correct format string handling
 * - VCT (Verifiable Credential Type) matching
 * - SD-JWT structure and selective disclosure
 */
class EudiPidSdJwtE2ETest(
    private val e2e: E2ETest,
    private val client: HttpClient
) {

    companion object {
        private val issuerKey = loadResource("issuance/key.json")
        private val issuerDid = loadResource("issuance/did.txt")

        // EUDI PID VCT - this must match what the issuer expects
        const val EUDI_PID_VCT = "urn:eudi:pid:1"

        val pidSdJwtIssuanceRequest = IssuanceRequest(
            issuerKey = Json.decodeFromString<JsonElement>(issuerKey).jsonObject,
            issuerDid = issuerDid,
            credentialConfigurationId = "eu.europa.ec.eudi.pid_vc_sd_jwt",
            credentialData = buildJsonObject {
                put("family_name", "DOE")
                put("given_name", "JOHN")
                put("birth_date", "1990-01-15")
                put("issuing_country", "AU")
                put("issuing_authority", "Australia Government")
            }
            // Note: selectiveDisclosure should be configured in issuer metadata
        )
    }

    /**
     * Tests PID issuance in dc+sd-jwt format following the EUDI wallet flow.
     */
    suspend fun testPidSdJwtIssuance() = e2e.test("EUDI PID SD-JWT issuance flow") {
        val walletClient = EudiWalletClient()

        // Step 1: Create credential offer via Issuer API
        val offerUri = createCredentialOffer(pidSdJwtIssuanceRequest)
        assertNotNull(offerUri, "Should receive credential offer URI")
        assertTrue(offerUri.startsWith("openid-credential-offer://"), "Should be valid offer URI")

        // Step 2: Resolve the credential offer
        val offer = walletClient.resolveCredentialOffer(offerUri)
        assertNotNull(offer, "Should resolve credential offer")

        // Verify Draft 13+ format with SD-JWT configuration
        val credConfigIds = offer.draft13?.credentialConfigurationIds
        assertNotNull(credConfigIds, "Should have credential_configuration_ids")
        assertTrue(
            credConfigIds.any { it.jsonPrimitive.content == "eu.europa.ec.eudi.pid_vc_sd_jwt" },
            "Should offer PID SD-JWT credential configuration"
        )

        // Step 3: Fetch issuer metadata
        val metadata = walletClient.fetchIssuerMetadata(offer.credentialIssuer!!)

        // Verify SD-JWT PID configuration exists (Draft13 metadata)
        val draft13Metadata = metadata as? OpenIDProviderMetadata.Draft13
        assertNotNull(draft13Metadata, "Metadata should be Draft13 format")
        val pidConfig = draft13Metadata.credentialConfigurationsSupported?.get("eu.europa.ec.eudi.pid_vc_sd_jwt")
        assertNotNull(pidConfig, "Metadata should include PID SD-JWT configuration")

        // Verify format is dc+sd-jwt (not vc+sd-jwt)
        assertEquals(CredentialFormat.sd_jwt_dc, pidConfig.format, "Format should be dc+sd-jwt for EUDI")

        // Step 4: Request access token
        val preAuthCode = walletClient.getPreAuthorizedCode(offer)!!
        val tokenResponse = walletClient.requestAccessToken(
            tokenEndpoint = metadata.tokenEndpoint!!,
            preAuthorizedCode = preAuthCode
        )
        assertNotNull(tokenResponse.accessToken, "Should receive access token")
        assertNotNull(tokenResponse.cNonce, "Should receive c_nonce")

        // Step 5: Request the PID credential
        val credentialResponse = walletClient.requestCredential(
            credentialEndpoint = metadata.credentialEndpoint!!,
            accessToken = tokenResponse.accessToken,
            credentialConfigurationId = "eu.europa.ec.eudi.pid_vc_sd_jwt",
            cNonce = tokenResponse.cNonce!!,
            format = CredentialFormat.sd_jwt_dc
        )

        assertNotNull(credentialResponse.credential, "Should receive credential")

        // Step 6: Validate the SD-JWT structure
        validateSdJwtCredential(credentialResponse.credential)
    }

    /**
     * Tests that VCT (Verifiable Credential Type) is correctly set in the SD-JWT.
     */
    suspend fun testVctMatching() = e2e.test("EUDI PID SD-JWT - VCT matching") {
        val walletClient = EudiWalletClient()

        val offerUri = createCredentialOffer(pidSdJwtIssuanceRequest)
        val offer = walletClient.resolveCredentialOffer(offerUri)
        val metadata = walletClient.fetchIssuerMetadata(offer.credentialIssuer!!)

        val preAuthCode = walletClient.getPreAuthorizedCode(offer)!!
        val tokenResponse = walletClient.requestAccessToken(
            tokenEndpoint = metadata.tokenEndpoint!!,
            preAuthorizedCode = preAuthCode
        )

        val credentialResponse = walletClient.requestCredential(
            credentialEndpoint = metadata.credentialEndpoint!!,
            accessToken = tokenResponse.accessToken,
            credentialConfigurationId = "eu.europa.ec.eudi.pid_vc_sd_jwt",
            cNonce = tokenResponse.cNonce!!,
            format = CredentialFormat.sd_jwt_dc
        )

        // Parse the SD-JWT and verify VCT
        val sdJwtVc = SDJwtVC.parse(credentialResponse.credential)
        assertNotNull(sdJwtVc.vct, "SD-JWT should have VCT claim")
        assertEquals(EUDI_PID_VCT, sdJwtVc.vct, "VCT should match EUDI PID type")
    }

    /**
     * Validates the structure of an SD-JWT credential.
     */
    private fun validateSdJwtCredential(credential: String) {
        // SD-JWT format: <jwt>~<disclosure1>~<disclosure2>~...
        assertTrue(credential.contains("~"), "SD-JWT should contain disclosure separator")

        val parts = credential.split("~")
        assertTrue(parts.isNotEmpty(), "SD-JWT should have at least JWT part")

        // First part should be the JWT
        val jwt = parts[0]
        assertTrue(jwt.startsWith("ey"), "First part should be JWT (base64url header)")
        assertEquals(2, jwt.count { it == '.' }, "JWT should have exactly 2 dots")

        // Parse as SD-JWT-VC
        val sdJwtVc = SDJwtVC.parse(credential)
        assertNotNull(sdJwtVc, "Should parse as valid SD-JWT-VC")

        // Verify SD algorithm is set
        assertNotNull(sdJwtVc.sdAlg, "SD-JWT should have _sd_alg claim")
        assertEquals("sha-256", sdJwtVc.sdAlg, "SD algorithm should be sha-256")
    }

    private suspend fun createCredentialOffer(request: IssuanceRequest): String {
        val response = client.post("/openid4vc/sdjwt/issue") {
            setBody(request)
        }
        assertTrue(response.status.isSuccess(), "Issuer API should return success")
        return response.body<String>()
    }
}
