package id.walt.oid4vc

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.crypto.bc.BouncyCastleProviderSingleton
import com.nimbusds.jose.jwk.ECKey
import id.walt.credentials.verification.policies.JwtSignaturePolicy
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.dids.DidService
import id.walt.did.dids.registrar.dids.DidIonCreateOptions
import id.walt.did.dids.registrar.dids.DidJwkCreateOptions
import id.walt.did.dids.registrar.dids.DidKeyCreateOptions
import id.walt.did.dids.registrar.dids.DidWebCreateOptions
import id.walt.oid4vc.data.*
import id.walt.oid4vc.definitions.JWTClaims
import id.walt.oid4vc.definitions.OPENID_CREDENTIAL_AUTHORIZATION_TYPE
import id.walt.oid4vc.providers.CredentialWalletConfig
import id.walt.oid4vc.providers.OpenIDClientConfig
import id.walt.oid4vc.providers.TokenTarget
import id.walt.oid4vc.requests.*
import id.walt.oid4vc.responses.*
import id.walt.oid4vc.util.JwtUtils
import id.walt.sdjwt.SDJwt
import id.walt.sdjwt.SDMap
import id.walt.sdjwt.SDPayload
import id.walt.sdjwt.SimpleJWTCryptoProvider
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.util.reflect.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.jupiter.api.BeforeAll
import java.io.File
import kotlin.test.*
import kotlin.time.Duration.Companion.minutes

class CI_JVM_Test {

    var testMetadata = OpenIDProviderMetadata(
        authorizationEndpoint = "https://localhost/oidc",
        credentialConfigurationsSupported = listOf(
            CredentialSupported(
                "UniversityDegreeCredential_jwt_vc_json",
                CredentialFormat.jwt_vc_json, setOf("did"), setOf("ES256K"),
                listOf(
                    DisplayProperties(
                        "University Credential",
                        "en-US",
                        LogoProperties(
                            "https://exampleuniversity.com/public/logo.png",
                            "a square logo of a university"
                        ),
                        backgroundColor = "#12107c", textColor = "#FFFFFF"
                    )
                ),
                types = listOf("VerifiableCredential", "UniversityDegreeCredential"),
                credentialSubject = mapOf(
                    "name" to ClaimDescriptor(
                        mandatory = false,
                        display = listOf(DisplayProperties("Full Name")),
                        customParameters = mapOf(
                            "firstName" to ClaimDescriptor(
                                valueType = "string",
                                display = listOf(DisplayProperties("First Name"))
                            ).toJSON(),
                            "lastName" to ClaimDescriptor(
                                valueType = "string",
                                display = listOf(DisplayProperties("Last Name"))
                            ).toJSON()
                        )
                    )
                )
            ),
            CredentialSupported(
                "VerifiableId_ldp_vc",
                CredentialFormat.ldp_vc, setOf("did"), setOf("ES256K"),
                listOf(DisplayProperties("Verifiable ID")),
                types = listOf("VerifiableCredential", "VerifiableId"),
                context = listOf(
                    JsonPrimitive("https://www.w3.org/2018/credentials/v1"),
                    JsonObject(mapOf("@version" to JsonPrimitive(1.1)))
                )
            )
        ).associateBy { it.id }
    )

    val ktorClient = HttpClient() {
        install(ContentNegotiation) {
            json()
        }
        install(Logging) {
            logger = Logger.SIMPLE
            level = LogLevel.ALL
        }
        followRedirects = false
    }

    private val testCIClientConfig = OpenIDClientConfig("test-client", null, redirectUri = "http://blank")

    companion object {
        private lateinit var ciTestProvider: CITestProvider
        private lateinit var credentialWallet: TestCredentialWallet

        @BeforeAll
        @JvmStatic
        fun init() = runTest {
            DidService.minimalInit()
            assertContains(DidService.registrarMethods.keys, "web")
            ciTestProvider = CITestProvider()
            credentialWallet = TestCredentialWallet(CredentialWalletConfig("http://blank"))
            ciTestProvider.start()
        }
    }

    @Test
    fun testCredentialSupportedSerialization() {
        val credentialSupportedJson = "{\n" +
                "    \"format\": \"jwt_vc_json\",\n" +
                "    \"id\": \"UniversityDegree_JWT\",\n" +
                "    \"types\": [\n" +
                "        \"VerifiableCredential\",\n" +
                "        \"UniversityDegreeCredential\"\n" +
                "    ],\n" +
                "    \"cryptographic_binding_methods_supported\": [\n" +
                "        \"did\"\n" +
                "    ],\n" +
                "    \"cryptographic_suites_supported\": [\n" +
                "        \"ES256K\"\n" +
                "    ],\n" +
                "    \"display\": [\n" +
                "        {\n" +
                "            \"name\": \"University Credential\",\n" +
                "            \"locale\": \"en-US\",\n" +
                "            \"logo\": {\n" +
                "                \"url\": \"https://exampleuniversity.com/public/logo.png\",\n" +
                "                \"alt_text\": \"a square logo of a university\"\n" +
                "            },\n" +
                "            \"background_color\": \"#12107c\",\n" +
                "            \"text_color\": \"#FFFFFF\"\n" +
                "        }\n" +
                "    ],\n" +
                "    \"credentialSubject\": {\n" +
                "        \"given_name\": {\n" +
                "            \"display\": [\n" +
                "                {\n" +
                "                    \"name\": \"Given Name\",\n" +
                "                    \"locale\": \"en-US\"\n" +
                "                }\n" +
                "            ]\n, \"nested\": {}" +
                "        },\n" +
                "        \"last_name\": {\n" +
                "            \"display\": [\n" +
                "                {\n" +
                "                    \"name\": \"Surname\",\n" +
                "                    \"locale\": \"en-US\"\n" +
                "                }\n" +
                "            ]\n" +
                "        },\n" +
                "        \"degree\": {},\n" +
                "        \"gpa\": {\n" +
                "            \"display\": [\n" +
                "                {\n" +
                "                    \"name\": \"GPA\"\n" +
                "                }\n" +
                "            ]\n" +
                "        }\n" +
                "    }\n" +
                "}"
        val credentialSupported = CredentialSupported.fromJSONString(credentialSupportedJson)
        assertEquals(expected = CredentialFormat.jwt_vc_json, actual = credentialSupported.format)
        assertEquals(
            expected = Json.parseToJsonElement(credentialSupportedJson).jsonObject,
            actual = Json.parseToJsonElement(credentialSupported.toJSONString()).jsonObject
        )
    }

    @Test
    fun testOIDProviderMetadata() {
        val metadataJson = testMetadata.toJSONString()
        println("metadataJson: $metadataJson")
        val metadataParsed = OpenIDProviderMetadata.fromJSONString(metadataJson)
        assertEquals(
            expected = Json.parseToJsonElement(metadataJson).jsonObject,
            actual = Json.parseToJsonElement(metadataParsed.toJSONString()).jsonObject
        )
        println("metadataParsed: $metadataParsed")
    }

    @Test
    fun testFetchAndParseMetadata() = runTest {
        val response = ktorClient.get("${CI_PROVIDER_BASE_URL}/.well-known/openid-configuration")
        println("response: $response")
        assertEquals(expected = HttpStatusCode.OK, actual = response.status)
        val respText = response.bodyAsText()
        val metadata: OpenIDProviderMetadata = OpenIDProviderMetadata.fromJSONString(respText)
        println("metadata: $metadata")
        assertEquals(
            expected = Json.parseToJsonElement(ciTestProvider.metadata.toJSONString()),
            actual = Json.parseToJsonElement(metadata.toJSONString())
        )
    }

    @Test
    fun testAuthorizationRequestSerialization() {
        val authorizationReq = "response_type=code" +
                "&client_id=s6BhdRkqt3" +
                "&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM" +
                "&code_challenge_method=S256" +
                "&authorization_details=%5B%7B%22type%22:%22openid_credential" +
                "%22,%22format%22:%22jwt_vc_json%22,%22types%22:%5B%22Verifia" +
                "bleCredential%22,%22UniversityDegreeCredential%22%5D%7D%5D" +
                "&redirect_uri=https%3A%2F%2Fclient.example.org%2Fcb"
        val parsedReq = AuthorizationRequest.fromHttpQueryString(authorizationReq)
        assertEquals(expected = "s6BhdRkqt3", actual = parsedReq.clientId)
        assertNotNull(actual = parsedReq.authorizationDetails)
        assertEquals(expected = "openid_credential", actual = parsedReq.authorizationDetails!!.first().type)

        val expectedReq = AuthorizationRequest(
            clientId = "s6BhdRkqt3", redirectUri = "https://client.example.org/cb",
            authorizationDetails = listOf(
                AuthorizationDetails(
                    type = OPENID_CREDENTIAL_AUTHORIZATION_TYPE,
                    format = CredentialFormat.jwt_vc_json,
                    types = listOf("VerifiableCredential", "UniversityDegreeCredential")
                )
            ),
            customParameters = mapOf(
                "code_challenge" to listOf("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"),
                "code_challenge_method" to listOf("S256")
            )
        )

        assertEquals(expected = expectedReq.toHttpQueryString(), actual = parsedReq.toHttpQueryString())
        assertEquals(
            expected = parseQueryString(authorizationReq),
            actual = parseQueryString(parsedReq.toHttpQueryString())
        )
    }

    @Test
    fun testInvalidAuthorizationRequest() = runTest {
        // 0. get issuer metadata
        val providerMetadata =
            ktorClient.get(ciTestProvider.getCIProviderMetadataUrl()).call.body<OpenIDProviderMetadata>()
        assertNotNull(actual = providerMetadata.pushedAuthorizationRequestEndpoint)

        // 1. send pushed authorization request with authorization details, containing info of credentials to be issued, receive session id
        val authReq = AuthorizationRequest(
            responseType = setOf(ResponseType.Code),
            clientId = testCIClientConfig.clientID,
            redirectUri = credentialWallet.config.redirectUri,
            authorizationDetails = listOf(
                AuthorizationDetails(
                    type = OPENID_CREDENTIAL_AUTHORIZATION_TYPE
                )
            )
        )
        val parResp = ktorClient.submitForm(
            providerMetadata.pushedAuthorizationRequestEndpoint!!,
            formParameters = parametersOf(authReq.toHttpParameters())
        ).body<JsonObject>().let { PushedAuthorizationResponse.fromJSON(it) }

        assertFalse(actual = parResp.isSuccess)
        assertEquals(expected = "invalid_request", actual = parResp.error)
    }

    private fun verifyIssuerAndSubjectId(credential: JsonObject, issuerId: String, subjectId: String) {
        assertEquals(expected = issuerId, actual = credential["issuer"]?.jsonPrimitive?.contentOrNull)
        //credential["credentialSubject"]?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull shouldBe subjectId // TODO <-- use this
        assertEquals(
            expected = subjectId,
            actual = credential["credentialSubject"]?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull?.substringBefore("#")
        ) // FIXME <-- remove
    }

