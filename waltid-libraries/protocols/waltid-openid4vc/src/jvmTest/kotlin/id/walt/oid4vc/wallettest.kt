package id.walt.oid4vc

import id.walt.oid4vc.data.*
import id.walt.oid4vc.providers.CredentialWalletConfig
import id.walt.oid4vc.providers.OpenIDClientConfig
import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.oid4vc.requests.CredentialOfferRequest
import id.walt.oid4vc.requests.CredentialRequest
import id.walt.oid4vc.requests.TokenRequest
import id.walt.oid4vc.responses.CredentialResponse
import id.walt.oid4vc.responses.TokenResponse
import id.walt.policies.policies.JwtSignaturePolicy
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.util.*
import io.ktor.util.reflect.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.*

class wallettest {

    /*
     * Instructions to use:
     * 1. Uncomment the @BeforeAll (at `fun init`) and @Test at `fun testPreauth`
     * 2. Update `val offerUri = "openid-credential-offer://..."`
     * 3. Run test "wallettest" (this file)
     */

    private val ktorClient = HttpClient {
        install(ContentNegotiation) {
            json()
        }
        followRedirects = false
    }

    private lateinit var credentialWallet: TestCredentialWallet
    private val testCIClientConfig = OpenIDClientConfig("test-client", null, redirectUri = "http://blank")

    //@BeforeAll   /* Uncomment me */
    fun init() {
        credentialWallet = TestCredentialWallet(CredentialWalletConfig("http://blank"))
        //ciTestProvider.start()
    }

