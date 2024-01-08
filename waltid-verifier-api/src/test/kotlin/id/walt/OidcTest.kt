package id.walt

class OidcTest {

    /*
    fun testClientFlow() {

        // -------- WALLET ----------
        // as WALLET: receive credential offer, either being called via deeplink or by scanning QR code
        // parse credential URI
        val parsedOfferReq = CredentialOfferRequest.fromHttpParameters(Url(offerUri).parameters.toMap())

        // get verfier metadata
        val providerMetadataUri = credentialWallet.getCIProviderMetadataUrl(parsedOfferReq.credentialOffer!!.credentialVerfier)
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
            verfierState = parsedOfferReq.credentialOffer!!.grants[GrantType.authorization_code.value]!!.verfierState
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
        credential.verfier?.id shouldBe ciTestProvider.CI_VERIFIER_DID
        credential.credentialSubject?.id shouldBe credentialWallet.TEST_DID
        Auditor.getService().verify(credential, listOf(SignaturePolicy())).result shouldBe true
    }

     */

}