    @Test
    fun testFullAuthCodeFlow() = runTest {
        println("// 0. get issuer metadata")
        val providerMetadata =
            ktorClient.get(ciTestProvider.getCIProviderMetadataUrl()).call.body<OpenIDProviderMetadata>()
        println("providerMetadata: $providerMetadata")
        assertNotNull(actual = providerMetadata.pushedAuthorizationRequestEndpoint)

        println("// 1. send pushed authorization request with authorization details, containing info of credentials to be issued, receive session id")
        val pushedAuthReq = AuthorizationRequest(
            responseType = setOf(ResponseType.Code),
            clientId = testCIClientConfig.clientID,
            redirectUri = credentialWallet.config.redirectUri,
            authorizationDetails = listOf(
                AuthorizationDetails(
                    type = OPENID_CREDENTIAL_AUTHORIZATION_TYPE,
                    format = CredentialFormat.jwt_vc_json,
                    types = listOf("VerifiableCredential", "VerifiableId")
                ), AuthorizationDetails(
                    type = OPENID_CREDENTIAL_AUTHORIZATION_TYPE,
                    format = CredentialFormat.jwt_vc_json,
                    types = listOf("VerifiableCredential", "VerifiableAttestation", "VerifiableDiploma")
                )
            )
        )
        println("pushedAuthReq: $pushedAuthReq")

        val pushedAuthResp = ktorClient.submitForm(
            providerMetadata.pushedAuthorizationRequestEndpoint!!,
            formParameters = parametersOf(pushedAuthReq.toHttpParameters())
        ).body<JsonObject>().let { PushedAuthorizationResponse.fromJSON(it) }
        println("pushedAuthResp: $pushedAuthResp")

        assertTrue(actual = pushedAuthResp.isSuccess)
        assertTrue(actual = pushedAuthResp.requestUri!!.startsWith("urn:ietf:params:oauth:request_uri:"))

        println("// 2. call authorize endpoint with request uri, receive HTTP redirect (302 Found) with Location header")
        assertNotNull(actual = providerMetadata.authorizationEndpoint)
        val authReq = AuthorizationRequest(
            responseType = setOf(ResponseType.Code),
            clientId = testCIClientConfig.clientID,
            requestUri = pushedAuthResp.requestUri
        )
        println("authReq: $authReq")
        val authResp = ktorClient.get(providerMetadata.authorizationEndpoint!!) {
            url {
                parameters.appendAll(parametersOf(authReq.toHttpParameters()))
            }
        }
        println("authResp: $authResp")
        assertEquals(expected = HttpStatusCode.Found, actual = authResp.status)
        assertContains(iterable = authResp.headers.names(), element = HttpHeaders.Location)
        val location = Url(authResp.headers[HttpHeaders.Location]!!)
        println("location: $location")
        assertTrue(actual = location.toString().startsWith(credentialWallet.config.redirectUri!!))
        assertContains(iterable = location.parameters.names(), element = ResponseType.Code.name.lowercase())

        println("// 3. Parse code response parameter from authorization redirect URI")
        assertNotNull(actual = providerMetadata.tokenEndpoint)

        val tokenReq = TokenRequest(
            grantType = GrantType.authorization_code,
            clientId = testCIClientConfig.clientID,
            redirectUri = credentialWallet.config.redirectUri,
            code = location.parameters["code"]!!
        )
        println("tokenReq: $tokenReq")

        println("// 4. Call token endpoint with code from authorization response, receive access token from response")
        val tokenResp = ktorClient.submitForm(
            providerMetadata.tokenEndpoint!!,
            formParameters = parametersOf(tokenReq.toHttpParameters())
        ).body<JsonObject>().let { TokenResponse.fromJSON(it) }
        println("tokenResp: $tokenResp")
        assertTrue(actual = tokenResp.isSuccess)
        assertNotNull(actual = tokenResp.accessToken)
        assertNotNull(actual = tokenResp.cNonce)

        println("// 5a. Call credential endpoint with access token, to receive credential (synchronous issuance)")
        assertNotNull(actual = providerMetadata.credentialEndpoint)
        ciTestProvider.deferIssuance = false
        var nonce = tokenResp.cNonce!!

        val credReq = CredentialRequest.forAuthorizationDetails(
            pushedAuthReq.authorizationDetails!!.first(),
            credentialWallet.generateDidProof(credentialWallet.TEST_DID, ciTestProvider.baseUrl, nonce)
        )
        println("credReq: $credReq")

        val credentialResp = ktorClient.post(providerMetadata.credentialEndpoint!!) {
            contentType(ContentType.Application.Json)
            bearerAuth(tokenResp.accessToken!!)
            setBody(credReq.toJSON())
        }.body<JsonObject>().let { CredentialResponse.fromJSON(it) }
        println("credentialResp: $credentialResp")

        assertTrue(actual = credentialResp.isSuccess)
        assertFalse(actual = credentialResp.isDeferred)
        assertEquals(expected = CredentialFormat.jwt_vc_json, actual = credentialResp.format!!)
        assertTrue(actual = credentialResp.credential!!.instanceOf(JsonPrimitive::class))
        val credential = credentialResp.credential!!.jsonPrimitive.content
        println(">>> Issued credential: $credential")
        //credential.issuer?.id shouldBe ciTestProvider.baseUrl
        //credential.credentialSubject?.id shouldBe credentialWallet.TEST_DID
        assertTrue(actual = JwtSignaturePolicy().verify(credential, null, mapOf()).isSuccess)
        //Auditor.getService().verify(credential, listOf(SignaturePolicy())).result shouldBe true

        nonce = credentialResp.cNonce ?: nonce

        println("// 5b. test deferred (asynchronous) credential issuance")
        assertNotNull(actual = providerMetadata.deferredCredentialEndpoint)
        ciTestProvider.deferIssuance = true

        val deferredCredResp = ktorClient.post(providerMetadata.credentialEndpoint!!) {
            contentType(ContentType.Application.Json)
            bearerAuth(tokenResp.accessToken!!)
            setBody(credReq.toJSON())
        }.body<JsonObject>().let { CredentialResponse.fromJSON(it) }
        println("deferredCredResp: $deferredCredResp")

        assertTrue(actual = deferredCredResp.isSuccess)
        assertTrue(actual = deferredCredResp.isDeferred)
        assertNotNull(actual = deferredCredResp.acceptanceToken)
        assertNull(actual = deferredCredResp.credential)

        nonce = deferredCredResp.cNonce ?: nonce

        val deferredCredResp2 = ktorClient.post(providerMetadata.deferredCredentialEndpoint!!) {
            bearerAuth(deferredCredResp.acceptanceToken!!)
        }.body<JsonObject>().let { CredentialResponse.fromJSON(it) }
        println("deferredCredResp2: $deferredCredResp2")

        assertTrue(actual = deferredCredResp2.isSuccess)
        assertFalse(actual = deferredCredResp2.isDeferred)

        val deferredCredential = deferredCredResp2.credential!!.jsonPrimitive.content
        println(">>> Issued deferred credential: $deferredCredential")

        verifyIssuerAndSubjectId(
            SDJwt.parse(deferredCredential).fullPayload["vc"]?.jsonObject!!,
            ciTestProvider.CI_ISSUER_DID, credentialWallet.TEST_DID
        )
        assertTrue(actual = JwtSignaturePolicy().verify(deferredCredential, null, mapOf()).isSuccess)

        nonce = deferredCredResp2.cNonce ?: nonce

        println("// 5c. test batch credential issuance (with one synchronous and one deferred credential)")
        assertNotNull(actual = providerMetadata.batchCredentialEndpoint)
        ciTestProvider.deferIssuance = false

        val proof = credentialWallet.generateDidProof(credentialWallet.TEST_DID, ciTestProvider.baseUrl, nonce)
        println("proof: $proof")

        val batchReq = BatchCredentialRequest(pushedAuthReq.authorizationDetails!!.map {
            CredentialRequest.forAuthorizationDetails(it, proof)
        })
        println("batchReq: $batchReq")

        val batchResp = ktorClient.post(providerMetadata.batchCredentialEndpoint!!) {
            contentType(ContentType.Application.Json)
            bearerAuth(tokenResp.accessToken!!)
            setBody(batchReq.toJSON())
        }.body<JsonObject>().let { BatchCredentialResponse.fromJSON(it) }
        println("batchResp: $batchResp")

        assertTrue(actual = batchResp.isSuccess)
        assertEquals(expected = 2, actual = batchResp.credentialResponses!!.size)
        assertFalse(actual = batchResp.credentialResponses!![0].isDeferred)
        assertNotNull(actual = batchResp.credentialResponses!![0].credential)
        assertTrue(actual = batchResp.credentialResponses!![1].isDeferred)
        assertNotNull(actual = batchResp.credentialResponses!![1].acceptanceToken)

        val batchCred1 =
            batchResp.credentialResponses!![0].credential!!.jsonPrimitive.content
        assertEquals(
            expected = "VerifiableId",
            actual = SDJwt.parse(batchCred1).fullPayload["vc"]?.jsonObject!!["type"]?.jsonArray?.last()?.jsonPrimitive?.contentOrNull
        )
        assertTrue(actual = JwtSignaturePolicy().verify(batchCred1, null, mapOf()).isSuccess)
        println("batchCred1: $batchCred1")

        val batchResp2 = ktorClient.post(providerMetadata.deferredCredentialEndpoint!!) {
            bearerAuth(batchResp.credentialResponses!![1].acceptanceToken!!)
        }.body<JsonObject>().let { CredentialResponse.fromJSON(it) }
        println("batchResp2: $batchResp2")

        assertTrue(actual = batchResp2.isSuccess)
        assertFalse(actual = batchResp2.isDeferred)
        assertNotNull(actual = batchResp2.credential)
        val batchCred2 = batchResp2.credential!!.jsonPrimitive.content
        assertEquals(
            expected = "VerifiableDiploma",
            actual = SDJwt.parse(batchCred2).fullPayload["vc"]?.jsonObject!!["type"]?.jsonArray?.last()?.jsonPrimitive?.contentOrNull
        )
        assertTrue(actual = JwtSignaturePolicy().verify(batchCred2, null, mapOf()).isSuccess)
    }