    //@Test   /* Uncomment me */
    suspend fun testPreauth() {
        // vvv UPDATE BELOW URL WITH OFFER_URI vvv
        val offerUri =
            "openid-credential-offer://issuer.portal.walt.id/?credential_offer=%7B%22credential_issuer%22%3A%22https%3A%2F%2Fissuer.portal.walt.id%22%2C%22credentials%22%3A%5B%7B%22format%22%3A%22jwt_vc_json%22%2C%22types%22%3A%5B%22VerifiableCredential%22%2C%22OpenBadgeCredential%22%5D%2C%22credential_definition%22%3A%7B%22%40context%22%3A%5B%22https%3A%2F%2Fwww.w3.org%2F2018%2Fcredentials%2Fv1%22%2C%22https%3A%2F%2Fpurl.imsglobal.org%2Fspec%2Fob%2Fv3p0%2Fcontext.json%22%5D%2C%22types%22%3A%5B%22VerifiableCredential%22%2C%22OpenBadgeCredential%22%5D%7D%7D%5D%2C%22grants%22%3A%7B%22authorization_code%22%3A%7B%22issuer_state%22%3A%22a9dec312-be7f-40c2-bee9-4e5275d49827%22%7D%2C%22urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Apre-authorized_code%22%3A%7B%22pre-authorized_code%22%3A%22eyJhbGciOiJFZERTQSJ9.eyJzdWIiOiJhOWRlYzMxMi1iZTdmLTQwYzItYmVlOS00ZTUyNzVkNDk4MjciLCJpc3MiOiJodHRwczovL2lzc3Vlci5wb3J0YWwud2FsdC5pZCIsImF1ZCI6IlRPS0VOIn0.LqgJurAZs862qNBqNGHSzPianqhlUpsayVXWmd0E6tQhrh_cgY5oqiUy1_hMO09aj37OmPRsQ4DFq7ufNo1kAA%22%2C%22user_pin_required%22%3Afalse%7D%7D%7D"
        // ^^^ UPDATE ABOVE URL WITH OFFER_URI ^^^

        AuthorizationRequest(
            responseType = setOf(),
            clientId = "",
            responseMode = null,
            redirectUri = null,
            scope = setOf(),
            state = null,
            authorizationDetails = listOf(),
            walletIssuer = null,
            userHint = null,
            issuerState = null,
            requestUri = null,
            presentationDefinition = null,
            presentationDefinitionUri = null,
            clientIdScheme = null,
            clientMetadata = null,
            clientMetadataUri = null,
            nonce = null,
            responseUri = null,
            customParameters = mapOf()

        )

        AuthorizationDetails(
            type = "minim",
            format = null,
            credentialDefinition = CredentialDefinition(type = listOf()),
            credentialSubject = mapOf(),
            docType = null,
            claims = mapOf(),
            customParameters = mapOf()
        )

        println("// -------- WALLET ----------")
        println("// as WALLET: receive credential offer, either being called via deeplink or by scanning QR code")
        println("// parse credential URI")
        val parsedOfferReq = CredentialOfferRequest.fromHttpParameters(Url(offerUri).parameters.toMap())
        println("parsedOfferReq: $parsedOfferReq")

        assertNotNull(actual = parsedOfferReq.credentialOffer)
        assertNotNull(actual = parsedOfferReq.credentialOffer.credentialIssuer)
        assertContains(
            iterable = parsedOfferReq.credentialOffer.grants.keys,
            element = GrantType.pre_authorized_code.value
        )
        assertNotNull(actual = parsedOfferReq.credentialOffer.grants[GrantType.pre_authorized_code.value]?.preAuthorizedCode)

        println("// get issuer metadata")
        val providerMetadataUri =
            credentialWallet.getCIProviderMetadataUrl(parsedOfferReq.credentialOffer.credentialIssuer)
        val providerMetadata = ktorClient.get(providerMetadataUri).call.body<OpenIDProviderMetadata>() as OpenIDProviderMetadata.Draft13
        println("providerMetadata: $providerMetadata")

        assertNotNull(actual = providerMetadata.credentialConfigurationsSupported)

        println("// resolve offered credentials")
        val offeredCredentials = OpenID4VCI.resolveOfferedCredentials(parsedOfferReq.credentialOffer, providerMetadata)
        println("offeredCredentials: $offeredCredentials")
        assertEquals(expected = 1, actual = offeredCredentials.size)
        assertEquals(expected = CredentialFormat.jwt_vc_json, actual = offeredCredentials.first().format)
        val offeredCredential = offeredCredentials.first()
        println("offeredCredentials[0]: $offeredCredential")

        println("// fetch access token using pre-authorized code (skipping authorization step)")
        val tokenReq = TokenRequest.PreAuthorizedCode(
            clientId = testCIClientConfig.clientID,
            preAuthorizedCode = parsedOfferReq.credentialOffer.grants[GrantType.pre_authorized_code.value]!!.preAuthorizedCode!!,
        )

        println("tokenReq: $tokenReq")

        val tokenResp = ktorClient.submitForm(
            providerMetadata.tokenEndpoint!!, formParameters = parametersOf(tokenReq.toHttpParameters())
        ).body<JsonObject>().let { TokenResponse.fromJSON(it) }
        println("tokenResp: $tokenResp")

        println(">>> Token response = success: ${tokenResp.isSuccess}")
        assertTrue(actual = tokenResp.isSuccess)
        assertNotNull(actual = tokenResp.accessToken)
        assertNotNull(actual = tokenResp.cNonce)

        println("// receive credential")
        val nonce = tokenResp.cNonce

        val credReq = CredentialRequest.forOfferedCredential(
            offeredCredential = offeredCredential,
            proof = credentialWallet.generateDidProof(credentialWallet.TEST_DID, providerMetadata.issuer!!, nonce)
        )
        println("credReq: $credReq")

        val credentialResp = ktorClient.post(providerMetadata.credentialEndpoint!!) {
            contentType(ContentType.Application.Json)
            bearerAuth(tokenResp.accessToken)
            setBody(credReq.toJSON())
        }.body<JsonObject>().let { CredentialResponse.fromJSON(it) }
        println("credentialResp: $credentialResp")

        assertTrue(actual = credentialResp.isSuccess)
        assertFalse(actual = credentialResp.isDeferred)
        assertEquals(expected = CredentialFormat.jwt_vc_json, actual = credentialResp.format!!)
        assertTrue(actual = credentialResp.credential!!.instanceOf(JsonPrimitive::class))

        println("// parse and verify credential")
        val credential = credentialResp.credential.jsonPrimitive.content
        println(">>> Issued credential: $credential")
        assertTrue(actual = JwtSignaturePolicy().verify(credential, null, mapOf()).isSuccess)
    }

}
