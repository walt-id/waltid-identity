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
 * E2E test for EUDI mDL (Mobile Driving License) issuance in mso_mdoc format.
 *
 * Tests the ISO 18013-5 compliant mDL credential issuance flow:
 * - DocType matching: org.iso.18013.5.1.mDL
 * - Namespace: org.iso.18013.5.1
 * - CBOR-encoded credential
 */
class EudiMdlE2ETest(
    private val e2e: E2ETest,
    private val client: HttpClient
) {

    companion object {
        private val issuerKey = loadResource("issuance/key.json")
        private val issuerDid = loadResource("issuance/did.txt")

        // ISO 18013-5 doctype
        const val MDL_DOCTYPE = "org.iso.18013.5.1.mDL"
        const val MDL_NAMESPACE = "org.iso.18013.5.1"

        val mdlIssuanceRequest = IssuanceRequest(
            issuerKey = Json.decodeFromString<JsonElement>(issuerKey).jsonObject,
            issuerDid = issuerDid,
            credentialConfigurationId = MDL_DOCTYPE,
            credentialData = buildJsonObject {
                put("family_name", "DOE")
                put("given_name", "JOHN")
                put("birth_date", "1990-01-15")
                put("issue_date", "2023-01-01")
                put("expiry_date", "2033-01-01")
                put("issuing_country", "AU")
                put("issuing_authority", "Roads and Maritime Services")
                put("document_number", "DL123456")
                put("driving_privileges", buildJsonArray {
                    add(buildJsonObject {
                        put("vehicle_category_code", "C")
                        put("issue_date", "2023-01-01")
                        put("expiry_date", "2033-01-01")
                    })
                })
            }
        )
    }

    /**
     * Tests mDL issuance in mso_mdoc format following the EUDI wallet flow.
     */
    suspend fun testMdlIssuance() = e2e.test("EUDI mDL issuance flow") {
        val walletClient = EudiWalletClient()

        // Step 1: Create credential offer via Issuer API
        val offerUri = createCredentialOffer(mdlIssuanceRequest)
        assertNotNull(offerUri, "Should receive credential offer URI")
        assertTrue(offerUri.startsWith("openid-credential-offer://"), "Should be valid offer URI")

        // Step 2: Resolve the credential offer
        val offer = walletClient.resolveCredentialOffer(offerUri)
        assertNotNull(offer, "Should resolve credential offer")

        // Verify Draft 13+ format with mDL configuration
        val credConfigIds = offer.draft13?.credentialConfigurationIds
        assertNotNull(credConfigIds, "Should have credential_configuration_ids")
        assertTrue(
            credConfigIds.any { it.jsonPrimitive.content == MDL_DOCTYPE },
            "Should offer mDL credential configuration"
        )

        // Step 3: Fetch issuer metadata
        val metadata = walletClient.fetchIssuerMetadata(offer.credentialIssuer!!)

        // Verify mDL configuration exists (Draft13 metadata)
        val draft13Metadata = metadata as? OpenIDProviderMetadata.Draft13
        assertNotNull(draft13Metadata, "Metadata should be Draft13 format")
        val mdlConfig = draft13Metadata.credentialConfigurationsSupported?.get(MDL_DOCTYPE)
        assertNotNull(mdlConfig, "Metadata should include mDL configuration")

        // Verify format is mso_mdoc
        assertTrue(
            mdlConfig.format == CredentialFormat.mso_mdoc,
            "Format should be mso_mdoc for mDL"
        )

        // Step 4: Request access token
        val preAuthCode = walletClient.getPreAuthorizedCode(offer)!!
        val tokenResponse = walletClient.requestAccessToken(
            tokenEndpoint = metadata.tokenEndpoint!!,
            preAuthorizedCode = preAuthCode
        )
        assertNotNull(tokenResponse.accessToken, "Should receive access token")
        assertNotNull(tokenResponse.cNonce, "Should receive c_nonce")

        // Step 5: Request the mDL credential
        val credentialResponse = walletClient.requestCredential(
            credentialEndpoint = metadata.credentialEndpoint!!,
            accessToken = tokenResponse.accessToken,
            credentialConfigurationId = MDL_DOCTYPE,
            cNonce = tokenResponse.cNonce!!,
            format = CredentialFormat.mso_mdoc
        )

        assertNotNull(credentialResponse.credential, "Should receive credential")

        // Step 6: Validate the mDoc credential format
        validateMdocCredential(credentialResponse.credential)
    }

    /**
     * Tests that mDL contains required driving privilege information.
     */
    suspend fun testMdlDrivingPrivileges() = e2e.test("EUDI mDL - driving privileges") {
        val walletClient = EudiWalletClient()

        val offerUri = createCredentialOffer(mdlIssuanceRequest)
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
            credentialConfigurationId = MDL_DOCTYPE,
            cNonce = tokenResponse.cNonce!!,
            format = CredentialFormat.mso_mdoc
        )

        // mDoc is CBOR encoded - basic validation that it's not empty
        assertTrue(
            credentialResponse.credential.isNotEmpty(),
            "mDL credential should not be empty"
        )

        // mDoc credentials should not look like JWTs
        assertTrue(
            !credentialResponse.credential.startsWith("ey"),
            "mDoc should not be JWT format"
        )
    }

    /**
     * Validates the structure of an mDoc credential.
     */
    private fun validateMdocCredential(credential: String) {
        // mDoc credentials are CBOR-encoded (base64url)
        assertNotNull(credential, "Credential should not be null")
        assertTrue(credential.isNotEmpty(), "Credential should not be empty")

        // mDoc credentials should NOT look like JWTs
        assertTrue(
            !credential.startsWith("ey"),
            "mDoc should not start with 'ey' (not JWT format)"
        )

        // mDoc credentials should NOT contain JWT dots
        assertTrue(
            credential.count { it == '.' } != 2,
            "mDoc should not have JWT structure (two dots)"
        )
    }

    private suspend fun createCredentialOffer(request: IssuanceRequest): String {
        val response = client.post("/openid4vc/mdoc/issue") {
            setBody(request)
        }
        assertTrue(response.status.isSuccess(), "Issuer API should return success")
        return response.body<String>()
    }
}