    @Test
    fun testCredentialIssuanceIsolatedFunctions() = runTest {
        // TODO: consider re-implementing CITestProvider, making use of new lib functions
        println("// -------- CREDENTIAL ISSUER ----------")
        // init credential offer for full authorization code flow
        val credOffer = CredentialOffer.Builder(ciTestProvider.baseUrl)
            .addOfferedCredential("VerifiableId")
            .addAuthorizationCodeGrant("test-state")
            .build()
        val issueReqUrl = OpenID4VCI.getCredentialOfferRequestUrl(credOffer)

        // Show credential offer request as QR code
        println(issueReqUrl)

        println("// -------- WALLET ----------")
//        val parsedCredOffer = runBlocking { OpenID4VCI.parseAndResolveCredentialOfferRequestUrl(issueReqUrl) }
        val parsedCredOffer = OpenID4VCI.parseAndResolveCredentialOfferRequestUrl(issueReqUrl)
        assertEquals(expected = credOffer.toJSONString(), actual = parsedCredOffer.toJSONString())

//        val providerMetadata = runBlocking { OpenID4VCI.resolveCIProviderMetadata(parsedCredOffer) }
        val providerMetadata = OpenID4VCI.resolveCIProviderMetadata(parsedCredOffer)
        assertEquals(expected = parsedCredOffer.credentialIssuer, actual = providerMetadata.credentialIssuer)

        println("// resolve offered credentials")
        val offeredCredentials = OpenID4VCI.resolveOfferedCredentials(parsedCredOffer, providerMetadata)
        println("offeredCredentials: $offeredCredentials")
        assertEquals(expected = 1, actual = offeredCredentials.size)
        assertEquals(expected = CredentialFormat.jwt_vc_json, actual = offeredCredentials.first().format)
        assertEquals(expected = "VerifiableId", actual = offeredCredentials.first().types?.last())
        val offeredCredential = offeredCredentials.first()
        println("offeredCredentials[0]: $offeredCredential")

        println("// go through full authorization code flow to receive offered credential")
        println("// auth request (short-cut, without pushed authorization request)")
        val authReq = AuthorizationRequest(
            setOf(ResponseType.Code), testCIClientConfig.clientID,
            redirectUri = credentialWallet.config.redirectUri,
            issuerState = parsedCredOffer.grants[GrantType.authorization_code.value]!!.issuerState
        )
        println("authReq: $authReq")

        println("// -------- CREDENTIAL ISSUER ----------")

        // create issuance session and generate authorization code
        val authCodeResponse: AuthorizationCodeResponse = AuthorizationCodeResponse.success("test-code")
        val redirectUri = authCodeResponse.toRedirectUri(authReq.redirectUri ?: TODO(), authReq.responseMode ?: ResponseMode.query)
        Url(redirectUri).let {
            assertContains(iterable = it.parameters.names(), element = ResponseType.Code.name.lowercase())
            assertEquals(
                expected = authCodeResponse.code,
                actual = it.parameters.get(ResponseType.Code.name.lowercase())
            )
        }

        println("// -------- WALLET ----------")
        println("// token req")
        val tokenReq =
            TokenRequest(
                GrantType.authorization_code,
                testCIClientConfig.clientID,
                code = authCodeResponse.code!!
            )
        println("tokenReq: $tokenReq")

        println("// -------- CREDENTIAL ISSUER ----------")

        // TODO: Validate authorization code
        // TODO: generate access token
        val accessToken = ciTestProvider.signToken(
            target = TokenTarget.ACCESS,
            payload = buildJsonObject {
            put(JWTClaims.Payload.subject, "test-issuance-session")
            put(JWTClaims.Payload.issuer, ciTestProvider.baseUrl)
            put(JWTClaims.Payload.audience, TokenTarget.ACCESS.name)
            put(JWTClaims.Payload.jwtID, "token-id")
        })
        val cNonce = "pop-nonce"
        val tokenResponse: TokenResponse = TokenResponse.success(accessToken, "bearer", cNonce = cNonce)

        println("// -------- WALLET ----------")
        assertTrue(actual = tokenResponse.isSuccess)
        assertNotNull(actual = tokenResponse.accessToken)
        assertNotNull(actual = tokenResponse.cNonce)

        println("// receive credential")
        var nonce = tokenResponse.cNonce!!
        val holderDid = TEST_WALLET_DID_WEB1
//        val holderKey = runBlocking { JWKKey.importJWK(TEST_WALLET_KEY1) }.getOrThrow()
        val holderKey = JWKKey.importJWK(TEST_WALLET_KEY1).getOrThrow()
//        val holderKeyId = runBlocking { holderKey.getKeyId() }
        val holderKeyId = holderKey.getKeyId()
        val proofKeyId = "$holderDid#$holderKeyId"
        val proofOfPossession =
            ProofOfPossession.JWTProofBuilder(ciTestProvider.baseUrl, null, nonce, proofKeyId).build(holderKey)

        val credReq = CredentialRequest.forOfferedCredential(offeredCredential, proofOfPossession)
        println("credReq: $credReq")

        println("// -------- CREDENTIAL ISSUER ----------")
        val parsedHolderKeyId = credReq.proof?.jwt?.let { JwtUtils.parseJWTHeader(it) }?.get("kid")?.jsonPrimitive?.content
        assertNotNull(actual = parsedHolderKeyId)
        assertTrue(actual = parsedHolderKeyId.startsWith("did:"))
        val parsedHolderDid = parsedHolderKeyId!!.substringBefore("#")
//        val resolvedKeyForHolderDid = runBlocking { DidService.resolveToKey(parsedHolderDid) }.getOrThrow()
        val resolvedKeyForHolderDid = DidService.resolveToKey(parsedHolderDid).getOrThrow()

        val validPoP = credReq.proof?.validateJwtProof(resolvedKeyForHolderDid, ciTestProvider.baseUrl,null, nonce, parsedHolderKeyId)
        assertTrue(actual = validPoP!!)

        val generatedCredential = ciTestProvider.generateCredential(credReq).credential
        assertNotNull(generatedCredential)
        val credentialResponse: CredentialResponse = CredentialResponse.success(credReq.format, generatedCredential!!)

        println("// -------- WALLET ----------")
        assertTrue(actual = credentialResponse.isSuccess)
        assertFalse(actual = credentialResponse.isDeferred)
        assertEquals(expected = CredentialFormat.jwt_vc_json, actual = credentialResponse.format!!)
        assertTrue(actual = credentialResponse.credential!!.instanceOf(JsonPrimitive::class))

        println("// parse and verify credential")
        val credential = credentialResponse.credential!!.jsonPrimitive.content
        println(">>> Issued credential: $credential")
        verifyIssuerAndSubjectId(
            SDJwt.parse(credential).fullPayload.get("vc")?.jsonObject!!,
            ciTestProvider.CI_ISSUER_DID, credentialWallet.TEST_DID
        )
        assertTrue(actual = JwtSignaturePolicy().verify(credential, null, mapOf()).isSuccess)
    }

    @Test
    fun testCredentialOfferFullAuth() = runTest {
        println("// -------- CREDENTIAL ISSUER ----------")
        println("// as CI provider, initialize credential offer for user")
        val issuanceSession = ciTestProvider.initializeCredentialOffer(
            CredentialOffer.Builder(ciTestProvider.baseUrl).addOfferedCredential("VerifiableId"),
            5.minutes, allowPreAuthorized = false
        )
        println("issuanceSession: $issuanceSession")
        assertNotNull(actual = issuanceSession.credentialOffer)
        val offerRequest = CredentialOfferRequest(issuanceSession.credentialOffer!!)
        val offerUri = ciTestProvider.getCredentialOfferRequestUrl(offerRequest)
        println(">>> Offer URI: $offerUri")

        println("// -------- WALLET ----------")
        println("// as WALLET: receive credential offer, either being called via deeplink or by scanning QR code")
        println("// parse credential URI")
        val parsedOfferReq = CredentialOfferRequest.fromHttpParameters(Url(offerUri).parameters.toMap())
        println("parsedOfferReq: $parsedOfferReq")

        assertNotNull(actual = parsedOfferReq.credentialOffer)
        assertNotNull(actual = parsedOfferReq.credentialOffer!!.credentialIssuer)
        assertEquals(
            expected = setOf(GrantType.authorization_code.value),
            actual = parsedOfferReq.credentialOffer!!.grants.keys
        )

        println("// get issuer metadata")
        val providerMetadataUri =
            credentialWallet.getCIProviderMetadataUrl(parsedOfferReq.credentialOffer!!.credentialIssuer)
        val providerMetadata = ktorClient.get(providerMetadataUri).call.body<OpenIDProviderMetadata>()
        println("providerMetadata: $providerMetadata")

        assertNotNull(actual = providerMetadata.credentialsSupported)

        println("// resolve offered credentials")
        val offeredCredentials = OpenID4VCI.resolveOfferedCredentials(parsedOfferReq.credentialOffer!!, providerMetadata)
        println("offeredCredentials: $offeredCredentials")

        assertEquals(expected = 1, actual = offeredCredentials.size)
        assertEquals(expected = CredentialFormat.jwt_vc_json, actual = offeredCredentials.first().format)
        assertEquals(expected = "VerifiableId", actual = offeredCredentials.first().types?.last())
        val offeredCredential = offeredCredentials.first()
        println("offeredCredentials[0]: $offeredCredential")

        println("// go through full authorization code flow to receive offered credential")
        println("// auth request (short-cut, without pushed authorization request)")
        val authReq = AuthorizationRequest(
            setOf(ResponseType.Code), testCIClientConfig.clientID,
            redirectUri = credentialWallet.config.redirectUri,
            issuerState = parsedOfferReq.credentialOffer!!.grants[GrantType.authorization_code.value]!!.issuerState
        )
        println("authReq: $authReq")

        val authResp = ktorClient.get(providerMetadata.authorizationEndpoint!!) {
            url {
                parameters.appendAll(parametersOf(authReq.toHttpParameters()))
            }
        }
        println("authResp: $authResp")

        assertEquals(expected = HttpStatusCode.Found, actual = authResp.status)
        val location = Url(authResp.headers[HttpHeaders.Location]!!)
        assertContains(iterable = location.parameters.names(), element = ResponseType.Code.name.lowercase())

        println("// token req")
        val tokenReq =
            TokenRequest(
                GrantType.authorization_code,
                testCIClientConfig.clientID,
                code = location.parameters[ResponseType.Code.name]!!
            )
        println("tokenReq: $tokenReq")

        val tokenResp = ktorClient.submitForm(
            providerMetadata.tokenEndpoint!!,
            formParameters = parametersOf(tokenReq.toHttpParameters())
        ).body<JsonObject>().let { TokenResponse.fromJSON(it) }
        println("tokenResp: $tokenResp")

        assertTrue(actual = tokenResp.isSuccess)
        assertNotNull(actual = tokenResp.accessToken)
        assertNotNull(actual = tokenResp.cNonce)

        println("// receive credential")
        ciTestProvider.deferIssuance = false
        val nonce = tokenResp.cNonce!!

        val credReq = CredentialRequest.forOfferedCredential(
            offeredCredential,
            credentialWallet.generateDidProof(credentialWallet.TEST_DID, ciTestProvider.baseUrl, nonce)
        )
        println("credReq: $credReq")

        val credentialResp = ktorClient.post(providerMetadata.credentialEndpoint!!) {
            contentType(ContentType.Application.Json)
            bearerAuth(tokenResp.accessToken!!)
            setBody(credReq.toJSON())
        }.body<JsonObject>().let { CredentialResponse.fromJSON(it) }
        println("credentialResp: $credentialResp")

        assertTrue(actual = credentialResp.isSuccess)
        assertFalse(actual = credentialResp.isDeferred)
        assertEquals(expected = CredentialFormat.jwt_vc_json, actual = credentialResp.format!!)
        assertTrue(actual = credentialResp.credential!!.instanceOf(JsonPrimitive::class))

        println("// parse and verify credential")
        val credential = credentialResp.credential!!.jsonPrimitive.content
        println(">>> Issued credential: $credential")
        verifyIssuerAndSubjectId(
            SDJwt.parse(credential).fullPayload["vc"]?.jsonObject!!,
            ciTestProvider.CI_ISSUER_DID, credentialWallet.TEST_DID
        )
        assertTrue(JwtSignaturePolicy().verify(credential, null, mapOf()).isSuccess)
    }

