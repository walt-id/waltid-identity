package id.walt

import id.walt.IssuerApiTest.Companion.TEST_ISSUER_DID
import id.walt.commons.config.ConfigManager
import id.walt.issuer.issuance.CIProvider
import id.walt.issuer.issuance.IssuanceRequest
import id.walt.oid4vc.OpenID4VCI
import id.walt.oid4vc.data.AuthenticationMethod
import id.walt.oid4vc.data.GrantType
import id.walt.oid4vc.data.TxCode
import id.walt.oid4vc.errors.TokenError
import id.walt.oid4vc.requests.CredentialOfferRequest
import id.walt.oid4vc.requests.TokenRequest
import id.walt.oid4vc.responses.TokenErrorCode
import kotlin.test.Test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.minutes

class OidcIssuanceTest {

    @Test
    fun testInitCredentialOffer() {
        ConfigManager.testWithConfigs(testConfigs)
        val ciTestProvider = CIProvider()

        // -------- CREDENTIAL ISSUER ----------
        // as CI provider, initialize credential offer for user
        val issuanceSession = ciTestProvider.initializeCredentialOffer(
            issuanceRequests = listOf(
                IssuanceRequest(
                    issuerKey = IssuerApiTest.jsonKeyObj,
                    credentialData = IssuerApiTest.jsonVCObj,
                    credentialConfigurationId = "VerifiableId",
                    mapping = IssuerApiTest.jsonMappingObj,
                    issuerDid = TEST_ISSUER_DID,
                    authenticationMethod = AuthenticationMethod.NONE,
                )
            ),
            expiresIn = 5.minutes
        )

        // Verify initial session status
        assertEquals(id.walt.issuer.issuance.IssuanceSessionStatus.ACTIVE, issuanceSession.status)
        assertEquals(false, issuanceSession.isClosed)

        val offerRequest = CredentialOfferRequest(issuanceSession.credentialOffer!!)
        val offerUri = OpenID4VCI.getCredentialOfferRequestUrl(offerRequest)
        println("Offer URI: $offerUri")
    }

    @Test
    fun testPreAuthorizedCodeIsSingleUse() {
        ConfigManager.testWithConfigs(testConfigs)
        val ciTestProvider = CIProvider()

        val issuanceSession = ciTestProvider.initializeCredentialOffer(
            issuanceRequests = listOf(
                IssuanceRequest(
                    issuerKey = IssuerApiTest.jsonKeyObj,
                    credentialData = IssuerApiTest.jsonVCObj,
                    credentialConfigurationId = "VerifiableId",
                    mapping = IssuerApiTest.jsonMappingObj,
                    issuerDid = TEST_ISSUER_DID,
                    authenticationMethod = AuthenticationMethod.PRE_AUTHORIZED,
                )
            ),
            expiresIn = 5.minutes
        )

        val preAuthorizedCode = issuanceSession
            .credentialOffer!!
            .grants[GrantType.pre_authorized_code.value]!!
            .preAuthorizedCode!!

        val tokenRequest = TokenRequest.PreAuthorizedCode(
            preAuthorizedCode = preAuthorizedCode
        )

        // First exchange must succeed
        val firstResponse = ciTestProvider.processTokenRequest(tokenRequest)

        assertNotNull(firstResponse.accessToken)

        // The same pre-authorized code must not be accepted twice
        val error = assertFailsWith<TokenError> {
            ciTestProvider.processTokenRequest(tokenRequest)
        }

        assertEquals(
            TokenErrorCode.invalid_grant,
            error.errorCode
        )
    }


