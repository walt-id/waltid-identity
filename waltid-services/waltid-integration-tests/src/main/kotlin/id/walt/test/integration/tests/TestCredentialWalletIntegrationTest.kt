@file:OptIn(ExperimentalUuidApi::class)

package id.walt.test.integration.tests

import id.walt.commons.testing.utils.ServiceTestUtils.loadResource
import id.walt.issuer.issuance.IssuanceRequest
import id.walt.oid4vc.OpenID4VCI.getCIProviderMetadataUrl
import id.walt.oid4vc.OpenID4VCIVersion
import id.walt.oid4vc.data.CredentialFormat
import id.walt.oid4vc.data.CredentialOffer
import id.walt.oid4vc.data.GrantType
import id.walt.oid4vc.data.OpenIDProviderMetadata
import id.walt.oid4vc.requests.CredentialOfferRequest
import id.walt.oid4vc.util.http
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.*
import kotlin.uuid.ExperimentalUuidApi


class TestOpenIdCredentialWalletIntegrationTest : AbstractIntegrationTest() {

    @Test
    fun testCredentialWallet() = runTest {

        val did = defaultWalletApi.listDids().first()

        val preAuthFlowIssuanceReqDraft13 =
            Json.decodeFromString<IssuanceRequest>(loadResource("issuance/openbadgecredential-issuance-request.json"))
                .copy(
                    standardVersion = OpenID4VCIVersion.DRAFT13,
                )

        var offerUrl = issuerApi.issueJwtCredential(preAuthFlowIssuanceReqDraft13)
        val offerUrlParams = Url(offerUrl).parameters.toMap()
        val offerObj = CredentialOfferRequest.fromHttpParameters(offerUrlParams)
        assertTrue(offerObj.credentialOfferUri!!.contains("draft13"))
        assertFalse(offerObj.credentialOfferUri!!.contains("draft11"))
        val credentialOffer = http.get(offerObj.credentialOfferUri!!).body<CredentialOffer.Draft13>()
        assertNotNull(credentialOffer.credentialIssuer)
        assertNotNull(credentialOffer.credentialConfigurationIds)
        assertNotNull(credentialOffer.grants)
        val issuerMetadataUrl = getCIProviderMetadataUrl(credentialOffer.credentialIssuer)
        val rawJsonMetadata = http.get(issuerMetadataUrl).bodyAsText()

        val issuerMetadata = OpenIDProviderMetadata.fromJSONString(rawJsonMetadata) as OpenIDProviderMetadata.Draft13

        assertContains(issuerMetadata.grantTypesSupported, GrantType.authorization_code)
        assertContains(issuerMetadata.grantTypesSupported, GrantType.pre_authorized_code)
        assertContains(issuerMetadata.authorizationServers!!, credentialOffer.credentialIssuer)

        defaultWalletApi.claimCredential(offerUrl, did.did).also { credentialResp ->
            assertTrue(credentialResp.isNotEmpty())
            assertEquals(1, credentialResp.size)
            assertEquals(CredentialFormat.jwt_vc_json, credentialResp.first().format)
        }
    }
}