    @Test
    fun testPreAuthCodeFlow() = runTest {
        println("// -------- CREDENTIAL ISSUER ----------")
        println("// as CI provider, initialize credential offer for user, this time providing full offered credential object, and allowing pre-authorized code flow with user pin")
        val issuanceSession = ciTestProvider.initializeCredentialOffer(
            CredentialOffer.Builder(ciTestProvider.baseUrl)
                .addOfferedCredential(ciTestProvider.metadata.credentialsSupported!!.first().id!!),
            5.minutes, allowPreAuthorized = true, txCode = TxCode(TxInputMode.numeric), txCodeValue = "1234"
        )
        println("issuanceSession: $issuanceSession")

        assertNotNull(actual = issuanceSession.credentialOffer)
        assertEquals(
            expected = ciTestProvider.metadata.credentialsSupported!!.first().id!!,
            actual = issuanceSession.credentialOffer!!.credentialConfigurationIds.first()
        )

        val offerRequest = CredentialOfferRequest(issuanceSession.credentialOffer!!)
        println("offerRequest: $offerRequest")

        println("// create credential offer request url (this time cross-device)")
        val offerUri = ciTestProvider.getCredentialOfferRequestUrl(offerRequest)
        println("Offer URI: $offerUri")

        println("// -------- WALLET ----------")
        println("// as WALLET: receive credential offer, either being called via deeplink or by scanning QR code")
        println("// parse credential URI")
        val parsedOfferReq = CredentialOfferRequest.fromHttpParameters(Url(offerUri).parameters.toMap())
        println("parsedOfferReq: $parsedOfferReq")

        assertNotNull(actual = parsedOfferReq.credentialOffer)
        assertNotNull(actual = parsedOfferReq.credentialOffer!!.credentialIssuer)
        assertContains(
            iterable = parsedOfferReq.credentialOffer!!.grants.keys,
            element = GrantType.pre_authorized_code.value
        )
        assertNotNull(actual = parsedOfferReq.credentialOffer!!.grants[GrantType.pre_authorized_code.value]?.preAuthorizedCode)
        assertNotNull(actual = parsedOfferReq.credentialOffer!!.grants[GrantType.pre_authorized_code.value]?.txCode)

        println("// get issuer metadata")
        val providerMetadataUri =
            credentialWallet.getCIProviderMetadataUrl(parsedOfferReq.credentialOffer!!.credentialIssuer)
        val providerMetadata = ktorClient.get(providerMetadataUri).call.body<OpenIDProviderMetadata>()
        println("providerMetadata: $providerMetadata")

        assertNotNull(actual = providerMetadata.credentialsSupported)

        println("// resolve offered credentials")
        val offeredCredentials = OpenID4VCI.resolveOfferedCredentials(parsedOfferReq.credentialOffer!!, providerMetadata)
        println("offeredCredentials: $offeredCredentials")
        assertEquals(expected = 1, actual = offeredCredentials.size)
        assertEquals(expected = CredentialFormat.jwt_vc_json, actual = offeredCredentials.first().format)
        assertEquals(expected = "VerifiableId", actual = offeredCredentials.first().types?.last())
        val offeredCredential = offeredCredentials.first()
        println("offeredCredentials[0]: $offeredCredential")

        println("// fetch access token using pre-authorized code (skipping authorization step)")
        println("// try without user PIN, should be rejected!")
        var tokenReq = TokenRequest(
            grantType = GrantType.pre_authorized_code,
            //clientId = testCIClientConfig.clientID,
            redirectUri = credentialWallet.config.redirectUri,
            preAuthorizedCode = parsedOfferReq.credentialOffer!!.grants[GrantType.pre_authorized_code.value]!!.preAuthorizedCode,
            txCode = null
        )
        println("tokenReq: $tokenReq")

        var tokenResp = ktorClient.submitForm(
            providerMetadata.tokenEndpoint!!, formParameters = parametersOf(tokenReq.toHttpParameters())
        ).body<JsonObject>().let { TokenResponse.fromJSON(it) }
        println("tokenResp: $tokenResp")

        assertFalse(actual = tokenResp.isSuccess)
        assertEquals(expected = TokenErrorCode.invalid_grant.name, actual = tokenResp.error)

        println("// try with user PIN, should work:")
        tokenReq = TokenRequest(
            grantType = GrantType.pre_authorized_code,
            clientId = testCIClientConfig.clientID,
            redirectUri = credentialWallet.config.redirectUri,
            preAuthorizedCode = parsedOfferReq.credentialOffer!!.grants[GrantType.pre_authorized_code.value]!!.preAuthorizedCode,
            txCode = issuanceSession.txCodeValue
        )
        println("tokenReq: $tokenReq")

        tokenResp = ktorClient.submitForm(
            providerMetadata.tokenEndpoint!!, formParameters = parametersOf(tokenReq.toHttpParameters())
        ).body<JsonObject>().let { TokenResponse.fromJSON(it) }
        println("tokenResp: $tokenResp")

        assertTrue(actual = tokenResp.isSuccess)
        assertNotNull(actual = tokenResp.accessToken)
        assertNotNull(actual = tokenResp.cNonce)

        println("// receive credential")
        ciTestProvider.deferIssuance = false
        val nonce = tokenResp.cNonce!!

        val credReq = CredentialRequest.forOfferedCredential(
            offeredCredential,
            credentialWallet.generateDidProof(credentialWallet.TEST_DID, ciTestProvider.baseUrl, nonce)
        )
        println("credReq: $credReq")

        val credentialResp = ktorClient.post(providerMetadata.credentialEndpoint!!) {
            contentType(ContentType.Application.Json)
            bearerAuth(tokenResp.accessToken!!)
            setBody(credReq.toJSON())
        }.body<JsonObject>().let { CredentialResponse.fromJSON(it) }
        println("credentialResp: $credentialResp")

        assertTrue(actual = credentialResp.isSuccess)
        assertFalse(actual = credentialResp.isDeferred)
        assertEquals(expected = CredentialFormat.jwt_vc_json, actual = credentialResp.format!!)
        assertTrue(actual = credentialResp.credential!!.instanceOf(JsonPrimitive::class))

        println("// parse and verify credential")
        val credential = credentialResp.credential!!.jsonPrimitive.content
        println(">>> Issued credential: $credential")

        verifyIssuerAndSubjectId(
            SDJwt.parse(credential).fullPayload["vc"]?.jsonObject!!,
            ciTestProvider.CI_ISSUER_DID, credentialWallet.TEST_DID
        )
        assertTrue(actual = JwtSignaturePolicy().verify(credential, null, mapOf()).isSuccess)
    }

    @Test
    fun testFullAuthImplicitFlow() = runTest {
        println("// 0. get issuer metadata")
        val providerMetadata =
            ktorClient.get(ciTestProvider.getCIProviderMetadataUrl()).call.body<OpenIDProviderMetadata>()
        println("providerMetadata: $providerMetadata")

        println("// 1. send pushed authorization request with authorization details, containing info of credentials to be issued, receive session id")
        val implicitAuthReq = AuthorizationRequest(
            responseType = setOf(ResponseType.Token),
            responseMode = ResponseMode.fragment,
            clientId = testCIClientConfig.clientID,
            redirectUri = credentialWallet.config.redirectUri,
            authorizationDetails = listOf(
                AuthorizationDetails(
                    type = OPENID_CREDENTIAL_AUTHORIZATION_TYPE,
                    format = CredentialFormat.jwt_vc_json,
                    types = listOf("VerifiableCredential", "VerifiableId")
                ), AuthorizationDetails(
                    type = OPENID_CREDENTIAL_AUTHORIZATION_TYPE,
                    format = CredentialFormat.jwt_vc_json,
                    types = listOf("VerifiableCredential", "VerifiableAttestation", "VerifiableDiploma")
                )
            )
        )
        println("implicitAuthReq: $implicitAuthReq")

        println("// 2. call authorize endpoint with request uri, receive HTTP redirect (302 Found) with Location header")
        assertNotNull(actual = providerMetadata.authorizationEndpoint)
        val authResp = ktorClient.get(providerMetadata.authorizationEndpoint!!) {
            url {
                parameters.appendAll(parametersOf(implicitAuthReq.toHttpParameters()))
            }
        }
        println("authResp: $authResp")

        assertEquals(expected = HttpStatusCode.Found, actual = authResp.status)
        assertContains(iterable = authResp.headers.names(), element = HttpHeaders.Location)

        val location = Url(authResp.headers[HttpHeaders.Location]!!)
        println("location: $location")
        assertTrue(actual = location.toString().startsWith(credentialWallet.config.redirectUri!!))
        assertFalse(actual = location.fragment.isEmpty())

        val locationWithQueryParams = Url("http://blank?${location.fragment}")
        val tokenResp = TokenResponse.fromHttpParameters(locationWithQueryParams.parameters.toMap())
        println("tokenResp: $tokenResp")

        assertTrue(actual = tokenResp.isSuccess)
        assertNotNull(actual = tokenResp.accessToken)
        assertNotNull(actual = tokenResp.cNonce)

        println("// 3a. Call credential endpoint with access token, to receive credential (synchronous issuance)")
        assertNotNull(actual = providerMetadata.credentialEndpoint)
        ciTestProvider.deferIssuance = false

        val credReq = CredentialRequest.forAuthorizationDetails(
            implicitAuthReq.authorizationDetails!!.first(),
            credentialWallet.generateDidProof(credentialWallet.TEST_DID, ciTestProvider.baseUrl, tokenResp.cNonce!!)
        )
        println("credReq: $credReq")

        val credentialResp = ktorClient.post(providerMetadata.credentialEndpoint!!) {
            contentType(ContentType.Application.Json)
            bearerAuth(tokenResp.accessToken!!)
            setBody(credReq.toJSON())
        }.body<JsonObject>().let { CredentialResponse.fromJSON(it) }
        println("credentialResp: $credentialResp")

        assertTrue(actual = credentialResp.isSuccess)
        assertFalse(actual = credentialResp.isDeferred)
        assertEquals(expected = CredentialFormat.jwt_vc_json, actual = credentialResp.format!!)
        assertTrue(actual = credentialResp.credential!!.instanceOf(JsonPrimitive::class))

        val credential = credentialResp.credential!!.jsonPrimitive.content
        println(">>> Issued credential: $credential")

        verifyIssuerAndSubjectId(
            SDJwt.parse(credential).fullPayload["vc"]?.jsonObject!!,
            ciTestProvider.CI_ISSUER_DID, credentialWallet.TEST_DID
        )
        assertTrue(actual = JwtSignaturePolicy().verify(credential, null, mapOf()).isSuccess)
    }

    val issuerPortalRequest =
        "openid-credential-offer://issuer.portal.walt.id/?credential_offer=%7B%22credential_issuer%22%3A%22https%3A%2F%2Fissuer.portal.walt.id%22%2C%22credentials%22%3A%5B%7B%22format%22%3A%22jwt_vc_json%22%2C%22types%22%3A%5B%22VerifiableCredential%22%2C%22OpenBadgeCredential%22%5D%2C%22credential_definition%22%3A%7B%22%40context%22%3A%5B%22https%3A%2F%2Fwww.w3.org%2F2018%2Fcredentials%2Fv1%22%2C%22https%3A%2F%2Fw3c-ccg.github.io%2Fvc-ed%2Fplugfest-1-2022%2Fjff-vc-edu-plugfest-1-context.json%22%2C%22https%3A%2F%2Fw3id.org%2Fsecurity%2Fsuites%2Fed25519-2020%2Fv1%22%5D%2C%22types%22%3A%5B%22VerifiableCredential%22%2C%22OpenBadgeCredential%22%5D%7D%7D%5D%2C%22grants%22%3A%7B%22authorization_code%22%3A%7B%22issuer_state%22%3A%22c7228046-1a8e-4e27-a7b1-cd6479e1455f%22%7D%2C%22urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Apre-authorized_code%22%3A%7B%22pre-authorized_code%22%3A%22eyJhbGciOiJFZERTQSJ9.eyJzdWIiOiJjNzIyODA0Ni0xYThlLTRlMjctYTdiMS1jZDY0NzllMTQ1NWYiLCJpc3MiOiJodHRwczovL2lzc3Vlci5wb3J0YWwud2FsdC5pZCIsImF1ZCI6IlRPS0VOIn0.On2_7P4vr5caTHKbWv2i0a604HQ-FaiuVZHH9kzEKK7mOdVHtNHoAZADpDJtowNCkhMQxruLbnqB7WvRQzufCg%22%2C%22user_pin_required%22%3Afalse%7D%7D%7D"

    //@Test
    suspend fun testIssuerPortalRequest() {
        val credOfferReq = CredentialOfferRequest.fromHttpQueryString(Url(issuerPortalRequest).encodedQuery)
        assertNotNull(actual = credOfferReq.credentialOffer?.credentialIssuer)
        println("// get issuer metadata")
        val providerMetadataUri =
            credentialWallet.getCIProviderMetadataUrl(credOfferReq.credentialOffer!!.credentialIssuer)
        val providerMetadata = ktorClient.get(providerMetadataUri).call.body<OpenIDProviderMetadata>()
        println("providerMetadata: $providerMetadata")
        assertNotNull(actual = providerMetadata.authorizationEndpoint)
        println("// resolve offered credentials")
        val offeredCredentials = OpenID4VCI.resolveOfferedCredentials(credOfferReq.credentialOffer!!, providerMetadata)
        println("offeredCredentials: $offeredCredentials")

        assertContains(iterable = providerMetadata.grantTypesSupported, element = GrantType.pre_authorized_code)
        assertContains(map = credOfferReq.credentialOffer!!.grants, key = GrantType.pre_authorized_code.value)

        // make token request
        val tokenReq = TokenRequest(
            grantType = GrantType.pre_authorized_code,
            //clientId = testCIClientConfig.clientID,
            redirectUri = credentialWallet.config.redirectUri,
            preAuthorizedCode = credOfferReq.credentialOffer!!.grants[GrantType.pre_authorized_code.value]!!.preAuthorizedCode,
            txCode = null
        )
        println("tokenReq: $tokenReq")
        val tokenResp = ktorClient.submitForm(
            providerMetadata.tokenEndpoint!!, formParameters = parametersOf(tokenReq.toHttpParameters())
        ).body<JsonObject>().let { TokenResponse.fromJSON(it) }
        println("tokenResp: $tokenResp")
        assertNotNull(actual = tokenResp.accessToken)

        // make credential request
        val credReq = CredentialRequest.forOfferedCredential(
            offeredCredentials.first(),
            credentialWallet.generateDidProof(credentialWallet.TEST_DID, ciTestProvider.baseUrl, tokenResp.cNonce!!)
        )
        println("credReq: $credReq")

        val credentialResp = ktorClient.post(providerMetadata.credentialEndpoint!!) {
            contentType(ContentType.Application.Json)
            bearerAuth(tokenResp.accessToken!!)
            setBody(credReq.toJSON())
        }.body<JsonObject>().let { CredentialResponse.fromJSON(it) }
        println("credentialResp: $credentialResp")

        assertTrue(actual = credentialResp.isSuccess)
        assertNotNull(actual = credentialResp.credential)
        println(SDJwt.parse(credentialResp.credential!!.jsonPrimitive.content).fullPayload.toString())
    }

