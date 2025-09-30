import id.walt.commons.testing.E2ETest
import id.walt.issuer.issuance.IssuanceRequest
import id.walt.oid4vc.OpenID4VCI.getCIProviderMetadataUrl
import id.walt.oid4vc.data.CredentialFormat
import id.walt.oid4vc.data.CredentialOffer
import id.walt.oid4vc.data.GrantType
import id.walt.oid4vc.data.OpenIDProviderMetadata
import id.walt.oid4vc.providers.CredentialWalletConfig
import id.walt.oid4vc.providers.OpenIDClientConfig
import id.walt.oid4vc.requests.CredentialOfferRequest
import id.walt.webwallet.service.oidc4vc.TestCredentialWallet
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*


class TestOpenIdCredentialWallet(
    private val e2e: E2ETest,
    private val client: HttpClient
) {

    fun testCredentialWallet(issuanceReq: IssuanceRequest, did: String) = runBlocking {
        val credentialWallet = TestCredentialWallet(
            config = CredentialWalletConfig(
                redirectUri = "http://blank",
            ),
            did = did
        )

        lateinit var offerUrl: String

        val issuerApi = IssuerApi(e2e, client)

        issuerApi.jwt(issuanceReq) {
            offerUrl = it
        }

        val offerUrlParams = Url(offerUrl).parameters.toMap()
        val offerObj = CredentialOfferRequest.fromHttpParameters(offerUrlParams)
        assertTrue(offerObj.credentialOfferUri!!.contains("draft13"))
        assertFalse(offerObj.credentialOfferUri!!.contains("draft11"))

        val credentialOffer = client.get(offerObj.credentialOfferUri!!).body<CredentialOffer.Draft13>()

        assertNotNull(credentialOffer.credentialIssuer)
        assertNotNull(credentialOffer.credentialConfigurationIds)
        assertNotNull(credentialOffer.grants)


        val issuerMetadataUrl = getCIProviderMetadataUrl(credentialOffer.credentialIssuer)
        val rawJsonMetadata = client.get(issuerMetadataUrl).bodyAsText()

        val issuerMetadata = OpenIDProviderMetadata.fromJSONString(rawJsonMetadata) as OpenIDProviderMetadata.Draft13

        assertContains(issuerMetadata.grantTypesSupported, GrantType.authorization_code)
        assertContains(issuerMetadata.grantTypesSupported, GrantType.pre_authorized_code)
        assertContains(issuerMetadata.authorizationServers!!, credentialOffer.credentialIssuer)

        val client = OpenIDClientConfig(
            clientID = did,
            clientSecret = null,
            redirectUri = credentialWallet.config.redirectUri,
            useCodeChallenge = false
        )

        val credentialResp = credentialWallet.executePreAuthorizedCodeFlow(
            credentialOffer = credentialOffer,
            holderDid = did,
            client = client,
            userPIN = null
        )

        assertTrue(credentialResp.isNotEmpty())
        assertEquals(1, credentialResp.size)
        assertEquals(CredentialFormat.jwt_vc_json, credentialResp.first().format)
    }
}
