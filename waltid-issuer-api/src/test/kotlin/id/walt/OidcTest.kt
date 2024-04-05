package id.walt

import id.walt.issuer.CIProvider
import id.walt.issuer.base.config.ConfigManager
import id.walt.oid4vc.data.CredentialOffer
import id.walt.oid4vc.requests.CredentialOfferRequest
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes

class OidcTest {

    @Test
    fun testInitCredentialOffer() {
        ConfigManager.loadConfigs(emptyArray())
        val ciTestProvider = CIProvider()

        // -------- CREDENTIAL ISSUER ----------
        // as CI provider, initialize credential offer for user
        val issuanceSession = ciTestProvider.initializeCredentialOffer(
            CredentialOffer.Builder(ciTestProvider.baseUrl).addOfferedCredential("VerifiableId"),
            5.minutes, allowPreAuthorized = false
        )

        val offerRequest = CredentialOfferRequest(issuanceSession.credentialOffer!!)
        val offerUri = ciTestProvider.getCredentialOfferRequestUrl(offerRequest)
        println("Offer URI: $offerUri")
    }

    /*
    fun testClientFlow() {

        // -------- WALLET ----------
        // as WALLET: receive credential offer, either being called via deeplink or by scanning QR code
        // parse credential URI
        val parsedOfferReq = CredentialOfferRequest.fromHttpParameters(Url(offerUri).parameters.toMap())

        // get issuer metadata
        val providerMetadataUri = credentialWallet.getCIProviderMetadataUrl(parsedOfferReq.credentialOffer!!.credentialIssuer)
        val providerMetadata = ktorClient.get(providerMetadataUri).call.body<OpenIDProviderMetadata>()
        providerMetadata.credentialsSupported shouldNotBe null

        // resolve offered credentials
        val offeredCredentials = parsedOfferReq.credentialOffer!!.resolveOfferedCredentials(providerMetadata)
        val offeredCredential = offeredCredentials.first()

        // go through full authorization code flow to receive offered credential
        // auth request (short-cut, without pushed authorization request)
        val authReq = AuthorizationRequest(
            ResponseType.code.name, testCIClientConfig.clientID,
            redirectUri = credentialWallet.config.redirectUri,
            issuerState = parsedOfferReq.credentialOffer!!.grants[GrantType.authorization_code.value]!!.issuerState
        )
        val authResp = ktorClient.get(providerMetadata.authorizationEndpoint!!) {
            url {
                parameters.appendAll(parametersOf(authReq.toHttpParameters()))
            }
        }
        authResp.status shouldBe HttpStatusCode.Found
        val location = Url(authResp.headers[HttpHeaders.Location]!!)
        location.parameters.names() shouldContain ResponseType.code.name

        // token req
        val tokenReq =
            TokenRequest(GrantType.authorization_code, testCIClientConfig.clientID, code = location.parameters[ResponseType.code.name]!!)
        val tokenResp = ktorClient.submitForm(
            providerMetadata.tokenEndpoint!!,
            formParameters = parametersOf(tokenReq.toHttpParameters())
        ).body<JsonObject>().let { TokenResponse.fromJSON(it) }
        tokenResp.isSuccess shouldBe true
        tokenResp.accessToken shouldNotBe null
        tokenResp.cNonce shouldNotBe null

        // receive credential
        ciTestProvider.deferIssuance = false
        var nonce = tokenResp.cNonce!!

        val credReq = CredentialRequest.forOfferedCredential(
            offeredCredential,
            credentialWallet.generateDidProof(credentialWallet.TEST_DID, ciTestProvider.baseUrl, nonce)
        )

        val credentialResp = ktorClient.post(providerMetadata.credentialEndpoint!!) {
            contentType(ContentType.Application.Json)
            bearerAuth(tokenResp.accessToken!!)
            setBody(credReq.toJSON())
        }.body<JsonObject>().let { CredentialResponse.fromJSON(it) }

        credentialResp.isSuccess shouldBe true
        credentialResp.isDeferred shouldBe false
        credentialResp.format!! shouldBe CredentialFormat.jwt_vc_json
        credentialResp.credential.shouldBeInstanceOf<JsonPrimitive>()

        // parse and verify credential
        val credential = VerifiableCredential.fromString(credentialResp.credential!!.jsonPrimitive.content)
        println("Issued credential: $credential")
        credential.issuer?.id shouldBe ciTestProvider.CI_ISSUER_DID
        credential.credentialSubject?.id shouldBe credentialWallet.TEST_DID
        Auditor.getService().verify(credential, listOf(SignaturePolicy())).result shouldBe true
    }

     */

}