    val mattrCredentialOffer =
        "openid-credential-offer://?credential_offer=%7B%22credential_issuer%22%3A%22https%3A%2F%2Flaunchpad.vii.electron.mattrlabs.io%22%2C%22credentials%22%3A%5B%7B%22format%22%3A%22jwt_vc_json%22%2C%22types%22%3A%5B%22OpenBadgeCredential%22%5D%7D%5D%2C%22grants%22%3A%7B%22urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Apre-authorized_code%22%3A%7B%22pre-authorized_code%22%3A%22VphetImmqY-iPICjhRzGPk-QV7-TeT0wVD-sTh9rZ9k%22%7D%7D%7D"

    //@Test
    suspend fun testMattrCredentialOffer() {
        val credOfferReq = CredentialOfferRequest.fromHttpQueryString(Url(mattrCredentialOffer).encodedQuery)
        assertNotNull(actual = credOfferReq.credentialOffer?.credentialIssuer)
        println("// get issuer metadata")
        val providerMetadataUri =
            credentialWallet.getCIProviderMetadataUrl(credOfferReq.credentialOffer!!.credentialIssuer)
        val providerMetadata =
            ktorClient.get(providerMetadataUri).call.body<JsonObject>().let { OpenIDProviderMetadata.fromJSON(it) }
        println("providerMetadata: $providerMetadata")
        assertNotNull(actual = providerMetadata.authorizationEndpoint)
        println("// resolve offered credentials")
        val offeredCredentials = OpenID4VCI.resolveOfferedCredentials(credOfferReq.credentialOffer!!, providerMetadata)
        println("offeredCredentials: $offeredCredentials")

        assertContains(iterable = providerMetadata.grantTypesSupported, element = GrantType.pre_authorized_code)
        assertContains(credOfferReq.credentialOffer!!.grants, GrantType.pre_authorized_code.value)

        // make token request
        val tokenReq = TokenRequest(
            grantType = GrantType.pre_authorized_code,
            clientId = testCIClientConfig.clientID,
            redirectUri = credentialWallet.config.redirectUri,
            preAuthorizedCode = credOfferReq.credentialOffer!!.grants[GrantType.pre_authorized_code.value]!!.preAuthorizedCode,
            txCode = null
        )
        println("tokenReq: ${tokenReq.toHttpQueryString()}")
        val tokenResp = ktorClient.submitForm(
            providerMetadata.tokenEndpoint!!, formParameters = parametersOf(tokenReq.toHttpParameters())
        ).body<JsonObject>().let { TokenResponse.fromJSON(it) }
        println("tokenResp: $tokenResp")
        assertNotNull(actual = tokenResp.accessToken)

        // make credential request
        val credReq = CredentialRequest.forOfferedCredential(
            offeredCredentials.first(),
            credentialWallet.generateDidProof(
                credentialWallet.TEST_DID, credOfferReq.credentialOffer!!.credentialIssuer, tokenResp.cNonce
            )
        )
        println("credReq: $credReq")

        val credentialResp = ktorClient.post(providerMetadata.credentialEndpoint!!) {
            contentType(ContentType.Application.Json)
            bearerAuth(tokenResp.accessToken!!)
            setBody(credReq.toJSON())
        }.body<JsonObject>().let { CredentialResponse.fromJSON(it) }
        println("credentialResp: $credentialResp")

        assertTrue(actual = credentialResp.isSuccess)
        assertNotNull(actual = credentialResp.credential)
        println(SDJwt.parse(credentialResp.credential!!.jsonPrimitive.content).fullPayload.toString())
    }

    val spheronCredOffer =
        "openid-credential-offer://?credential_offer=%7B%22grants%22%3A%7B%22urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Apre-authorized_code%22%3A%7B%22pre-authorized_code%22%3A%221P8XydcKW1Gy5y7e1u25mM%22%2C%22user_pin_required%22%3Afalse%7D%7D%2C%22credentials%22%3A%5B%22OpenBadgeCredential%22%5D%2C%22credential_issuer%22%3A%22https%3A%2F%2Fssi.sphereon.com%2Fpf3%22%7D"

    //@Test
    suspend fun parseSpheronCredOffer() {
        val credOfferReq = CredentialOfferRequest.fromHttpQueryString(Url(spheronCredOffer).encodedQuery)
        assertNotNull(actual = credOfferReq.credentialOffer)
        val providerMetadataUri =
            credentialWallet.getCIProviderMetadataUrl(credOfferReq.credentialOffer!!.credentialIssuer)
        val providerMetadata =
            ktorClient.get(providerMetadataUri).call.body<JsonObject>().let { OpenIDProviderMetadata.fromJSON(it) }
        println("providerMetadata: $providerMetadata")
        assertNotNull(actual = providerMetadata.tokenEndpoint)
        assertNotNull(actual = providerMetadata.credentialEndpoint)
        println("// resolve offered credentials")
        val offeredCredentials = OpenID4VCI.resolveOfferedCredentials(credOfferReq.credentialOffer!!, providerMetadata)
        println("offeredCredentials: $offeredCredentials")

        // make token request
        val tokenReq = TokenRequest(
            grantType = GrantType.pre_authorized_code,
            clientId = testCIClientConfig.clientID,
            redirectUri = credentialWallet.config.redirectUri,
            preAuthorizedCode = credOfferReq.credentialOffer!!.grants[GrantType.pre_authorized_code.value]!!.preAuthorizedCode,
            txCode = null
        )
        println("tokenReq: ${tokenReq.toHttpQueryString()}")
        val tokenResp = ktorClient.submitForm(
            providerMetadata.tokenEndpoint!!, formParameters = parametersOf(tokenReq.toHttpParameters())
        ).body<JsonObject>().let { TokenResponse.fromJSON(it) }
        println("tokenResp: $tokenResp")
        assertNotNull(actual = tokenResp.accessToken)

        // make credential request
        val credReq = CredentialRequest.forOfferedCredential(
            offeredCredentials.first(),
            credentialWallet.generateDidProof(
                credentialWallet.TEST_DID, credOfferReq.credentialOffer?.credentialIssuer
                    ?: credOfferReq.credentialOffer!!.credentialIssuer, tokenResp.cNonce
            )
        )
        println("credReq: $credReq")

        val credentialResp = ktorClient.post(providerMetadata.credentialEndpoint!!) {
            contentType(ContentType.Application.Json)
            bearerAuth(tokenResp.accessToken!!)
            setBody(credReq.toJSON())
        }.body<JsonObject>().let { CredentialResponse.fromJSON(it) }
        println("credentialResp: $credentialResp")

        assertTrue(actual = credentialResp.isSuccess)
        assertNotNull(actual = credentialResp.credential)
        println(SDJwt.parse(credentialResp.credential!!.jsonPrimitive.content).fullPayload.toString())
    }

    // issuance by reference

    //@Test
    suspend fun testCredentialOfferByReference() {
        println("// -------- CREDENTIAL ISSUER ----------")
        println("// as CI provider, initialize credential offer for user, this time providing full offered credential object, and allowing pre-authorized code flow with user pin")
        val issuanceSession = ciTestProvider.initializeCredentialOffer(
            CredentialOffer.Builder(ciTestProvider.baseUrl)
                .addOfferedCredential(ciTestProvider.metadata.credentialsSupported!!.first().id!!),
            5.minutes, allowPreAuthorized = true, TxCode(TxInputMode.numeric), txCodeValue = "1234"
        )
        println("issuanceSession: $issuanceSession")

        assertNotNull(actual = issuanceSession.credentialOffer)
        assertEquals(
            expected = ciTestProvider.metadata.credentialsSupported!!.first().id!!,
            actual = issuanceSession.credentialOffer!!.credentialConfigurationIds.first()
        )

        val offerRequest = ciTestProvider.getCredentialOfferRequest(issuanceSession, byReference = true)
        println("offerRequest: $offerRequest")
        assertNull(actual = offerRequest.credentialOffer)
        assertNotNull(actual = offerRequest.credentialOfferUri)

        println("// create credential offer request url (this time cross-device)")
        val offerUri = ciTestProvider.getCredentialOfferRequestUrl(offerRequest)
        println("Offer URI: $offerUri")

        println("// -------- WALLET ----------")
        println("// as WALLET: receive credential offer, either being called via deeplink or by scanning QR code")
        println("// parse credential URI")

        val credentialOffer =
            credentialWallet.resolveCredentialOffer(CredentialOfferRequest.fromHttpParameters(Url(offerUri).parameters.toMap()))

        assertNotNull(actual = credentialOffer.credentialIssuer)
        assertContains(iterable = credentialOffer.grants.keys, element = GrantType.pre_authorized_code.value)
        assertNotNull(actual = credentialOffer.grants[GrantType.pre_authorized_code.value]?.preAuthorizedCode)
        assertNotNull(actual = credentialOffer.grants[GrantType.pre_authorized_code.value]?.txCode)

        println("// get issuer metadata")
        val providerMetadataUri =
            credentialWallet.getCIProviderMetadataUrl(credentialOffer.credentialIssuer)
        val providerMetadata = ktorClient.get(providerMetadataUri).call.body<OpenIDProviderMetadata>()
        println("providerMetadata: $providerMetadata")

        assertNotNull(actual = providerMetadata.credentialsSupported)

        println("// resolve offered credentials")
        val offeredCredentials = OpenID4VCI.resolveOfferedCredentials(credentialOffer, providerMetadata)
        println("offeredCredentials: $offeredCredentials")
        assertEquals(expected = 1, actual = offeredCredentials.size)
        assertEquals(expected = CredentialFormat.jwt_vc_json, actual = offeredCredentials.first().format)
        assertEquals(expected = "VerifiableId", actual = offeredCredentials.first().types?.last())
        val offeredCredential = offeredCredentials.first()
        println("offeredCredentials[0]: $offeredCredential")

        println("// fetch access token using pre-authorized code (skipping authorization step)")
        println("// try without user PIN, should be rejected!")
        var tokenReq = TokenRequest(
            grantType = GrantType.pre_authorized_code,
            //clientId = testCIClientConfig.clientID,
            redirectUri = credentialWallet.config.redirectUri,
            preAuthorizedCode = credentialOffer.grants[GrantType.pre_authorized_code.value]!!.preAuthorizedCode,
            txCode = null
        )
        println("tokenReq: $tokenReq")

        var tokenResp = ktorClient.submitForm(
            providerMetadata.tokenEndpoint!!, formParameters = parametersOf(tokenReq.toHttpParameters())
        ).body<JsonObject>().let { TokenResponse.fromJSON(it) }
        println("tokenResp: $tokenResp")

        assertFalse(actual = tokenResp.isSuccess)
        assertEquals(expected = TokenErrorCode.invalid_grant.name, actual = tokenResp.error)

        println("// try with user PIN, should work:")
        tokenReq = TokenRequest(
            grantType = GrantType.pre_authorized_code,
            clientId = testCIClientConfig.clientID,
            redirectUri = credentialWallet.config.redirectUri,
            preAuthorizedCode = credentialOffer.grants[GrantType.pre_authorized_code.value]!!.preAuthorizedCode,
            txCode = issuanceSession.txCodeValue
        )
        println("tokenReq: $tokenReq")

        tokenResp = ktorClient.submitForm(
            providerMetadata.tokenEndpoint!!, formParameters = parametersOf(tokenReq.toHttpParameters())
        ).body<JsonObject>().let { TokenResponse.fromJSON(it) }
        println("tokenResp: $tokenResp")

        assertTrue(actual = tokenResp.isSuccess)
        assertNotNull(actual = tokenResp.accessToken)
        assertNotNull(actual = tokenResp.cNonce)

        println("// receive credential")
        ciTestProvider.deferIssuance = false
        val nonce = tokenResp.cNonce!!

        val credReq = CredentialRequest.forOfferedCredential(
            offeredCredential,
            credentialWallet.generateDidProof(credentialWallet.TEST_DID, ciTestProvider.baseUrl, nonce)
        )
        println("credReq: $credReq")

        val credentialResp = ktorClient.post(providerMetadata.credentialEndpoint!!) {
            contentType(ContentType.Application.Json)
            bearerAuth(tokenResp.accessToken!!)
            setBody(credReq.toJSON())
        }.body<JsonObject>().let { CredentialResponse.fromJSON(it) }
        println("credentialResp: $credentialResp")

        assertTrue(actual = credentialResp.isSuccess)
        assertFalse(actual = credentialResp.isDeferred)
        assertEquals(expected = CredentialFormat.jwt_vc_json, actual = credentialResp.format!!)
        assertTrue(actual = credentialResp.credential!!.instanceOf(JsonPrimitive::class))

        println("// parse and verify credential")
        val credential = credentialResp.credential!!.jsonPrimitive.content
        println(">>> Issued credential: $credential")
        verifyIssuerAndSubjectId(
            SDJwt.parse(credential).fullPayload["vc"]?.jsonObject!!,
            ciTestProvider.CI_ISSUER_DID, credentialWallet.TEST_DID
        )
        assertTrue(actual = JwtSignaturePolicy().verify(credential, null, mapOf()).isSuccess)
    }