    @Test
    fun testConcurrentPreAuthorizedCodeRedemptionAllowsOnlyOneSuccess() {
        ConfigManager.testWithConfigs(testConfigs)
        val ciTestProvider = CIProvider()

        val issuanceSession = ciTestProvider.initializeCredentialOffer(
            issuanceRequests = listOf(
                IssuanceRequest(
                    issuerKey = IssuerApiTest.jsonKeyObj,
                    credentialData = IssuerApiTest.jsonVCObj,
                    credentialConfigurationId = "VerifiableId",
                    mapping = IssuerApiTest.jsonMappingObj,
                    issuerDid = TEST_ISSUER_DID,
                    authenticationMethod = AuthenticationMethod.PRE_AUTHORIZED,
                )
            ),
            expiresIn = 5.minutes
        )

        val preAuthorizedCode = issuanceSession
            .credentialOffer!!
            .grants[GrantType.pre_authorized_code.value]!!
            .preAuthorizedCode!!

        val tokenRequest = TokenRequest.PreAuthorizedCode(
            preAuthorizedCode = preAuthorizedCode
        )

        val results = runBlocking {
            val start = CompletableDeferred<Unit>()
            val ready = List(2) { CompletableDeferred<Unit>() }

            val attempts = List(2) { index ->
                async(Dispatchers.Default) {
                    ready[index].complete(Unit)
                    start.await()

                    runCatching {
                        ciTestProvider.processTokenRequest(tokenRequest)
                    }
                }
            }

            // Wait until both attempts are ready, then release them together.
            ready.forEach { it.await() }
            start.complete(Unit)

            attempts.awaitAll()
        }

        assertEquals(
            1,
            results.count { it.isSuccess },
            "Exactly one concurrent redemption must succeed"
        )

        val failures = results.mapNotNull { it.exceptionOrNull() }

        assertEquals(
            1,
            failures.size,
            "Exactly one concurrent redemption must fail"
        )

        val error = assertIs<TokenError>(failures.single())

        assertEquals(
            TokenErrorCode.invalid_grant,
            error.errorCode
        )
    }


    @Test
    fun testInvalidTxCodeDoesNotConsumePreAuthorizedCode() {
        ConfigManager.testWithConfigs(testConfigs)
        val ciTestProvider = CIProvider()

        val validTxCode = "123456"

        val issuanceSession = ciTestProvider.initializeCredentialOffer(
            issuanceRequests = listOf(
                IssuanceRequest(
                    issuerKey = IssuerApiTest.jsonKeyObj,
                    credentialData = IssuerApiTest.jsonVCObj,
                    credentialConfigurationId = "VerifiableId",
                    mapping = IssuerApiTest.jsonMappingObj,
                    issuerDid = TEST_ISSUER_DID,
                    authenticationMethod = AuthenticationMethod.PRE_AUTHORIZED,
                )
            ),
            expiresIn = 5.minutes,
            txCode = TxCode.makeFor(
                pin = validTxCode,
                description = "Enter the transaction code"
            ),
            txCodeValue = validTxCode
        )

        val preAuthorizedCode = issuanceSession
            .credentialOffer!!
            .grants[GrantType.pre_authorized_code.value]!!
            .preAuthorizedCode!!

        // Wrong txCode must fail
        val wrongTxCodeError = assertFailsWith<TokenError> {
            ciTestProvider.processTokenRequest(
                TokenRequest.PreAuthorizedCode(
                    preAuthorizedCode = preAuthorizedCode,
                    txCode = "000000"
                )
            )
        }

        assertEquals(
            TokenErrorCode.invalid_grant,
            wrongTxCodeError.errorCode
        )

        // A failed txCode validation must NOT consume the pre-authorized code
        val validResponse = ciTestProvider.processTokenRequest(
            TokenRequest.PreAuthorizedCode(
                preAuthorizedCode = preAuthorizedCode,
                txCode = validTxCode
            )
        )

        assertNotNull(validResponse.accessToken)

        // After the successful exchange, the code is consumed
        val reusedCodeError = assertFailsWith<TokenError> {
            ciTestProvider.processTokenRequest(
                TokenRequest.PreAuthorizedCode(
                    preAuthorizedCode = preAuthorizedCode,
                    txCode = validTxCode
                )
            )
        }

        assertEquals(
            TokenErrorCode.invalid_grant,
            reusedCodeError.errorCode
        )
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