    val http = HttpClient() {
        install(ContentNegotiation) {
            json()
        }
        install(Logging) {
            logger = Logger.SIMPLE
            level = LogLevel.ALL
        }
        followRedirects = false
    }

    suspend fun entraAuthorize(): String {
        val tenantId = "8bc955d9-38fd-4c15-a520-0c656407537a"
        val clientId = "e50ceaa6-8554-4ae6-bfdf-fd95e2243ae0"
        val clientSecret = "ctL8Q~Ezdrcrju85gEtvbCmQQDmm7bXjJKsdXbCr"
        val response = http.submitForm("https://login.microsoftonline.com/$tenantId/oauth2/v2.0/token", parameters {
            append("client_id", clientId)
            append("scope", "3db474b9-6a0c-4840-96ac-1fceb342124f/.default")
            append("client_secret", clientSecret)
            append("grant_type", "client_credentials")
        }).body<JsonObject>()
        return "${response["token_type"]!!.jsonPrimitive.content} ${response["access_token"]!!.jsonPrimitive.content}"
    }

    // Point browser to: https://login.microsoftonline.com/8bc955d9-38fd-4c15-a520-0c656407537a/oauth2/v2.0/authorize?client_id=e50ceaa6-8554-4ae6-bfdf-fd95e2243ae0&response_type=id_token&redirect_uri=http%3A%2F%2Flocalhost:8000%2F&response_mode=fragment&scope=openid&state=12345&nonce=678910&login_hint=test%40severinstamplergmail.onmicrosoft.com
    val ms_id_token =
        "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsImtpZCI6IlQxU3QtZExUdnlXUmd4Ql82NzZ1OGtyWFMtSSJ9.eyJhdWQiOiJlNTBjZWFhNi04NTU0LTRhZTYtYmZkZi1mZDk1ZTIyNDNhZTAiLCJpc3MiOiJodHRwczovL2xvZ2luLm1pY3Jvc29mdG9ubGluZS5jb20vOGJjOTU1ZDktMzhmZC00YzE1LWE1MjAtMGM2NTY0MDc1MzdhL3YyLjAiLCJpYXQiOjE3MDMwMDExMjQsIm5iZiI6MTcwMzAwMTEyNCwiZXhwIjoxNzAzMDA1MDI0LCJhaW8iOiJBVVFBdS84VkFBQUF0ZTdkTWtZcFN2WWhwaUxpVmluSXF4V1hJREhXb1FoRnpUZVAwc0RLRGxiTWtRT0ZtRzJqckwxQ0dlVXlzTDlyVEg2emhPOTBJenJ3VExFbWc3elBJUT09IiwiY2MiOiJDZ0VBRWlSelpYWmxjbWx1YzNSaGJYQnNaWEpuYldGcGJDNXZibTFwWTNKdmMyOW1kQzVqYjIwYUVnb1FoY0UxZmwvS1lFMmJZT0c5R1FZN2VTSVNDaEJ6TVltQTdXQTFTNFNubmxyY3RXNEFNZ0pGVlRnQSIsIm5vbmNlIjoiNjc4OTEwIiwicmgiOiIwLkFYa0EyVlhKaV8wNEZVeWxJQXhsWkFkVGVxYnFET1ZVaGVaS3Y5XzlsZUlrT3VDVUFGVS4iLCJzdWIiOiI0cDgyb3hySGhiZ2x4V01oTDBIUmpKbDNRTjZ2eDhMS1pQWkVyLW9wako0IiwidGlkIjoiOGJjOTU1ZDktMzhmZC00YzE1LWE1MjAtMGM2NTY0MDc1MzdhIiwidXRpIjoiY3pHSmdPMWdOVXVFcDU1YTNMVnVBQSIsInZlciI6IjIuMCJ9.DE9LEsmzx9BG0z4Q7d-g_CH8ach4-cm7yztGHuHJykdLCjznu131nRsOFc9HdnIIqzHUX8kj1ZtAlPMLRaDYVYasKomRO4Fx7GCLY6kG5szQZJ8t8hkwX4O_zk7IaDHtn4HiyfwfSPwZjknMiQpTyiAqUqt0tR8ojSf5VeKnQmChvmp0w86izNYwTmWx5OOx2FXLsDEmvF42mp96bSsvyQt6hn4FcmhYkE4nf_5nHssb3SsL485ppHjWOvj81nGanK_u4iKVkfY_9KFF98hOwtWEi1UyvlTo5CdyYkehV0ZVs4gFAKiV7L5uasI-MYIlg0kUEK-mtMjHhU9TWIa4SA"
    // "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsImtpZCI6IlQxU3QtZExUdnlXUmd4Ql82NzZ1OGtyWFMtSSJ9.eyJhdWQiOiJlNTBjZWFhNi04NTU0LTRhZTYtYmZkZi1mZDk1ZTIyNDNhZTAiLCJpc3MiOiJodHRwczovL2xvZ2luLm1pY3Jvc29mdG9ubGluZS5jb20vOGJjOTU1ZDktMzhmZC00YzE1LWE1MjAtMGM2NTY0MDc1MzdhL3YyLjAiLCJpYXQiOjE3MDI4OTYzMDIsIm5iZiI6MTcwMjg5NjMwMiwiZXhwIjoxNzAyOTAwMjAyLCJhaW8iOiJBYlFBUy84VkFBQUE2QlpyS1IvZkJpaHlwalFHRy9hcDJpNXJxVzBBSDY1TDVYVFI1K0VZRTRqNGhIL2RUdStheGFrRUFwd2RLU0RJN1ZKck01bXN6SEx4YklYTE5uRS9uaHVxZWZRcHJaeDF5ZGR1Z1JVN0NjcU4wdzFyZzlZTUlHRkFpeGtVV2N4cllhbmhEbmNqTlFLVnloYThKNXlVTHd2MW85MXdYQzFFZS9aQmo3dWtBczBORXdZeTd2K2JRQnpkS250Y3BaY1NWZWhlY2xVV0NkWEJUVnZpZzdqTjVRVHEzc01QSHJGUWtxMERZQjRTU3l3PSIsImlkcCI6Imh0dHBzOi8vc3RzLndpbmRvd3MubmV0LzkxODgwNDBkLTZjNjctNGM1Yi1iMTEyLTM2YTMwNGI2NmRhZC8iLCJub25jZSI6IjY3ODkxMCIsInByb3ZfZGF0YSI6W3siYXQiOnRydWUsInByb3YiOiJzYW1zdW5nLmNvbSIsImFsdHNlY2lkIjoiZDhkN2dTOVVGRUVORU1uUTZ5SmNKZWxLX2IxX3VhN0V4QXR6Z0F6WFdmUSJ9XSwicmgiOiIwLkFYa0EyVlhKaV8wNEZVeWxJQXhsWkFkVGVxYnFET1ZVaGVaS3Y5XzlsZUlrT3VDVUFCTS4iLCJzdWIiOiJobXd2QklDT0p2TEFZU2ktRW1PZ0ZXaF95ODFVRXBuRGNpa3d0dlBVdU5NIiwidGlkIjoiOGJjOTU1ZDktMzhmZC00YzE1LWE1MjAtMGM2NTY0MDc1MzdhIiwidXRpIjoiSFh3YXRnT0tOMHVVRXdGaWpnSjRBQSIsInZlciI6IjIuMCJ9.kXoMA1Y-KdL8Z_Guq5Jzq-6zhrKECdVm6uDOFeRr39oegCeFYA8FJG2fesmQq5q5MWBWcETAp6Ovyx6SmSVQIicWE8PhH2aD40NsIEq-rXkovaqNimhZzkuuwqh0LlIDBbE_l3qtIkfXaUYS2UE029ggmX16Ek0rrs6JunD3MAO_Y7K4kZrSKRjozrbBv_NN1xZPp51RC5PuU9Lb6aacXPgTJaImvA31aNwSbAJohqdZlgX6vwakRaZFQWVtIaTEeedzqOump8wyNqSkSOTJLZLehWgmPF7cSLUZ0hhsiZUH0BPby_X8dvpwVjs6155jBIFo5iFJsBgxFJRu0VO71Q"

    val TEST_WALLET_KEY1 =
        "{\"kty\":\"EC\",\"d\":\"uD-uxub011cplvr5Bd6MrIPSEUBsgLk-C1y3tnmfetQ\",\"use\":\"sig\",\"crv\":\"secp256k1\",\"kid\":\"48d8a34263cf492aa7ff61b6183e8bcf\",\"x\":\"TKaQ6sCocTDsmuj9tTR996tFXpEcS2EJN-1gOadaBvk\",\"y\":\"0TrIYHcfC93VpEuvj-HXTnyKt0snayOMwGSJA1XiDX8\"}"
    val TEST_WALLET_KEY2 =
        "{\"kty\":\"OKP\",\"d\":\"q-ET7OMlI_chsYr0bV4mWDGTWuU-Cw_xWLvQkqExnwM\",\"crv\":\"Ed25519\",\"kid\":\"4cULZU4BQEJYax3vyqRKpGfkc_jcYtth0Wh-iPJa1hk\",\"x\":\"B0XctHANkPzJdjSoHrumdOh0wtsAQuNKas0N_QfzvDo\"}"
    val TEST_WALLET_KEY = TEST_WALLET_KEY1

    val TEST_WALLET_DID_ION =
        "did:ion:EiDh0EL8wg8oF-7rRiRzEZVfsJvh4sQX4Jock2Kp4j_zxg:eyJkZWx0YSI6eyJwYXRjaGVzIjpbeyJhY3Rpb24iOiJyZXBsYWNlIiwiZG9jdW1lbnQiOnsicHVibGljS2V5cyI6W3siaWQiOiI0OGQ4YTM0MjYzY2Y0OTJhYTdmZjYxYjYxODNlOGJjZiIsInB1YmxpY0tleUp3ayI6eyJjcnYiOiJzZWNwMjU2azEiLCJraWQiOiI0OGQ4YTM0MjYzY2Y0OTJhYTdmZjYxYjYxODNlOGJjZiIsImt0eSI6IkVDIiwidXNlIjoic2lnIiwieCI6IlRLYVE2c0NvY1REc211ajl0VFI5OTZ0RlhwRWNTMkVKTi0xZ09hZGFCdmsiLCJ5IjoiMFRySVlIY2ZDOTNWcEV1dmotSFhUbnlLdDBzbmF5T013R1NKQTFYaURYOCJ9LCJwdXJwb3NlcyI6WyJhdXRoZW50aWNhdGlvbiJdLCJ0eXBlIjoiRWNkc2FTZWNwMjU2azFWZXJpZmljYXRpb25LZXkyMDE5In1dfX1dLCJ1cGRhdGVDb21taXRtZW50IjoiRWlCQnlkZ2R5WHZkVERob3ZsWWItQkV2R3ExQnR2TWJSLURmbDctSHdZMUhUZyJ9LCJzdWZmaXhEYXRhIjp7ImRlbHRhSGFzaCI6IkVpRGJxa05ldzdUcDU2cEJET3p6REc5bThPZndxamlXRjI3bTg2d1k3TS11M1EiLCJyZWNvdmVyeUNvbW1pdG1lbnQiOiJFaUFGOXkzcE1lQ2RQSmZRYjk1ZVV5TVlfaUdCRkMwdkQzeDNKVTB6V0VjWUtBIn19"
    val TEST_WALLET_DID_WEB1 = "did:web:entra.walt.id:holder"
    val TEST_WALLET_DID_WEB2 = "did:web:entra.walt.id:holder2"
    val TEST_WALLET_DID_JWK =
        "did:jwk:eyJrdHkiOiJFQyIsInVzZSI6InNpZyIsImNydiI6InNlY3AyNTZrMSIsImtpZCI6IjQ4ZDhhMzQyNjNjZjQ5MmFhN2ZmNjFiNjE4M2U4YmNmIiwieCI6IlRLYVE2c0NvY1REc211ajl0VFI5OTZ0RlhwRWNTMkVKTi0xZ09hZGFCdmsiLCJ5IjoiMFRySVlIY2ZDOTNWcEV1dmotSFhUbnlLdDBzbmF5T013R1NKQTFYaURYOCJ9"
    val TEST_WALLET_DID_KEY =
        "did:key:zdCru39GRVTj7Y6gKRbT9axbErpR9xAq9GmQkBz1DwnYxpMv4GsjWxZM3anG94V4PsTg12RDWt7Ss7dN2SDBd2A948UKMDxTD8oxRPujyyZ9Fcvv2saXeyp41jst"
    val TEST_WALLET_DID = TEST_WALLET_DID_WEB1

    //@Test
    suspend fun createDidWebDoc() {
        val pubKey = JWKKey.importJWK(TEST_WALLET_KEY).getOrThrow().getPublicKey()
        val didWebResult = DidService.registerByKey(
            "web",
            pubKey,
            DidWebCreateOptions("entra.walt.id", "holder", KeyType.secp256k1)
        )
        println(didWebResult.didDocument)
        // NEED TO UPDATE did document, so that verificationMethod id #0 equals ids of other methods!

        val didJwkResult = DidService.registerByKey("jwk", pubKey, DidJwkCreateOptions(KeyType.secp256k1))
        println(didJwkResult.did)
        val didKeyResult = DidService.registerByKey("key", pubKey, DidKeyCreateOptions(KeyType.secp256k1))
        println(didKeyResult.did)
    }

    val ENTRA_CALLBACK_PORT = 9002
    val CALLBACK_COMPLETE = Object()
    var ENTRA_STATUS: String = "none"
    suspend fun startEntraCallbackServer() {
        embeddedServer(Netty, port = ENTRA_CALLBACK_PORT) {
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
            routing {
                post("/callback") {
                    println("ENTRA CALLBACK (Issuer)")
                    val callbackBody = call.receiveText().also { println(it) }.let { Json.parseToJsonElement(it) }.jsonObject
                    ENTRA_STATUS = callbackBody["requestStatus"]!!.jsonPrimitive.content
                    if(ENTRA_STATUS == "issuance_successful")
                        CALLBACK_COMPLETE.notifyAll()
                }
            }
        }.start()
    }

    @Test
    @Ignore
    fun testEntraIssuance() = runTest {
        println("--- ENTRA ISSUANCE TEST ---")

        val pin: String? = null //"0288"

        println("============ Issuer ============")

        println("> Doing Entra authorize...")
        val accessToken = entraAuthorize()
        println("> Using access token: $accessToken")
        startEntraCallbackServer()

        val createIssuanceReq = "{\n" +
                "    \"callback\": {\n" +
                "        \"url\": \"https://httpstat.us/200\",\n" +
                //"        \"url\": \"https://0fc7-2001-871-25f-66b3-9ea8-fc44-915d-107e.ngrok-free.app/callback\",\n" +
                "        \"state\": \"1234\",\n" +
                "        \"headers\": {\n" +
                "            \"api-key\": \"1234\"\n" +
                "        }\n" +
                "    },\n" +
                "    \"authority\": \"did:web:entra.walt.id\",\n" +
                "    \"registration\": {\n" +
                "        \"clientName\": \"test\"\n" +
                "    },\n" +
                "    \"type\": \"VerifiableCredential,MyID\",\n" +
                "    \"manifest\": \"https://verifiedid.did.msidentity.com/v1.0/tenants/8bc955d9-38fd-4c15-a520-0c656407537a/verifiableCredentials/contracts/133d7e92-d227-f74d-1a5b-354cbc8df49a/manifest\",\n" +
                (pin?.let {
                    "\"pin\": {\n" +
                            "       \"value\": \"$pin\",\n" +
                            "       \"length\": 4\n" +
                            "   },"
                } ?: "") +
                "   \"claims\": { \n" +
                "       \"given_name\": \"Sev\",\n" +
                "       \"family_name\": \"Sta\"\n" +
                "   }\n" +
                "}"

        println("> Create issuance request: $createIssuanceReq")

        val createIssuanceRequestUrl = "https://verifiedid.did.msidentity.com/v1.0/verifiableCredentials/createIssuanceRequest"

        println("> Sending HTTP POST with create issuance request to: $createIssuanceRequestUrl")
        val response = http.post(createIssuanceRequestUrl) {
            header(HttpHeaders.Authorization, accessToken)
            contentType(ContentType.Application.Json)
            setBody(createIssuanceReq)
        }
        println("> Response: $response")

        assertEquals(expected = HttpStatusCode.Created, actual = response.status)

        val responseObj = response.body<JsonObject>()
        println("> Response JSON body: $responseObj")

        val url = responseObj["url"]?.jsonPrimitive?.content

        //val url = "openid-vc://?request_uri=https://verifiedid.did.msidentity.com/v1.0/tenants/37a99dab-212b-44d9-9b49-7756cb4dd915/verifiableCredentials/issuanceRequests/67e271be-be8b-42f8-9cb9-1b57ee010e41"
        println(">>>> URL from response: $url")

        return@runTest
        //val url = "openid-vc://?request_uri=https://verifiedid.did.msidentity.com/v1.0/tenants/3c32ed40-8a10-465b-8ba4-0b1e86882668/verifiableCredentials/issuanceRequests/a7e5db5b-2fba-4d02-bc0d-21ee82191386"


        println("\n============ Wallet ============")

        println("> Loading key: $TEST_WALLET_KEY")
        val testWalletKey = JWKKey.importJWK(TEST_WALLET_KEY).getOrThrow()
        assertTrue(actual = testWalletKey.hasPrivateKey)
        println("> Private key loaded!")

        println("> Parsing issuance request...")
        val reqParams = parseQueryString(Url(url!!).encodedQuery).toMap()
        println("> Request query params: $reqParams")

        val authReq = AuthorizationRequest.fromHttpParametersAuto(reqParams)
        println("> Parsed Authorization request: $authReq")

        println("* detect that this is an MS Entra issuance request and trigger custom protocol flow!")
        println("> Parsing EntraIssuanceRequest from Authorization request...")
        val entraIssuanceRequest = EntraIssuanceRequest.fromAuthorizationRequest(authReq)
        println("> Entra issuance request is: $entraIssuanceRequest")

        println("* create response JWT token, signed by key for holder DID credentialWallet.")

        println("> Response (SDPayload) token payload creating... DID = $TEST_WALLET_DID")
        val responseTokenPayload = SDPayload.createSDPayload(
            entraIssuanceRequest.getResponseObject(testWalletKey.getThumbprint(), TEST_WALLET_DID, testWalletKey.getPublicKey().jwk!!, pin),
            SDMap.fromJSON("{}")
        )
        println("> Created response token payload: $responseTokenPayload")

        println("> Creating JWT Crypto provider with key: $TEST_WALLET_KEY")
        val jwtCryptoProvider = let {
            //val key = OctetKeyPair.parse(TEST_WALLET_KEY)
            val key = ECKey.parse(TEST_WALLET_KEY)
            SimpleJWTCryptoProvider(JWSAlgorithm.ES256K, ECDSASigner(key).apply {
                jcaContext.provider = BouncyCastleProviderSingleton.getInstance()
            }, ECDSAVerifier(key.toPublicJWK()).apply {
                jcaContext.provider = BouncyCastleProviderSingleton.getInstance()
            })
        }

        val keyId = TEST_WALLET_DID + "#${testWalletKey.getKeyId()}"
        println("> Signing response token payload with JWT Crypto provider, keyId: $keyId")
        val responseToken = SDJwt.sign(responseTokenPayload, jwtCryptoProvider, keyId).toString()

        println("* POST response JWT token to return address found in manifest")
//        val issuerReturnAddress = "https://beta.did.msidentity.com/v1.0/tenants/3c32ed40-8a10-465b-8ba4-0b1e86882668/verifiableCredentials/issue"
//        val responseToken = "eyJraWQiOiJkaWQ6aW9uOkVpRGgwRUw4d2c4b0YtN3JSaVJ6RVpWZnNKdmg0c1FYNEpvY2syS3A0al96eGc6ZXlKa1pXeDBZU0k2ZXlKd1lYUmphR1Z6SWpwYmV5SmhZM1JwYjI0aU9pSnlaWEJzWVdObElpd2laRzlqZFcxbGJuUWlPbnNpY0hWaWJHbGpTMlY1Y3lJNlczc2lhV1FpT2lJME9HUTRZVE0wTWpZelkyWTBPVEpoWVRkbVpqWXhZall4T0RObE9HSmpaaUlzSW5CMVlteHBZMHRsZVVwM2F5STZleUpqY25ZaU9pSnpaV053TWpVMmF6RWlMQ0pyYVdRaU9pSTBPR1E0WVRNME1qWXpZMlkwT1RKaFlUZG1aall4WWpZeE9ETmxPR0pqWmlJc0ltdDBlU0k2SWtWRElpd2lkWE5sSWpvaWMybG5JaXdpZUNJNklsUkxZVkUyYzBOdlkxUkVjMjExYWpsMFZGSTVPVFowUmxod1JXTlRNa1ZLVGkweFowOWhaR0ZDZG1zaUxDSjVJam9pTUZSeVNWbElZMlpET1ROV2NFVjFkbW90U0ZoVWJubExkREJ6Ym1GNVQwMTNSMU5LUVRGWWFVUllPQ0o5TENKd2RYSndiM05sY3lJNld5SmhkWFJvWlc1MGFXTmhkR2x2YmlKZExDSjBlWEJsSWpvaVJXTmtjMkZUWldOd01qVTJhekZXWlhKcFptbGpZWFJwYjI1TFpYa3lNREU1SW4xZGZYMWRMQ0oxY0dSaGRHVkRiMjF0YVhSdFpXNTBJam9pUldsQ1FubGtaMlI1V0haa1ZFUm9iM1pzV1dJdFFrVjJSM0V4UW5SMlRXSlNMVVJtYkRjdFNIZFpNVWhVWnlKOUxDSnpkV1ptYVhoRVlYUmhJanA3SW1SbGJIUmhTR0Z6YUNJNklrVnBSR0p4YTA1bGR6ZFVjRFUyY0VKRVQzcDZSRWM1YlRoUFpuZHhhbWxYUmpJM2JUZzJkMWszVFMxMU0xRWlMQ0p5WldOdmRtVnllVU52YlcxcGRHMWxiblFpT2lKRmFVRkdPWGt6Y0UxbFEyUlFTbVpSWWprMVpWVjVUVmxmYVVkQ1JrTXdka1F6ZUROS1ZUQjZWMFZqV1V0QkluMTkjNDhkOGEzNDI2M2NmNDkyYWE3ZmY2MWI2MTgzZThiY2YiLCJ0eXAiOiJKV1QiLCJhbGciOiJFUzI1NksifQ.eyJzdWIiOiJKb1I1T1hicGVldlRpeVM4S0Zma2NSN3NlMGMwSVhLbGU0REk1YlVXTncwIiwiYXVkIjoiaHR0cHM6Ly9iZXRhLmRpZC5tc2lkZW50aXR5LmNvbS92MS4wL3RlbmFudHMvM2MzMmVkNDAtOGExMC00NjViLThiYTQtMGIxZTg2ODgyNjY4L3ZlcmlmaWFibGVDcmVkZW50aWFscy9pc3N1ZSIsInN1Yl9qd2siOnsia3R5IjoiRUMiLCJraWQiOiI0OGQ4YTM0MjYzY2Y0OTJhYTdmZjYxYjYxODNlOGJjZiIsInVzZSI6InNpZyIsImNydiI6InNlY3AyNTZrMSIsIngiOiJUS2FRNnNDb2NURHNtdWo5dFRSOTk2dEZYcEVjUzJFSk4tMWdPYWRhQnZrIiwieSI6IjBUcklZSGNmQzkzVnBFdXZqLUhYVG55S3Qwc25heU9Nd0dTSkExWGlEWDgifSwiaWF0IjoxNzAzMTAzNDg4LCJleHAiOjE3MDMxMDcwNzIsImp0aSI6IjkwYjkzMThlLWVkMGEtNGMzZC1hMGJiLTg2OTYwOGE3OWYzNCIsImlzcyI6Imh0dHBzOi8vc2VsZi1pc3N1ZWQubWUiLCJwaW4iOiJTQ2NVaHo1MThscWZxbVFQVkpIVVdaOU9scTVVeUZPL2dQYmNOaW93cTNNPSIsImNvbnRyYWN0IjoiaHR0cHM6Ly92ZXJpZmllZGlkLmRpZC5tc2lkZW50aXR5LmNvbS92MS4wL3RlbmFudHMvM2MzMmVkNDAtOGExMC00NjViLThiYTQtMGIxZTg2ODgyNjY4L3ZlcmlmaWFibGVDcmVkZW50aWFscy9jb250cmFjdHMvMDVkN2JhNTctZjRlNi0yNjBjLWNjYjYtMGNkNzQxNzk3NzljL21hbmlmZXN0IiwiYXR0ZXN0YXRpb25zIjp7ImlkVG9rZW5zIjp7Imh0dHBzOi8vc2VsZi1pc3N1ZWQubWUiOiJleUpoYkdjaU9pSkZVekkxTmtzaUxDSnJhV1FpT2lKa2FXUTZkMlZpT21ScFpDNTNiMjlrWjNKdmRtVmtaVzF2TG1OdmJTTTVZMlZoTVRVeU5XWmtOV1kwWkRKak9URTROMlF6TXpBMU9HRTNaVFExT1haalUybG5ibWx1WjB0bGVTMDJaR1V3T1NJc0luUjVjQ0k2SWtwWFZDSjkuZXlKemRXSWlPaUozYkVOeGRYQnhaWGt3Y2xoeGNtdHVSbE5oVW1wQk56QkNNMHRUT1dST05WQllXVjluUXkxS2FuWmpJaXdpWVhWa0lqb2lhSFIwY0hNNkx5OWlaWFJoTG1ScFpDNXRjMmxrWlc1MGFYUjVMbU52YlM5Mk1TNHdMM1JsYm1GdWRITXZNMk16TW1Wa05EQXRPR0V4TUMwME5qVmlMVGhpWVRRdE1HSXhaVGcyT0RneU5qWTRMM1psY21sbWFXRmliR1ZEY21Wa1pXNTBhV0ZzY3k5cGMzTjFaU0lzSW01dmJtTmxJam9pU2tSRk5FVktOa1Y0WWpWQ1VFNDVOMm8zTVZWSFFUMDlJaXdpYzNWaVgycDNheUk2ZXlKamNuWWlPaUp6WldOd01qVTJhekVpTENKcmFXUWlPaUprYVdRNmQyVmlPbVJwWkM1M2IyOWtaM0p2ZG1Wa1pXMXZMbU52YlNNNVkyVmhNVFV5Tldaa05XWTBaREpqT1RFNE4yUXpNekExT0dFM1pUUTFPWFpqVTJsbmJtbHVaMHRsZVMwMlpHVXdPU0lzSW10MGVTSTZJa1ZESWl3aWVDSTZJa3RNYmpWRmFuZFlaazlxZVhkaVZIbzFiRU5hVVdGbFdWbDNObmxEUkROMFlURTNkak5ZVDNOaVJVa2lMQ0o1SWpvaVNFRkdjRlJYWDJNMWJGOUhaamhQVlhKelkzbGZabE5KUjFkUGVWbGxSMDFxZVVkU1lrbzJOemhZYXlKOUxDSmthV1FpT2lKa2FXUTZkMlZpT21ScFpDNTNiMjlrWjNKdmRtVmtaVzF2TG1OdmJTSXNJbVpwY25OMFRtRnRaU0k2SWsxaGRIUm9aWGNpTENKc1lYTjBUbUZ0WlNJNklrMXBZMmhoWld3aUxDSnpZMkZ1Ym1Wa1pHOWpJam9pVGxrZ1UzUmhkR1VnUkhKcGRtVnljeUJNYVdObGJuTmxJaXdpYzJWc1ptbGxJam9pVm1WeWFXWnBaV1FnVTJWc1ptbGxJaXdpZG1WeWFXWnBZMkYwYVc5dUlqb2lSblZzYkhrZ1ZtVnlhV1pwWldRaUxDSmhaR1J5WlhOeklqb2lNak0wTlNCQmJubDNhR1Z5WlNCVGRISmxaWFFzSUZsdmRYSWdRMmwwZVN3Z1Rsa2dNVEl6TkRVaUxDSmhaMlYyWlhKcFptbGxaQ0k2SWs5c1pHVnlJSFJvWVc0Z01qRWlMQ0pwYzNNaU9pSm9kSFJ3Y3pvdkwzTmxiR1l0YVhOemRXVmtMbTFsSWl3aWFXRjBJam94TnpBek1UQXpORFV5TENKcWRHa2lPaUpqWlRSaFpqRXpZeTAwWTJVeUxUUTBNbUV0WVROaVlTMDFaR0V3WVdOa056RmpZamNpTENKbGVIQWlPakUzTURNeE1ETTNOVElzSW5CcGJpSTZleUpzWlc1bmRHZ2lPalFzSW5SNWNHVWlPaUp1ZFcxbGNtbGpJaXdpWVd4bklqb2ljMmhoTWpVMklpd2lhWFJsY21GMGFXOXVjeUk2TVN3aWMyRnNkQ0k2SWpBNU1HTTRabVl6TVdKbU1qUTRaamc0T1RSaU5UaGxZemc0TlRnM1l6Z3dJaXdpYUdGemFDSTZJblZWUW5sNVJXWnpNVzFXYlRBMlZtaGhNelIwT1U5eU5tOVJXVEoyV0RWaGJrZFFiMDVuZGxselUwVTlJbjE5Ll9vUTR1TzVnVDJ2WmZRWGdpcm5YX1BEdG9POS1nZGF0dERqQlpSeEZVZklLUGpYbXJYRjU4RmlFdGNseWxpWHhGTHZ2dnNZeDBFeDhiNWQ3YzZ0ZWFBIn19LCJkaWQiOiJkaWQ6aW9uOkVpRGgwRUw4d2c4b0YtN3JSaVJ6RVpWZnNKdmg0c1FYNEpvY2syS3A0al96eGc6ZXlKa1pXeDBZU0k2ZXlKd1lYUmphR1Z6SWpwYmV5SmhZM1JwYjI0aU9pSnlaWEJzWVdObElpd2laRzlqZFcxbGJuUWlPbnNpY0hWaWJHbGpTMlY1Y3lJNlczc2lhV1FpT2lJME9HUTRZVE0wTWpZelkyWTBPVEpoWVRkbVpqWXhZall4T0RObE9HSmpaaUlzSW5CMVlteHBZMHRsZVVwM2F5STZleUpqY25ZaU9pSnpaV053TWpVMmF6RWlMQ0pyYVdRaU9pSTBPR1E0WVRNME1qWXpZMlkwT1RKaFlUZG1aall4WWpZeE9ETmxPR0pqWmlJc0ltdDBlU0k2SWtWRElpd2lkWE5sSWpvaWMybG5JaXdpZUNJNklsUkxZVkUyYzBOdlkxUkVjMjExYWpsMFZGSTVPVFowUmxod1JXTlRNa1ZLVGkweFowOWhaR0ZDZG1zaUxDSjVJam9pTUZSeVNWbElZMlpET1ROV2NFVjFkbW90U0ZoVWJubExkREJ6Ym1GNVQwMTNSMU5LUVRGWWFVUllPQ0o5TENKd2RYSndiM05sY3lJNld5SmhkWFJvWlc1MGFXTmhkR2x2YmlKZExDSjBlWEJsSWpvaVJXTmtjMkZUWldOd01qVTJhekZXWlhKcFptbGpZWFJwYjI1TFpYa3lNREU1SW4xZGZYMWRMQ0oxY0dSaGRHVkRiMjF0YVhSdFpXNTBJam9pUldsQ1FubGtaMlI1V0haa1ZFUm9iM1pzV1dJdFFrVjJSM0V4UW5SMlRXSlNMVVJtYkRjdFNIZFpNVWhVWnlKOUxDSnpkV1ptYVhoRVlYUmhJanA3SW1SbGJIUmhTR0Z6YUNJNklrVnBSR0p4YTA1bGR6ZFVjRFUyY0VKRVQzcDZSRWM1YlRoUFpuZHhhbWxYUmpJM2JUZzJkMWszVFMxMU0xRWlMQ0p5WldOdmRtVnllVU52YlcxcGRHMWxiblFpT2lKRmFVRkdPWGt6Y0UxbFEyUlFTbVpSWWprMVpWVjVUVmxmYVVkQ1JrTXdka1F6ZUROS1ZUQjZWMFZqV1V0QkluMTkifQ.RwwcxrVxu_S5V_tWGbBBq-09o8OeQ92ueA8tGJSPjkG7YsmKq1oXOKsL3-hsq0gl30c9tb7O-P4YyZegNoomOA"

        println("> Sending HTTP POST to: ${entraIssuanceRequest.issuerReturnAddress}")
        val resp = http.post(entraIssuanceRequest.issuerReturnAddress) {
            contentType(ContentType.Text.Plain)
            setBody(responseToken)
        }
        println("> HTTP response: $resp")
        println("> Body: " + resp.bodyAsText())
        assertEquals(expected = HttpStatusCode.OK, actual = resp.status)

        println("> Parsing VC...")
        val vc = Json.parseToJsonElement(resp.bodyAsText()).jsonObject["vc"]!!.jsonPrimitive.content
        println("> VC is: $vc")

        println("> Success: " + CredentialResponse.Companion.success(CredentialFormat.jwt_vc_json, vc).credential?.toString())

        assertEquals(
            expected = HttpStatusCode.Accepted,
            actual = entraIssuanceRequest.authorizationRequest.redirectUri?.let { redirectUri ->
                http.post(redirectUri) {
                    contentType(ContentType.Application.Json)
                    setBody(EntraIssuanceCompletionResponse(EntraIssuanceCompletionCode.issuance_successful, entraIssuanceRequest.authorizationRequest.state!!))
                }.also {
                    println("ENTRA redirect URI response: ${it.status}")
                    println(it.bodyAsText())
                }
            }?.status
        )
//        synchronized(CALLBACK_COMPLETE) {
//            CALLBACK_COMPLETE.wait(1000)
//            ENTRA_STATUS shouldBe "issuance_successful"
//        }
    }

    //@Test
    suspend fun testCreateDidIon() {
        assertContains(iterable = DidService.registrarMethods.keys, element = "ion")
        val didResult = DidService.register(DidIonCreateOptions())
        println(didResult.did)
    }

    //@Test
    suspend fun testCreateKey() {
        val result = JWKKey.Companion.importJWK(File("/home/work/waltid/entra/keys/priv.jwk").readText().trimIndent())
        assertTrue(actual = result.isSuccess)
        val key = result.getOrNull()!!
        assertTrue(actual = key.hasPrivateKey)

    }

    //@Test
    suspend fun testGenerateHolderDid2() {
        val key = JWKKey.generate(KeyType.Ed25519)
        println("== Key ==")
        println(key.exportJWK())
        println("== DID ==")
        val did = DidService.register(DidWebCreateOptions("entra.walt.id", "/holder2", KeyType.Ed25519))
        println(did.did)
        println("== Did doc ==")
        println(did.didDocument.toString())
    }
}
