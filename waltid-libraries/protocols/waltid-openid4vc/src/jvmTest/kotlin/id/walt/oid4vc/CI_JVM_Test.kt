package id.walt.oid4vc

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.crypto.bc.BouncyCastleProviderSingleton
import com.nimbusds.jose.jwk.ECKey
import id.walt.policies.policies.JwtSignaturePolicy
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.dids.DidService
import id.walt.did.dids.registrar.dids.DidIonCreateOptions
import id.walt.did.dids.registrar.dids.DidJwkCreateOptions
import id.walt.did.dids.registrar.dids.DidKeyCreateOptions
import id.walt.did.dids.registrar.dids.DidWebCreateOptions
import id.walt.oid4vc.data.*
import id.walt.oid4vc.data.dif.PresentationDefinition
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
import kotlinx.datetime.Clock
import kotlinx.serialization.json.*
import org.junit.jupiter.api.BeforeAll
import java.io.File
import kotlin.test.*
import kotlin.time.Duration.Companion.minutes

class CI_JVM_Test {

    var testMetadata = OpenIDProviderMetadata(
        authorizationEndpoint = "https://localhost/oidc",
        credentialConfigurationsSupported = mapOf(
            "UniversityDegreeCredential_jwt_vc_json" to CredentialSupported(
                CredentialFormat.jwt_vc_json,
                cryptographicBindingMethodsSupported = setOf("did"),
                credentialSigningAlgValuesSupported =  setOf("ES256K"),
                display = listOf(
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
                credentialDefinition = CredentialDefinition(type = listOf("VerifiableCredential", "UniversityDegreeCredential")),
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
            "VerifiableId_ldp_vc" to CredentialSupported(
                CredentialFormat.ldp_vc,
                cryptographicBindingMethodsSupported = setOf("did"),
                credentialSigningAlgValuesSupported = setOf("ES256K"),
                display = listOf(DisplayProperties("Verifiable ID")),
                credentialDefinition = CredentialDefinition(type = listOf("VerifiableCredential", "VerifiableId")),
                context = listOf(
                    JsonPrimitive("https://www.w3.org/2018/credentials/v1"),
                    JsonObject(mapOf("@version" to JsonPrimitive(1.1)))
                )
            )
        )
    )

    val ktorClient = HttpClient {
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
                "&authorization_details=%5B%7B%22type%22%3A%22openid_credential%22%2C%22format%22%3A%22jwt_vc_json%22%2C%22credential_definition%22%3A%7B%22type%22%3A%5B%22VerifiableCredential%22%2C%22UniversityDegreeCredential%22%5D%7D%7D%5D" +
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
                    credentialDefinition = CredentialDefinition(type = listOf("VerifiableCredential", "UniversityDegreeCredential"))
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
                    credentialDefinition = CredentialDefinition(type = listOf("VerifiableCredential", "VerifiableId"))
                ), AuthorizationDetails(
                    type = OPENID_CREDENTIAL_AUTHORIZATION_TYPE,
                    format = CredentialFormat.jwt_vc_json,
                    credentialDefinition = CredentialDefinition(type = listOf("VerifiableCredential", "VerifiableAttestation", "VerifiableDiploma"))
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
        assertEquals(expected = "VerifiableId", actual = offeredCredentials.first().credentialDefinition?.type?.last())
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
                actual = it.parameters[ResponseType.Code.name.lowercase()]
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
        val nonce = tokenResponse.cNonce!!
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
        val parsedHolderDid = parsedHolderKeyId.substringBefore("#")
//        val resolvedKeyForHolderDid = runBlocking { DidService.resolveToKey(parsedHolderDid) }.getOrThrow()
        val resolvedKeyForHolderDid = DidService.resolveToKey(parsedHolderDid).getOrThrow()

        val validPoP = credReq.proof?.validateJwtProof(resolvedKeyForHolderDid, ciTestProvider.baseUrl,null, nonce, parsedHolderKeyId)
        assertTrue(actual = validPoP!!)

        val generatedCredential = ciTestProvider.generateCredential(credReq).credential
        assertNotNull(generatedCredential)
        val credentialResponse: CredentialResponse = CredentialResponse.success(credReq.format, generatedCredential)

        println("// -------- WALLET ----------")
        assertTrue(actual = credentialResponse.isSuccess)
        assertFalse(actual = credentialResponse.isDeferred)
        assertEquals(expected = CredentialFormat.jwt_vc_json, actual = credentialResponse.format!!)
        assertTrue(actual = credentialResponse.credential!!.instanceOf(JsonPrimitive::class))

        println("// parse and verify credential")
        val credential = credentialResponse.credential!!.jsonPrimitive.content
        println(">>> Issued credential: $credential")
        verifyIssuerAndSubjectId(
            SDJwt.parse(credential).fullPayload["vc"]?.jsonObject!!,
            ciTestProvider.CI_ISSUER_DID, credentialWallet.TEST_DID
        )
        assertTrue(actual = JwtSignaturePolicy().verify(credential, null, mapOf()).isSuccess)
    }

    // Test case for available authentication methods are: NONE, ID_TOKEN, VP_TOKEN, PRE_AUTHORIZED PWD(Handled by third party authorization server)
    @Test
    fun testCredentialIssuanceIsolatedFunctionsAuthCodeFlow() = runTest {
        // TODO: consider re-implementing CITestProvider, making use of new lib functions
        // is it ok to generate the credential offer using the ciTestProvider (OpenIDCredentialIssuer class) ?
        val issuedCredentialId = "VerifiableId"
        val baseUrl = ciTestProvider.baseUrl
        ciTestProvider.deferIssuance = false

        println("// -------- CREDENTIAL ISSUER ----------")
        // Init credential offer for full authorization code flow

        // Issuer Client stores the authentication method in session.
        // Available authentication methods are: NONE, ID_TOKEN, VP_TOKEN, PWD(Handled by third party authorization server), PRE_AUTHORIZED. The response for each method is a redirect to the proper location.
        println("// --Authentication method is NONE--")
        var issuerState = "test-state-none-auth"
        var issueReqUrl = testIsolatedFunctionsCreateCredentialOffer(baseUrl, issuerState, issuedCredentialId)

        // Issuer Client shows credential offer request as QR code
        println(issueReqUrl)

        println("// -------- WALLET ----------")
        var parsedCredOffer = OpenID4VCI.parseAndResolveCredentialOfferRequestUrl(issueReqUrl)
        var providerMetadata = OpenID4VCI.resolveCIProviderMetadata(parsedCredOffer)
        assertEquals(expected = parsedCredOffer.credentialIssuer, actual = providerMetadata.credentialIssuer)

        println("// resolve offered credentials")
        var offeredCredentials = OpenID4VCI.resolveOfferedCredentials(parsedCredOffer, providerMetadata)
        println("offeredCredentials: $offeredCredentials")
        assertEquals(expected = 1, actual = offeredCredentials.size)
        assertEquals(expected = CredentialFormat.jwt_vc_json, actual = offeredCredentials.first().format)
        assertEquals(expected = "VerifiableId", actual = offeredCredentials.first().credentialDefinition?.type?.last())
        var offeredCredential = offeredCredentials.first()
        println("offeredCredentials[0]: $offeredCredential")


        println("// go through authorization code flow to receive offered credential")
        println("// auth request (short-cut, without pushed authorization request)")
        var authReqWalletState = "secured_state"
        var authReqWallet = AuthorizationRequest(
            setOf(ResponseType.Code), testCIClientConfig.clientID,
            redirectUri = credentialWallet.config.redirectUri,
            issuerState = parsedCredOffer.grants[GrantType.authorization_code.value]!!.issuerState,
            state = authReqWalletState
        )
        println("authReq: $authReqWallet")

        // Wallet client calls /authorize endpoint


        println("// -------- CREDENTIAL ISSUER ----------")
        var authReq = OpenID4VCI.validateAuthorizationRequestQueryString(authReqWallet.toHttpQueryString())

        // Issuer Client retrieves issuance session based on issuer state and stores the credential request, including authReqWallet state
        // Available authentication methods are: NONE, ID_TOKEN, VP_TOKEN, PWD(Handled by third party authorization server). The response for each method is a redirect to the proper location.
        // Issuer Client checks the authentication method of the session

        println("// --Authentication method is NONE--")
        // Issuer Client generates authorization code
        var authorizationCode = "secured_code"
        var authCodeResponse: AuthorizationCodeResponse = AuthorizationCodeResponse.success(authorizationCode,  mapOf("state" to listOf(authReqWallet.state!!)))
        var redirectUri = testCredentialIssuanceIsolatedFunctionsAuthCodeFlowRedirectWithCode(authCodeResponse, authReqWallet)

        // Issuer client redirects the request to redirectUri

        println("// -------- WALLET ----------")
        println("// token req")
        var tokenReq =
            TokenRequest(
                GrantType.authorization_code,
                testCIClientConfig.clientID,
                code = authCodeResponse.code!!
            )
        println("tokenReq: $tokenReq")


        println("// -------- CREDENTIAL ISSUER ----------")
        // Validate token request against authorization code
        OpenID4VCI.validateTokenRequestRaw(tokenReq.toHttpParameters(), authorizationCode)

        // Generate Access Token
        var expirationTime = (Clock.System.now().epochSeconds + 864000L) // ten days in milliseconds

        var accessToken = OpenID4VCI.signToken(
            privateKey = ciTestProvider.CI_TOKEN_KEY,
            payload = buildJsonObject {
                put(JWTClaims.Payload.audience, ciTestProvider.baseUrl)
                put(JWTClaims.Payload.subject, authReq.clientId)
                put(JWTClaims.Payload.issuer, ciTestProvider.baseUrl)
                put(JWTClaims.Payload.expirationTime, expirationTime)
                put(JWTClaims.Payload.notBeforeTime, Clock.System.now().epochSeconds)
            }
        )

        // Issuer client creates cPoPnonce
        var cPoPNonce = "secured_cPoPnonce"
        var tokenResponse: TokenResponse = TokenResponse.success(accessToken, "bearer", cNonce = cPoPNonce, expiresIn = expirationTime)

        // Issuer client sends successful response with tokenResponse


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
        var proofOfPossession =
            ProofOfPossession.JWTProofBuilder(ciTestProvider.baseUrl, null, nonce, proofKeyId).build(holderKey)

        var credReq = CredentialRequest.forOfferedCredential(offeredCredential, proofOfPossession)
        println("credReq: $credReq")

        println("// -------- CREDENTIAL ISSUER ----------")
        // Issuer Client extracts Access Token from header
        OpenID4VCI.verifyToken(tokenResponse.accessToken.toString(),  ciTestProvider.CI_TOKEN_KEY.getPublicKey())

        //Then VC Stuff



        // ----------------------------------
        // Authentication Method is ID_TOKEN
        // ----------------------------------
        println("// --Authentication method is ID_TOKEN--")
        issuerState = "test-state-idtoken-auth"
        issueReqUrl = testIsolatedFunctionsCreateCredentialOffer(baseUrl, issuerState, issuedCredentialId)

        // Issuer Client shows credential offer request as QR code
        println(issueReqUrl)

        println("// -------- WALLET ----------")
        parsedCredOffer = OpenID4VCI.parseAndResolveCredentialOfferRequestUrl(issueReqUrl)
        providerMetadata = OpenID4VCI.resolveCIProviderMetadata(parsedCredOffer)
        assertEquals(expected = parsedCredOffer.credentialIssuer, actual = providerMetadata.credentialIssuer)

        println("// resolve offered credentials")
        offeredCredentials = OpenID4VCI.resolveOfferedCredentials(parsedCredOffer, providerMetadata)
        println("offeredCredentials: $offeredCredentials")
        assertEquals(expected = 1, actual = offeredCredentials.size)
        assertEquals(expected = CredentialFormat.jwt_vc_json, actual = offeredCredentials.first().format)
        assertEquals(expected = "VerifiableId", actual = offeredCredentials.first().credentialDefinition?.type?.last())
        offeredCredential = offeredCredentials.first()
        println("offeredCredentials[0]: $offeredCredential")


        authReqWalletState = "secured_state_idtoken"
        authReqWallet = AuthorizationRequest(
            setOf(ResponseType.Code), testCIClientConfig.clientID,
            redirectUri = credentialWallet.config.redirectUri,
            issuerState = parsedCredOffer.grants[GrantType.authorization_code.value]!!.issuerState,
            state = authReqWalletState
        )
        println("authReq: $authReqWallet")

        // Wallet client calls /authorize endpoint


        println("// -------- CREDENTIAL ISSUER ----------")
        authReq = OpenID4VCI.validateAuthorizationRequestQueryString(authReqWallet.toHttpQueryString())

        // Issuer Client retrieves issuance session based on issuer state and stores the credential request, including authReqWallet state
        // Available authentication methods are: NONE, ID_TOKEN, VP_TOKEN, PWD(Handled by third party authorization server). The response for each method is a redirect to the proper location.
        // Issuer Client checks the authentication method of the session

        // Issuer Client generates authorization code
        // Issuer client creates state and nonce for the id token authorization request
        var authReqIssuerState = "secured_state_issuer_idtoken"
        var authReqIssuerNonce = "secured_nonce_issue_idtoken"

        var authReqIssuer = OpenID4VCI.generateAuthorizationRequest(authReq, ciTestProvider.baseUrl, ciTestProvider.CI_TOKEN_KEY, ResponseType.IdToken, authReqIssuerState, authReqIssuerNonce)

        // Redirect uri is located in the client_metadata.authorization_endpoint or "openid://"
        var redirectUriReq = authReqIssuer.toRedirectUri(authReq.clientMetadata?.customParameters?.get("authorization_endpoint")?.jsonPrimitive?.content ?: "openid://", authReq.responseMode ?: ResponseMode.query)
        Url(redirectUriReq).let {
            assertContains(iterable = it.parameters.names(), element = "request")
            assertContains(iterable = it.parameters.names(), element = "redirect_uri")
            assertEquals(expected = ciTestProvider.baseUrl + "/direct_post", actual = it.parameters["redirect_uri"])
        }

        // Issuer Client redirects the request to redirectUri


        println("// -------- WALLET ----------")
        // wallet creates id token
        val idToken = "eyJ0eXAiOiJKV1QiLCJhbGciOiJFUzI1NiIsImtpZCI6ImRpZDprZXk6ejJkbXpEODFjZ1B4OFZraTdKYnV1TW1GWXJXUGdZb3l0eWtVWjNleXFodDFqOUtib2o3ZzlQZlhKeGJiczRLWWVneXI3RUxuRlZucERNemJKSkRETlpqYXZYNmp2dERtQUxNYlhBR1c2N3BkVGdGZWEyRnJHR1NGczhFanhpOTZvRkxHSGNMNFA2YmpMRFBCSkV2UlJIU3JHNExzUG5lNTJmY3p0Mk1XakhMTEpCdmhBQyN6MmRtekQ4MWNnUHg4VmtpN0pidXVNbUZZcldQZ1lveXR5a1VaM2V5cWh0MWo5S2JvajdnOVBmWEp4YmJzNEtZZWd5cjdFTG5GVm5wRE16YkpKREROWmphdlg2anZ0RG1BTE1iWEFHVzY3cGRUZ0ZlYTJGckdHU0ZzOEVqeGk5Nm9GTEdIY0w0UDZiakxEUEJKRXZSUkhTckc0THNQbmU1MmZjenQyTVdqSExMSkJ2aEFDIn0.eyJub25jZSI6ImE4YWE1NDYwLTRmN2UtNDRmNy05ZGE3LWU1NmQ0YjIxMWE1MSIsInN1YiI6ImRpZDprZXk6ejJkbXpEODFjZ1B4OFZraTdKYnV1TW1GWXJXUGdZb3l0eWtVWjNleXFodDFqOUtib2o3ZzlQZlhKeGJiczRLWWVneXI3RUxuRlZucERNemJKSkRETlpqYXZYNmp2dERtQUxNYlhBR1c2N3BkVGdGZWEyRnJHR1NGczhFanhpOTZvRkxHSGNMNFA2YmpMRFBCSkV2UlJIU3JHNExzUG5lNTJmY3p0Mk1XakhMTEpCdmhBQyIsImlzcyI6ImRpZDprZXk6ejJkbXpEODFjZ1B4OFZraTdKYnV1TW1GWXJXUGdZb3l0eWtVWjNleXFodDFqOUtib2o3ZzlQZlhKeGJiczRLWWVneXI3RUxuRlZucERNemJKSkRETlpqYXZYNmp2dERtQUxNYlhBR1c2N3BkVGdGZWEyRnJHR1NGczhFanhpOTZvRkxHSGNMNFA2YmpMRFBCSkV2UlJIU3JHNExzUG5lNTJmY3p0Mk1XakhMTEpCdmhBQyIsImF1ZCI6Imh0dHBzOi8vMDFiYi01LTIwMy0xNzQtNjcubmdyb2stZnJlZS5hcHAiLCJpYXQiOjE3MjExNDQ3MzYsImV4cCI6MTcyMTE0NTAzNn0.VPWyLkMQAlcc40WCNSRH-Vxaj4LHi-wf2P9kcEKDvcdyVec2xJIwkg0JF4INMbLCkF0Y89lT0oswALd345wdUg"
        // wallet calls POST /direct_post (e.g. redirect_uri of Issuer Auth Req) providing the id_token


        println("// -------- CREDENTIAL ISSUER ----------")
        // Create validateIdTokenResponse()
        val idTokenPayload = OpenID4VCI.validateAuthorizationRequestToken(idToken)

        // Issuer Client validates states and nonces based on idTokenPayload

        // Issuer client generates authorization code
        authorizationCode = "secured_code_idtoken"
        authCodeResponse = AuthorizationCodeResponse.success(authorizationCode,  mapOf("state" to listOf(authReqWallet.state!!)))

        redirectUri = authCodeResponse.toRedirectUri(authReq.redirectUri ?: TODO(), authReq.responseMode ?: ResponseMode.query)
        Url(redirectUri).let {
            assertContains(iterable = it.parameters.names(), element = ResponseType.Code.name.lowercase())
            assertEquals(
                expected = authCodeResponse.code,
                actual = it.parameters[ResponseType.Code.name.lowercase()]
            )
        }


        println("// -------- WALLET ----------")
        println("// token req")
        tokenReq =
            TokenRequest(
                GrantType.authorization_code,
                testCIClientConfig.clientID,
                code = authCodeResponse.code!!
            )
        println("tokenReq: $tokenReq")


        println("// -------- CREDENTIAL ISSUER ----------")
        // Validate token request against authorization code
        OpenID4VCI.validateTokenRequestRaw(tokenReq.toHttpParameters(), authorizationCode)

        // Generate Access Token
        expirationTime = (Clock.System.now().epochSeconds + 864000L) // ten days in milliseconds

        accessToken = OpenID4VCI.signToken(
            privateKey = ciTestProvider.CI_TOKEN_KEY,
            payload = buildJsonObject {
                put(JWTClaims.Payload.audience, ciTestProvider.baseUrl)
                put(JWTClaims.Payload.subject, authReq.clientId)
                put(JWTClaims.Payload.issuer, ciTestProvider.baseUrl)
                put(JWTClaims.Payload.expirationTime, expirationTime)
                put(JWTClaims.Payload.notBeforeTime, Clock.System.now().epochSeconds)
            }
        )

        // Issuer client creates cPoPnonce
        cPoPNonce = "secured_cPoPnonce_idtoken"
        tokenResponse = TokenResponse.success(accessToken, "bearer", cNonce = cPoPNonce, expiresIn = expirationTime)

        // Issuer client sends successful response with tokenResponse

        println("// -------- WALLET ----------")
        assertTrue(actual = tokenResponse.isSuccess)
        assertNotNull(actual = tokenResponse.accessToken)
        assertNotNull(actual = tokenResponse.cNonce)

        println("// receive credential")
        nonce = tokenResponse.cNonce!!
        proofOfPossession = ProofOfPossession.JWTProofBuilder(ciTestProvider.baseUrl, null, nonce, proofKeyId).build(holderKey)

        credReq = CredentialRequest.forOfferedCredential(offeredCredential, proofOfPossession)
        println("credReq: $credReq")

        println("// -------- CREDENTIAL ISSUER ----------")
        // Issuer Client extracts Access Token from header
        OpenID4VCI.verifyToken(tokenResponse.accessToken.toString(),  ciTestProvider.CI_TOKEN_KEY.getPublicKey())

        //Then VC Stuff



        // ----------------------------------
        // Authentication Method is VP_TOKEN
        // ----------------------------------
        println("// --Authentication method is VP_TOKEN--")
        issuerState = "test-state-vptoken-auth"
        issueReqUrl = testIsolatedFunctionsCreateCredentialOffer(baseUrl, issuerState, issuedCredentialId)

        // Issuer Client shows credential offer request as QR code
        println(issueReqUrl)

        println("// -------- WALLET ----------")
        parsedCredOffer = OpenID4VCI.parseAndResolveCredentialOfferRequestUrl(issueReqUrl)
        providerMetadata = OpenID4VCI.resolveCIProviderMetadata(parsedCredOffer)
        assertEquals(expected = parsedCredOffer.credentialIssuer, actual = providerMetadata.credentialIssuer)

        println("// resolve offered credentials")
        offeredCredentials = OpenID4VCI.resolveOfferedCredentials(parsedCredOffer, providerMetadata)
        println("offeredCredentials: $offeredCredentials")
        assertEquals(expected = 1, actual = offeredCredentials.size)
        assertEquals(expected = CredentialFormat.jwt_vc_json, actual = offeredCredentials.first().format)
        assertEquals(expected = "VerifiableId", actual = offeredCredentials.first().credentialDefinition?.type?.last())
        offeredCredential = offeredCredentials.first()
        println("offeredCredentials[0]: $offeredCredential")


        authReqWalletState = "secured_state_vptoken"
        authReqWallet = AuthorizationRequest(
            setOf(ResponseType.Code), testCIClientConfig.clientID,
            redirectUri = credentialWallet.config.redirectUri,
            issuerState = parsedCredOffer.grants[GrantType.authorization_code.value]!!.issuerState,
            state = authReqWalletState
        )
        println("authReq: $authReqWallet")

        // Wallet client calls /authorize endpoint


        println("// -------- CREDENTIAL ISSUER ----------")
        authReq = OpenID4VCI.validateAuthorizationRequestQueryString(authReqWallet.toHttpQueryString())

        // Issuer Client retrieves issuance session based on issuer state and stores the credential request, including authReqWallet state
        // Available authentication methods are: NONE, ID_TOKEN, VP_TOKEN, PWD(Handled by third party authorization server). The response for each method is a redirect to the proper location.
        val requestedCredentialId = "OpenBadgeCredential"
        val vpProfile = OpenId4VPProfile.EBSIV3
        val requestCredentialsArr = buildJsonArray { add(requestedCredentialId) }
        val requestedTypes = requestCredentialsArr.map {
            when (it) {
                is JsonPrimitive -> it.contentOrNull
                is JsonObject -> it["credential"]?.jsonPrimitive?.contentOrNull
                else -> throw IllegalArgumentException("Invalid JSON type for requested credential: $it")
            } ?: throw IllegalArgumentException("Invalid VC type for requested credential: $it")
        }

        val presentationDefinition = PresentationDefinition.defaultGenerationFromVcTypesForCredentialFormat(requestedTypes, CredentialFormat.jwt_vc)

        // Issuer Client creates state and nonce for the vp_token authorization request
        authReqIssuerState = "secured_state_issuer_vptoken"
        authReqIssuerNonce = "secured_nonce_issuer_vptoken"

        authReqIssuer = OpenID4VCI.generateAuthorizationRequest(authReq, ciTestProvider.baseUrl, ciTestProvider.CI_TOKEN_KEY, ResponseType.VpToken, authReqIssuerState, authReqIssuerNonce, true, presentationDefinition)

        // Redirect uri is located in the client_metadata.authorization_endpoint or "openid://"
        redirectUriReq = authReqIssuer.toRedirectUri(authReq.clientMetadata?.customParameters?.get("authorization_endpoint")?.jsonPrimitive?.content ?: "openid://", authReq.responseMode ?: ResponseMode.query)
        Url(redirectUriReq).let {
            assertContains(iterable = it.parameters.names(), element = "request")
            assertContains(iterable = it.parameters.names(), element = "redirect_uri")
            assertContains(iterable = it.parameters.names(), element = "presentation_definition")
            assertEquals(expected = ciTestProvider.baseUrl + "/direct_post", actual = it.parameters["redirect_uri"])
        }
        // Issuer Client redirects the request to redirectUri


        println("// -------- WALLET ----------")
        // wallet creates vp token
        val vpToken = "eyJhbGciOiJFZERTQSIsInR5cCI6IkpXVCIsImtpZCI6ImRpZDprZXk6ejZNa3A3QVZ3dld4bnNORHVTU2JmMTlzZ0t6cngyMjNXWTk1QXFaeUFHaWZGVnlWI3o2TWtwN0FWd3ZXeG5zTkR1U1NiZjE5c2dLenJ4MjIzV1k5NUFxWnlBR2lmRlZ5ViJ9.eyJzdWIiOiJkaWQ6a2V5Ono2TWtwN0FWd3ZXeG5zTkR1U1NiZjE5c2dLenJ4MjIzV1k5NUFxWnlBR2lmRlZ5ViIsIm5iZiI6MTcyMDc2NDAxOSwiaWF0IjoxNzIwNzY0MDc5LCJqdGkiOiJ1cm46dXVpZDpiNzE2YThlOC0xNzVlLTRhMTYtODZlMC0xYzU2Zjc4NTFhZDEiLCJpc3MiOiJkaWQ6a2V5Ono2TWtwN0FWd3ZXeG5zTkR1U1NiZjE5c2dLenJ4MjIzV1k5NUFxWnlBR2lmRlZ5ViIsIm5vbmNlIjoiNDY0YTAwMTUtNzQ1OS00Y2Y4LWJmNjgtNDg0ODQyYTE5Y2FmIiwiYXVkIjoiZGlkOmtleTp6Nk1rcDdBVnd2V3huc05EdVNTYmYxOXNnS3pyeDIyM1dZOTVBcVp5QUdpZkZWeVYiLCJ2cCI6eyJAY29udGV4dCI6WyJodHRwczovL3d3dy53My5vcmcvMjAxOC9jcmVkZW50aWFscy92MSJdLCJ0eXBlIjpbIlZlcmlmaWFibGVQcmVzZW50YXRpb24iXSwiaWQiOiJ1cm46dXVpZDpiNzE2YThlOC0xNzVlLTRhMTYtODZlMC0xYzU2Zjc4NTFhZDEiLCJob2xkZXIiOiJkaWQ6a2V5Ono2TWtwN0FWd3ZXeG5zTkR1U1NiZjE5c2dLenJ4MjIzV1k5NUFxWnlBR2lmRlZ5ViIsInZlcmlmaWFibGVDcmVkZW50aWFsIjpbImV5SmhiR2NpT2lKRlpFUlRRU0lzSW5SNWNDSTZJa3BYVkNJc0ltdHBaQ0k2SW1ScFpEcHJaWGs2ZWpaTmEzQTNRVlozZGxkNGJuTk9SSFZUVTJKbU1UbHpaMHQ2Y25neU1qTlhXVGsxUVhGYWVVRkhhV1pHVm5sV0luMC5leUpwYzNNaU9pSmthV1E2YTJWNU9ubzJUV3R3TjBGV2QzWlhlRzV6VGtSMVUxTmlaakU1YzJkTGVuSjRNakl6VjFrNU5VRnhXbmxCUjJsbVJsWjVWaUlzSW5OMVlpSTZJbVJwWkRwclpYazZlalpOYTJwdE1tZGhSM052WkVkamFHWkhOR3M0VURaTGQwTklXbk5XUlZCYWFHODFWblZGWWxrNU5IRnBRa0k1SWl3aWRtTWlPbnNpUUdOdmJuUmxlSFFpT2xzaWFIUjBjSE02THk5M2QzY3Vkek11YjNKbkx6SXdNVGd2WTNKbFpHVnVkR2xoYkhNdmRqRWlMQ0pvZEhSd2N6b3ZMM0IxY213dWFXMXpaMnh2WW1Gc0xtOXlaeTl6Y0dWakwyOWlMM1l6Y0RBdlkyOXVkR1Y0ZEM1cWMyOXVJbDBzSW1sa0lqb2lkWEp1T25WMWFXUTZNVEl6SWl3aWRIbHdaU0k2V3lKV1pYSnBabWxoWW14bFEzSmxaR1Z1ZEdsaGJDSXNJazl3Wlc1Q1lXUm5aVU55WldSbGJuUnBZV3dpWFN3aWJtRnRaU0k2SWtwR1JpQjRJSFpqTFdWa2RTQlFiSFZuUm1WemRDQXpJRWx1ZEdWeWIzQmxjbUZpYVd4cGRIa2lMQ0pwYzNOMVpYSWlPbnNpZEhsd1pTSTZXeUpRY205bWFXeGxJbDBzSW1sa0lqb2laR2xrT21WNFlXMXdiR1U2TVRJeklpd2libUZ0WlNJNklrcHZZbk1nWm05eUlIUm9aU0JHZFhSMWNtVWdLRXBHUmlraUxDSjFjbXdpT2lKb2RIUndjem92TDNkM2R5NXFabVl1YjNKbkx5SXNJbWx0WVdkbElqcDdJbWxrSWpvaWFIUjBjSE02THk5M00yTXRZMk5uTG1kcGRHaDFZaTVwYnk5Mll5MWxaQzl3YkhWblptVnpkQzB4TFRJd01qSXZhVzFoWjJWekwwcEdSbDlNYjJkdlRHOWphM1Z3TG5CdVp5SXNJblI1Y0dVaU9pSkpiV0ZuWlNKOWZTd2lhWE56ZFdGdVkyVkVZWFJsSWpvaU1qQXlNeTB3TnkweU1GUXdOem93TlRvME5Gb2lMQ0psZUhCcGNtRjBhVzl1UkdGMFpTSTZJakl3TXpNdE1EY3RNakJVTURjNk1EVTZORFJhSWl3aVkzSmxaR1Z1ZEdsaGJGTjFZbXBsWTNRaU9uc2lhV1FpT2lKa2FXUTZaWGhoYlhCc1pUb3hNak1pTENKMGVYQmxJanBiSWtGamFHbGxkbVZ0Wlc1MFUzVmlhbVZqZENKZExDSmhZMmhwWlhabGJXVnVkQ0k2ZXlKcFpDSTZJblZ5YmpwMWRXbGtPbUZqTWpVMFltUTFMVGhtWVdRdE5HSmlNUzA1WkRJNUxXVm1aRGt6T0RVek5qa3lOaUlzSW5SNWNHVWlPbHNpUVdOb2FXVjJaVzFsYm5RaVhTd2libUZ0WlNJNklrcEdSaUI0SUhaakxXVmtkU0JRYkhWblJtVnpkQ0F6SUVsdWRHVnliM0JsY21GaWFXeHBkSGtpTENKa1pYTmpjbWx3ZEdsdmJpSTZJbFJvYVhNZ2QyRnNiR1YwSUhOMWNIQnZjblJ6SUhSb1pTQjFjMlVnYjJZZ1Z6TkRJRlpsY21sbWFXRmliR1VnUTNKbFpHVnVkR2xoYkhNZ1lXNWtJR2hoY3lCa1pXMXZibk4wY21GMFpXUWdhVzUwWlhKdmNHVnlZV0pwYkdsMGVTQmtkWEpwYm1jZ2RHaGxJSEJ5WlhObGJuUmhkR2x2YmlCeVpYRjFaWE4wSUhkdmNtdG1iRzkzSUdSMWNtbHVaeUJLUmtZZ2VDQldReTFGUkZVZ1VHeDFaMFpsYzNRZ015NGlMQ0pqY21sMFpYSnBZU0k2ZXlKMGVYQmxJam9pUTNKcGRHVnlhV0VpTENKdVlYSnlZWFJwZG1VaU9pSlhZV3hzWlhRZ2MyOXNkWFJwYjI1eklIQnliM1pwWkdWeWN5QmxZWEp1WldRZ2RHaHBjeUJpWVdSblpTQmllU0JrWlcxdmJuTjBjbUYwYVc1bklHbHVkR1Z5YjNCbGNtRmlhV3hwZEhrZ1pIVnlhVzVuSUhSb1pTQndjbVZ6Wlc1MFlYUnBiMjRnY21WeGRXVnpkQ0IzYjNKclpteHZkeTRnVkdocGN5QnBibU5zZFdSbGN5QnpkV05qWlhOelpuVnNiSGtnY21WalpXbDJhVzVuSUdFZ2NISmxjMlZ1ZEdGMGFXOXVJSEpsY1hWbGMzUXNJR0ZzYkc5M2FXNW5JSFJvWlNCb2IyeGtaWElnZEc4Z2MyVnNaV04wSUdGMElHeGxZWE4wSUhSM2J5QjBlWEJsY3lCdlppQjJaWEpwWm1saFlteGxJR055WldSbGJuUnBZV3h6SUhSdklHTnlaV0YwWlNCaElIWmxjbWxtYVdGaWJHVWdjSEpsYzJWdWRHRjBhVzl1TENCeVpYUjFjbTVwYm1jZ2RHaGxJSEJ5WlhObGJuUmhkR2x2YmlCMGJ5QjBhR1VnY21WeGRXVnpkRzl5TENCaGJtUWdjR0Z6YzJsdVp5QjJaWEpwWm1sallYUnBiMjRnYjJZZ2RHaGxJSEJ5WlhObGJuUmhkR2x2YmlCaGJtUWdkR2hsSUdsdVkyeDFaR1ZrSUdOeVpXUmxiblJwWVd4ekxpSjlMQ0pwYldGblpTSTZleUpwWkNJNkltaDBkSEJ6T2k4dmR6TmpMV05qWnk1bmFYUm9kV0l1YVc4dmRtTXRaV1F2Y0d4MVoyWmxjM1F0TXkweU1ESXpMMmx0WVdkbGN5OUtSa1l0VmtNdFJVUlZMVkJNVlVkR1JWTlVNeTFpWVdSblpTMXBiV0ZuWlM1d2JtY2lMQ0owZVhCbElqb2lTVzFoWjJVaWZYMTlMQ0pqY21Wa1pXNTBhV0ZzVTJOb1pXMWhJanA3SW1sa0lqb2lhSFIwY0hNNkx5OXdkWEpzTG1sdGMyZHNiMkpoYkM1dmNtY3ZjM0JsWXk5dllpOTJNM0F3TDNOamFHVnRZUzlxYzI5dUwyOWlYM1l6Y0RCZllXTm9hV1YyWlcxbGJuUmpjbVZrWlc1MGFXRnNYM05qYUdWdFlTNXFjMjl1SWl3aWRIbHdaU0k2SWtaMWJHeEtjMjl1VTJOb1pXMWhWbUZzYVdSaGRHOXlNakF5TVNKOWZTd2lhblJwSWpvaWRYSnVPblYxYVdRNk1USXpJaXdpWlhod0lqb3lNREExTkRVMU9UUTBMQ0pwWVhRaU9qRTJPRGs0TXpZM05EUXNJbTVpWmlJNk1UWTRPVGd6TmpjME5IMC5PRHZUQXVMN2JrME1pX3hNLVFualg4azByZ3VUeWtiYzJ6bFdFMVU2SGlmVXFjWTdFVU5GcUdUZWFUWHRESkxrODBuZWN6YkNNTGh1YlZseEFkdl9DdyJdfX0.zTXluOVIP0sQzc5GzNvtVvWRiaC-x9qMZg0d-EvCuRIg7QSgY0hmrfVlAzh2IDEvaXZ1ahM3hSVDx_YI74ToAw"
        // wallet calls POST /direct_post (e.g. redirect_uri of Issuer Auth Req) providing the vp_token and presentation submission

        println("// -------- CREDENTIAL ISSUER ----------")
        val vpTokenPayload = OpenID4VCI.validateAuthorizationRequestToken(vpToken)

        // Issuer Client validates states and nonces based on vpTokenPayload

        // Issuer client generates authorization code
        authorizationCode = "secured_code_vptoken"
        authCodeResponse = AuthorizationCodeResponse.success(authorizationCode,  mapOf("state" to listOf(authReqWallet.state!!)))

        redirectUri = authCodeResponse.toRedirectUri(authReq.redirectUri ?: TODO(), authReq.responseMode ?: ResponseMode.query)
        Url(redirectUri).let {
            assertContains(iterable = it.parameters.names(), element = ResponseType.Code.name.lowercase())
            assertEquals(
                expected = authCodeResponse.code,
                actual = it.parameters[ResponseType.Code.name.lowercase()]
            )
        }


        println("// -------- WALLET ----------")
        println("// token req")
        tokenReq =
            TokenRequest(
                GrantType.authorization_code,
                testCIClientConfig.clientID,
                code = authCodeResponse.code!!
            )
        println("tokenReq: $tokenReq")


        println("// -------- CREDENTIAL ISSUER ----------")
        // Validate token request against authorization code
        OpenID4VCI.validateTokenRequestRaw(tokenReq.toHttpParameters(), authorizationCode)

        // Generate Access Token
        expirationTime = (Clock.System.now().epochSeconds + 864000L) // ten days in milliseconds

        accessToken = OpenID4VCI.signToken(
            privateKey = ciTestProvider.CI_TOKEN_KEY,
            payload = buildJsonObject {
                put(JWTClaims.Payload.audience, ciTestProvider.baseUrl)
                put(JWTClaims.Payload.subject, authReq.clientId)
                put(JWTClaims.Payload.issuer, ciTestProvider.baseUrl)
                put(JWTClaims.Payload.expirationTime, expirationTime)
                put(JWTClaims.Payload.notBeforeTime, Clock.System.now().epochSeconds)
            }
        )

        // Issuer client creates cPoPnonce
        cPoPNonce = "secured_cPoPnonce_idtoken"
        tokenResponse = TokenResponse.success(accessToken, "bearer", cNonce = cPoPNonce, expiresIn = expirationTime)

        // Issuer client sends successful response with tokenResponse

        println("// -------- WALLET ----------")
        assertTrue(actual = tokenResponse.isSuccess)
        assertNotNull(actual = tokenResponse.accessToken)
        assertNotNull(actual = tokenResponse.cNonce)

        println("// receive credential")
        nonce = tokenResponse.cNonce!!
        proofOfPossession = ProofOfPossession.JWTProofBuilder(ciTestProvider.baseUrl, null, nonce, proofKeyId).build(holderKey)

        credReq = CredentialRequest.forOfferedCredential(offeredCredential, proofOfPossession)
        println("credReq: $credReq")

        println("// -------- CREDENTIAL ISSUER ----------")
        // Issuer Client extracts Access Token from header
        OpenID4VCI.verifyToken(tokenResponse.accessToken.toString(),  ciTestProvider.CI_TOKEN_KEY.getPublicKey())

        //Then VC Stuff


        // ----------------------------------
        // Authentication Method is PRE_AUTHORIZED
        // ----------------------------------
        println("// --Authentication method is PRE_AUTHORIZED--")
        val preAuthCode = "test-state-pre_auth"
        val credOffer = CredentialOffer.Builder(baseUrl)
            .addOfferedCredential(issuedCredentialId)
            .addPreAuthorizedCodeGrant(preAuthCode)
            .build()

        issueReqUrl = OpenID4VCI.getCredentialOfferRequestUrl(credOffer)
        // Issuer Client shows credential offer request as QR code
        println(issueReqUrl)

        println("// -------- WALLET ----------")
        parsedCredOffer = OpenID4VCI.parseAndResolveCredentialOfferRequestUrl(issueReqUrl)
        providerMetadata = OpenID4VCI.resolveCIProviderMetadata(parsedCredOffer)
        assertEquals(expected = parsedCredOffer.credentialIssuer, actual = providerMetadata.credentialIssuer)

        println("// resolve offered credentials")
        offeredCredentials = OpenID4VCI.resolveOfferedCredentials(parsedCredOffer, providerMetadata)
        println("offeredCredentials: $offeredCredentials")
        assertEquals(expected = 1, actual = offeredCredentials.size)
        assertEquals(expected = CredentialFormat.jwt_vc_json, actual = offeredCredentials.first().format)
        assertEquals(expected = "VerifiableId", actual = offeredCredentials.first().credentialDefinition?.type?.last())
        offeredCredential = offeredCredentials.first()
        println("offeredCredentials[0]: $offeredCredential")
        assertNotNull(actual = parsedCredOffer.grants[GrantType.pre_authorized_code.value]?.preAuthorizedCode)


        println("// token req")
        tokenReq = TokenRequest(
            grantType = GrantType.pre_authorized_code,
            //clientId = testCIClientConfig.clientID,
            redirectUri = credentialWallet.config.redirectUri,
            preAuthorizedCode = parsedCredOffer.grants[GrantType.pre_authorized_code.value]!!.preAuthorizedCode,
            txCode = null
        )


        println("// -------- CREDENTIAL ISSUER ----------")
        // Validate token request against authorization code
        OpenID4VCI.validateTokenRequestRaw(tokenReq.toHttpParameters(), preAuthCode)

        // Generate Access Token
        expirationTime = (Clock.System.now().epochSeconds + 864000L) // ten days in milliseconds

        accessToken = OpenID4VCI.signToken(
            privateKey = ciTestProvider.CI_TOKEN_KEY,
            payload = buildJsonObject {
                put(JWTClaims.Payload.audience, ciTestProvider.baseUrl)
                put(JWTClaims.Payload.subject, authReq.clientId)
                put(JWTClaims.Payload.issuer, ciTestProvider.baseUrl)
                put(JWTClaims.Payload.expirationTime, expirationTime)
                put(JWTClaims.Payload.notBeforeTime, Clock.System.now().epochSeconds)
            }
        )

        // Issuer client creates cPoPnonce
        cPoPNonce = "secured_cPoPnonce_preauthorized"
        tokenResponse = TokenResponse.success(accessToken, "bearer", cNonce = cPoPNonce, expiresIn = expirationTime)

        // Issuer client sends successful response with tokenResponse


        println("// -------- WALLET ----------")
        assertTrue(actual = tokenResponse.isSuccess)
        assertNotNull(actual = tokenResponse.accessToken)
        assertNotNull(actual = tokenResponse.cNonce)

        println("// receive credential")
        nonce = tokenResponse.cNonce!!
        proofOfPossession = ProofOfPossession.JWTProofBuilder(ciTestProvider.baseUrl, null, nonce, proofKeyId).build(holderKey)

        credReq = CredentialRequest.forOfferedCredential(offeredCredential, proofOfPossession)
        println("credReq: $credReq")

        println("// -------- CREDENTIAL ISSUER ----------")
        // Issuer Client extracts Access Token from header
        OpenID4VCI.verifyToken(tokenResponse.accessToken.toString(),  ciTestProvider.CI_TOKEN_KEY.getPublicKey())

        //Then VC Stuff


    }

//
//    @Test
//    fun testCredentialIssuanceIsolatedFunctionsAuthCodeFlowWithNoneAuth() = runTest {
//        // TODO: consider re-implementing CITestProvider, making use of new lib functions
//        // is it ok to generate the credential offer using the ciTestProvider (OpenIDCredentialIssuer class) ?
//
//        println("// -------- CREDENTIAL ISSUER ----------")
//        // init credential offer for full authorization code flow
//        // Issuer Client stores the authentication method in session, NONE in this case (PRE_AUTHORIZED, PWD, ID_TOKEN, VP_TOKEN, NONE)
//        val credOffer = CredentialOffer.Builder(ciTestProvider.baseUrl)
//            .addOfferedCredential("VerifiableId")
//            .addAuthorizationCodeGrant("test-state-none-auth")
//            .build()
//        val issueReqUrl = OpenID4VCI.getCredentialOfferRequestUrl(credOffer)
//        ciTestProvider.deferIssuance = false
//
//        // Show credential offer request as QR code
//        println(issueReqUrl)
//
//
//        println("// -------- WALLET ----------")
////        val parsedCredOffer = runBlocking { OpenID4VCI.parseAndResolveCredentialOfferRequestUrl(issueReqUrl) }
//        val parsedCredOffer = OpenID4VCI.parseAndResolveCredentialOfferRequestUrl(issueReqUrl)
//        assertEquals(expected = credOffer.toJSONString(), actual = parsedCredOffer.toJSONString())
//
////        val providerMetadata = runBlocking { OpenID4VCI.resolveCIProviderMetadata(parsedCredOffer) }
//        val providerMetadata = OpenID4VCI.resolveCIProviderMetadata(parsedCredOffer)
//        assertEquals(expected = parsedCredOffer.credentialIssuer, actual = providerMetadata.credentialIssuer)
//
//        println("// resolve offered credentials")
//        val offeredCredentials = OpenID4VCI.resolveOfferedCredentials(parsedCredOffer, providerMetadata)
//        println("offeredCredentials: $offeredCredentials")
//        assertEquals(expected = 1, actual = offeredCredentials.size)
//        assertEquals(expected = CredentialFormat.jwt_vc_json, actual = offeredCredentials.first().format)
//        assertEquals(expected = "VerifiableId", actual = offeredCredentials.first().types?.last())
//        val offeredCredential = offeredCredentials.first()
//        println("offeredCredentials[0]: $offeredCredential")
//
//        println("// go through authorization code flow to receive offered credential")
//        println("// auth request (short-cut, without pushed authorization request)")
//        val authReqWalletState = "secured_state"
//        val authReqWallet = AuthorizationRequest(
//            setOf(ResponseType.Code), testCIClientConfig.clientID,
//            redirectUri = credentialWallet.config.redirectUri,
//            issuerState = parsedCredOffer.grants[GrantType.authorization_code.value]!!.issuerState,
//            state = authReqWalletState
//        )
//        println("authReq: $authReqWallet")
//
//        // Wallet client calls /authorize endpoint
//
//
//        println("// -------- CREDENTIAL ISSUER ----------")
//        val authReq = validateAuthorizationRequestQueryString(authReqWallet.toHttpQueryString())
//
//        // Issuer client retrieves issuance session based on issuer state and stores the credential request, including authReqWallet state
//        // Available authentication methods are: NONE, ID_TOKEN, VP_TOKEN, PWD(Handled by third party authorization server). The response for each method is a redirect to the proper location.
//
//        // Issuer client checks the authentication method of the session
//        // A) authentication method is NONE
//
//        // Issuer client generates authorization code
//
//        val authorizationCode = "secured_code"
//        val authCodeResponse: AuthorizationCodeResponse = AuthorizationCodeResponse.success(authorizationCode,  mapOf("state" to listOf(authReqWallet.state!!)))
//
//        val redirectUri = authCodeResponse.toRedirectUri(authReq.redirectUri ?: TODO(), authReq.responseMode ?: ResponseMode.query)
//        Url(redirectUri).let {
//            assertContains(iterable = it.parameters.names(), element = ResponseType.Code.name.lowercase())
//            assertEquals(
//                expected = authCodeResponse.code,
//                actual = it.parameters[ResponseType.Code.name.lowercase()]
//            )
//        }
//
//        // Issuer client redirects the request to redirectUri
//
//        println("// -------- WALLET ----------")
//        println("// token req")
//        val tokenReq =
//            TokenRequest(
//                GrantType.authorization_code,
//                testCIClientConfig.clientID,
//                code = authCodeResponse.code!!
//            )
//        println("tokenReq: $tokenReq")
//
//
//        println("// -------- CREDENTIAL ISSUER ----------")
//        // Validate token request against authorization code
//        validateTokenRequestRaw(tokenReq.toHttpParameters(), authorizationCode)
//
//        // Generate Access Token
//        val expirationTime = (Clock.System.now().epochSeconds + 864000L) // ten days in milliseconds
//
//        val accessToken = signToken(
//            privateKey = ciTestProvider.CI_TOKEN_KEY,
//            payload = buildJsonObject {
//                put(JWTClaims.Payload.audience, ciTestProvider.baseUrl)
//                put(JWTClaims.Payload.subject, authReq.clientId)
//                put(JWTClaims.Payload.issuer, ciTestProvider.baseUrl)
//                put(JWTClaims.Payload.expirationTime, expirationTime)
//                put(JWTClaims.Payload.notBeforeTime, Clock.System.now().epochSeconds)
//            }
//        )
//
//        // Issuer client creates cPoPnonce
//        val cPoPNonce = "secured_cPoPnonce"
//        val tokenResponse: TokenResponse = TokenResponse.success(accessToken, "bearer", cNonce = cPoPNonce, expiresIn = expirationTime)
//
//        // Issuer client sends successful response with tokenResponse
//
//
//        println("// -------- WALLET ----------")
//        assertTrue(actual = tokenResponse.isSuccess)
//        assertNotNull(actual = tokenResponse.accessToken)
//        assertNotNull(actual = tokenResponse.cNonce)
//
//        println("// receive credential")
//        val nonce = tokenResponse.cNonce!!
//        val holderDid = TEST_WALLET_DID_WEB1
////        val holderKey = runBlocking { JWKKey.importJWK(TEST_WALLET_KEY1) }.getOrThrow()
//        val holderKey = JWKKey.importJWK(TEST_WALLET_KEY1).getOrThrow()
////        val holderKeyId = runBlocking { holderKey.getKeyId() }
//        val holderKeyId = holderKey.getKeyId()
//        val proofKeyId = "$holderDid#$holderKeyId"
//        val proofOfPossession =
//            ProofOfPossession.JWTProofBuilder(ciTestProvider.baseUrl, null, nonce, proofKeyId).build(holderKey)
//
//        val credReq = CredentialRequest.forOfferedCredential(offeredCredential, proofOfPossession)
//        println("credReq: $credReq")
//
//
//        println("// -------- CREDENTIAL ISSUER ----------")
//        // Issuer Client extracts Access Token from header
//        verifyToken(tokenResponse.accessToken.toString(),  ciTestProvider.CI_TOKEN_KEY.getPublicKey())
//
//        val parsedHolderKeyId = credReq.proof?.jwt?.let { JwtUtils.parseJWTHeader(it) }?.get("kid")?.jsonPrimitive?.content
//        assertNotNull(actual = parsedHolderKeyId)
//        assertTrue(actual = parsedHolderKeyId.startsWith("did:"))
//        val parsedHolderDid = parsedHolderKeyId.substringBefore("#")
////        val resolvedKeyForHolderDid = runBlocking { DidService.resolveToKey(parsedHolderDid) }.getOrThrow()
//        val resolvedKeyForHolderDid = DidService.resolveToKey(parsedHolderDid).getOrThrow()
//
//        val validPoP = credReq.proof?.validateJwtProof(resolvedKeyForHolderDid, ciTestProvider.baseUrl,null, nonce, parsedHolderKeyId)
//        assertTrue(actual = validPoP!!)
//        val generatedCredential = ciTestProvider.generateCredential(credReq).credential
//        assertNotNull(generatedCredential)
//        val credentialResponse: CredentialResponse = CredentialResponse.success(credReq.format, generatedCredential)
//
//        println("// -------- WALLET ----------")
//        assertTrue(actual = credentialResponse.isSuccess)
//        assertFalse(actual = credentialResponse.isDeferred)
//        assertEquals(expected = CredentialFormat.jwt_vc_json, actual = credentialResponse.format!!)
//        assertTrue(actual = credentialResponse.credential!!.instanceOf(JsonPrimitive::class))
//
//        println("// parse and verify credential")
//        val credential = credentialResponse.credential!!.jsonPrimitive.content
//        println(">>> Issued credential: $credential")
//        verifyIssuerAndSubjectId(
//            SDJwt.parse(credential).fullPayload["vc"]?.jsonObject!!,
//            ciTestProvider.CI_ISSUER_DID, credentialWallet.TEST_DID
//        )
//        assertTrue(actual = JwtSignaturePolicy().verify(credential, null, mapOf()).isSuccess)
//    }
//
//    @Test
//    fun testCredentialIssuanceIsolatedFunctionsAuthCodeFlowWithIdTokenAuth() = runTest {
//        // TODO: consider re-implementing CITestProvider, making use of new lib functions
//        // is it ok to generate the credential offer using the ciTestProvider (OpenIDCredentialIssuer class) ?
//
//        println("// -------- CREDENTIAL ISSUER ----------")
//        // init credential offer for full authorization code flow
//        // Issuer Client stores the authentication method in session, ID_TOKEN in this case (PRE_AUTHORIZED, PWD, ID_TOKEN, VP_TOKEN, NONE)
//
//        val credOffer = CredentialOffer.Builder(ciTestProvider.baseUrl)
//            .addOfferedCredential("VerifiableId")
//            .addAuthorizationCodeGrant("test-state-idtoken-auth")
//            .build()
//        val issueReqUrl = OpenID4VCI.getCredentialOfferRequestUrl(credOffer)
//        ciTestProvider.deferIssuance = false
//
//        // Show credential offer request as QR code
//        println(issueReqUrl)
//
//
//        println("// -------- WALLET ----------")
////        val parsedCredOffer = runBlocking { OpenID4VCI.parseAndResolveCredentialOfferRequestUrl(issueReqUrl) }
//        val parsedCredOffer = OpenID4VCI.parseAndResolveCredentialOfferRequestUrl(issueReqUrl)
//        assertEquals(expected = credOffer.toJSONString(), actual = parsedCredOffer.toJSONString())
//
////        val providerMetadata = runBlocking { OpenID4VCI.resolveCIProviderMetadata(parsedCredOffer) }
//        val providerMetadata = OpenID4VCI.resolveCIProviderMetadata(parsedCredOffer)
//        assertEquals(expected = parsedCredOffer.credentialIssuer, actual = providerMetadata.credentialIssuer)
//
//        println("// resolve offered credentials")
//        val offeredCredentials = OpenID4VCI.resolveOfferedCredentials(parsedCredOffer, providerMetadata)
//        println("offeredCredentials: $offeredCredentials")
//        assertEquals(expected = 1, actual = offeredCredentials.size)
//        assertEquals(expected = CredentialFormat.jwt_vc_json, actual = offeredCredentials.first().format)
//        assertEquals(expected = "VerifiableId", actual = offeredCredentials.first().types?.last())
//        val offeredCredential = offeredCredentials.first()
//        println("offeredCredentials[0]: $offeredCredential")
//
//        println("// go through authorization code flow to receive offered credential")
//        println("// auth request (short-cut, without pushed authorization request)")
//        val authReqWalletState = "secured_state_wallet"
//        val authReqWallet = AuthorizationRequest(
//            setOf(ResponseType.Code), testCIClientConfig.clientID,
//            redirectUri = credentialWallet.config.redirectUri,
//            issuerState = parsedCredOffer.grants[GrantType.authorization_code.value]!!.issuerState,
//            state = authReqWalletState,
//            clientMetadata = OpenIDClientMetadata(customParameters=mapOf("authorization_endpoint" to "wallet-api.com/callback".toJsonElement()))
//        )
//        println("authReq: $authReqWallet")
//
//        // Wallet client calls /authorize endpoint
//
//        println("// -------- CREDENTIAL ISSUER ----------")
//        val authReq = OpenID4VCI.validateAuthorizationRequestQueryString(authReqWallet.toHttpQueryString())
//
//        // Issuer client retrieves issuance session based on issuer state and stores the credential request, including authReqWallet state
//        // Issuer client checks the authentication method of the session and find out that authentication method is ID_TOKEN
//        // Issuer client creates state and nonce for the id token authorization request
//        val authReqIssuerState = "secured_state_issuer"
//        val authReqIssuerNonce = "secured_nonce_issuer"
//
//        val authReqIssuer = OpenID4VCI.generateAuthorizationRequest(authReq, ciTestProvider.baseUrl, ciTestProvider.CI_TOKEN_KEY, ResponseType.IdToken, authReqIssuerState, authReqIssuerNonce)
//
//        // Redirect uri is located in the client_metadata.authorization_endpoint or "openid://"
//        val redirectUriReq = authReqIssuer.toRedirectUri(authReq.clientMetadata?.customParameters?.get("authorization_endpoint")?.jsonPrimitive?.content ?: "openid://", authReq.responseMode ?: ResponseMode.query)
//        Url(redirectUriReq).let {
//            assertContains(iterable = it.parameters.names(), element = "request")
//            assertContains(iterable = it.parameters.names(), element = "redirect_uri")
//            assertEquals(expected = ciTestProvider.baseUrl + "/direct_post", actual = it.parameters["redirect_uri"])
//        }
//
//        // Issuer client redirects the request to redirectUri
//        println("// -------- WALLET ----------")
//        // wallet creates id token
//        val idToken = "eyJ0eXAiOiJKV1QiLCJhbGciOiJFUzI1NiIsImtpZCI6ImRpZDprZXk6ejJkbXpEODFjZ1B4OFZraTdKYnV1TW1GWXJXUGdZb3l0eWtVWjNleXFodDFqOUtib2o3ZzlQZlhKeGJiczRLWWVneXI3RUxuRlZucERNemJKSkRETlpqYXZYNmp2dERtQUxNYlhBR1c2N3BkVGdGZWEyRnJHR1NGczhFanhpOTZvRkxHSGNMNFA2YmpMRFBCSkV2UlJIU3JHNExzUG5lNTJmY3p0Mk1XakhMTEpCdmhBQyN6MmRtekQ4MWNnUHg4VmtpN0pidXVNbUZZcldQZ1lveXR5a1VaM2V5cWh0MWo5S2JvajdnOVBmWEp4YmJzNEtZZWd5cjdFTG5GVm5wRE16YkpKREROWmphdlg2anZ0RG1BTE1iWEFHVzY3cGRUZ0ZlYTJGckdHU0ZzOEVqeGk5Nm9GTEdIY0w0UDZiakxEUEJKRXZSUkhTckc0THNQbmU1MmZjenQyTVdqSExMSkJ2aEFDIn0.eyJub25jZSI6ImE4YWE1NDYwLTRmN2UtNDRmNy05ZGE3LWU1NmQ0YjIxMWE1MSIsInN1YiI6ImRpZDprZXk6ejJkbXpEODFjZ1B4OFZraTdKYnV1TW1GWXJXUGdZb3l0eWtVWjNleXFodDFqOUtib2o3ZzlQZlhKeGJiczRLWWVneXI3RUxuRlZucERNemJKSkRETlpqYXZYNmp2dERtQUxNYlhBR1c2N3BkVGdGZWEyRnJHR1NGczhFanhpOTZvRkxHSGNMNFA2YmpMRFBCSkV2UlJIU3JHNExzUG5lNTJmY3p0Mk1XakhMTEpCdmhBQyIsImlzcyI6ImRpZDprZXk6ejJkbXpEODFjZ1B4OFZraTdKYnV1TW1GWXJXUGdZb3l0eWtVWjNleXFodDFqOUtib2o3ZzlQZlhKeGJiczRLWWVneXI3RUxuRlZucERNemJKSkRETlpqYXZYNmp2dERtQUxNYlhBR1c2N3BkVGdGZWEyRnJHR1NGczhFanhpOTZvRkxHSGNMNFA2YmpMRFBCSkV2UlJIU3JHNExzUG5lNTJmY3p0Mk1XakhMTEpCdmhBQyIsImF1ZCI6Imh0dHBzOi8vMDFiYi01LTIwMy0xNzQtNjcubmdyb2stZnJlZS5hcHAiLCJpYXQiOjE3MjExNDQ3MzYsImV4cCI6MTcyMTE0NTAzNn0.VPWyLkMQAlcc40WCNSRH-Vxaj4LHi-wf2P9kcEKDvcdyVec2xJIwkg0JF4INMbLCkF0Y89lT0oswALd345wdUg"
//        // wallet calls POST /direct_post (e.g. redirect_uri of Issuer Auth Req) providing the id_token
//
//        println("// -------- CREDENTIAL ISSUER ----------")
//        // Create validateIdTokenResponse()
//        val idTokenPayload = validateAuthorizationRequestToken(idToken)
//
//        // Issuer Client validates states and nonces based on idTokenPayload
//
//        // Issuer client generates authorization code
//        val authorizationCode = "secured_code"
//        val authCodeResponse: AuthorizationCodeResponse = AuthorizationCodeResponse.success(authorizationCode,  mapOf("state" to listOf(authReqWallet.state!!)))
//
//        val redirectUri = authCodeResponse.toRedirectUri(authReq.redirectUri ?: TODO(), authReq.responseMode ?: ResponseMode.query)
//        Url(redirectUri).let {
//            assertContains(iterable = it.parameters.names(), element = ResponseType.Code.name.lowercase())
//            assertEquals(
//                expected = authCodeResponse.code,
//                actual = it.parameters[ResponseType.Code.name.lowercase()]
//            )
//        }
//
//        // Issuer client redirects the request to redirectUri
//
//        println("// -------- WALLET ----------")
//        println("// token req")
//        val tokenReq =
//            TokenRequest(
//                GrantType.authorization_code,
//                testCIClientConfig.clientID,
//                code = authCodeResponse.code!!
//            )
//        println("tokenReq: $tokenReq")
//
//
//        println("// -------- CREDENTIAL ISSUER ----------")
//        // Validate token request against authorization code
//        validateTokenRequestRaw(tokenReq.toHttpParameters(), authorizationCode)
//
//        // Generate Access Token
//        val expirationTime = (Clock.System.now().epochSeconds + 864000L) // ten days in milliseconds
//        val accessToken = signToken(
//            privateKey = ciTestProvider.CI_TOKEN_KEY,
//            payload = buildJsonObject {
//                put(JWTClaims.Payload.audience, ciTestProvider.baseUrl)
//                put(JWTClaims.Payload.subject, authReq.clientId)
//                put(JWTClaims.Payload.issuer, ciTestProvider.baseUrl)
//                put(JWTClaims.Payload.expirationTime, expirationTime)
//                put(JWTClaims.Payload.notBeforeTime, Clock.System.now().epochSeconds)
//            }
//        )
//        // Issuer client creates cPoPnonce
//        val cPoPNonce = "secured_cPoPnonce"
//        val tokenResponse: TokenResponse = TokenResponse.success(accessToken, "bearer", cNonce = cPoPNonce, expiresIn = expirationTime)
//
//        // Issuer client sends successful response with tokenResponse
//
//
//        //
//        println("// -------- WALLET ----------")
//        assertTrue(actual = tokenResponse.isSuccess)
//        assertNotNull(actual = tokenResponse.accessToken)
//        assertNotNull(actual = tokenResponse.cNonce)
//
//        println("// receive credential")
//        val nonce = tokenResponse.cNonce!!
//        val holderDid = TEST_WALLET_DID_WEB1
////        val holderKey = runBlocking { JWKKey.importJWK(TEST_WALLET_KEY1) }.getOrThrow()
//        val holderKey = JWKKey.importJWK(TEST_WALLET_KEY1).getOrThrow()
////        val holderKeyId = runBlocking { holderKey.getKeyId() }
//        val holderKeyId = holderKey.getKeyId()
//        val proofKeyId = "$holderDid#$holderKeyId"
//        val proofOfPossession =
//            ProofOfPossession.JWTProofBuilder(ciTestProvider.baseUrl, null, nonce, proofKeyId).build(holderKey)
//
//        val credReq = CredentialRequest.forOfferedCredential(offeredCredential, proofOfPossession)
//        println("credReq: $credReq")
//
//
//        println("// -------- CREDENTIAL ISSUER ----------")
//        // Issuer Client extracts Access Token from header
//        verifyToken(tokenResponse.accessToken.toString(),  ciTestProvider.CI_TOKEN_KEY)
//
//        val parsedHolderKeyId = credReq.proof?.jwt?.let { JwtUtils.parseJWTHeader(it) }?.get("kid")?.jsonPrimitive?.content
//        assertNotNull(actual = parsedHolderKeyId)
//        assertTrue(actual = parsedHolderKeyId.startsWith("did:"))
//        val parsedHolderDid = parsedHolderKeyId.substringBefore("#")
////        val resolvedKeyForHolderDid = runBlocking { DidService.resolveToKey(parsedHolderDid) }.getOrThrow()
//        val resolvedKeyForHolderDid = DidService.resolveToKey(parsedHolderDid).getOrThrow()
//
//        val validPoP = credReq.proof?.validateJwtProof(resolvedKeyForHolderDid, ciTestProvider.baseUrl,null, nonce, parsedHolderKeyId)
//        assertTrue(actual = validPoP!!)
//        val generatedCredential = ciTestProvider.generateCredential(credReq).credential
//        assertNotNull(generatedCredential)
//        val credentialResponse: CredentialResponse = CredentialResponse.success(credReq.format, generatedCredential)
//
//        println("// -------- WALLET ----------")
//        assertTrue(actual = credentialResponse.isSuccess)
//        assertFalse(actual = credentialResponse.isDeferred)
//        assertEquals(expected = CredentialFormat.jwt_vc_json, actual = credentialResponse.format!!)
//        assertTrue(actual = credentialResponse.credential!!.instanceOf(JsonPrimitive::class))
//
//        println("// parse and verify credential")
//        val credential = credentialResponse.credential!!.jsonPrimitive.content
//        println(">>> Issued credential: $credential")
//        verifyIssuerAndSubjectId(
//            SDJwt.parse(credential).fullPayload["vc"]?.jsonObject!!,
//            ciTestProvider.CI_ISSUER_DID, credentialWallet.TEST_DID
//        )
//        assertTrue(actual = JwtSignaturePolicy().verify(credential, null, mapOf()).isSuccess)
//    }
//
//    @Test
//    fun testCredentialIssuanceIsolatedFunctionsAuthCodeFlowWithVpTokenAuth() = runTest {
//        // TODO: consider re-implementing CITestProvider, making use of new lib functions
//        // is it ok to generate the credential offer using the ciTestProvider (OpenIDCredentialIssuer class) ?
//
//        println("// -------- CREDENTIAL ISSUER ----------")
//        // init credential offer for full authorization code flow
//        // Issuer Client stores the authentication method in session, ID_TOKEN in this case (PRE_AUTHORIZED, PWD, ID_TOKEN, VP_TOKEN, NONE)
//
//        val credOffer = CredentialOffer.Builder(ciTestProvider.baseUrl)
//            .addOfferedCredential("VerifiableId")
//            .addAuthorizationCodeGrant("test-state-vptoken-auth")
//            .build()
//        val issueReqUrl = OpenID4VCI.getCredentialOfferRequestUrl(credOffer)
//        ciTestProvider.deferIssuance = false
//
//        // Show credential offer request as QR code
//        println(issueReqUrl)
//
//
//        println("// -------- WALLET ----------")
////        val parsedCredOffer = runBlocking { OpenID4VCI.parseAndResolveCredentialOfferRequestUrl(issueReqUrl) }
//        val parsedCredOffer = OpenID4VCI.parseAndResolveCredentialOfferRequestUrl(issueReqUrl)
//        assertEquals(expected = credOffer.toJSONString(), actual = parsedCredOffer.toJSONString())
//
////        val providerMetadata = runBlocking { OpenID4VCI.resolveCIProviderMetadata(parsedCredOffer) }
//        val providerMetadata = OpenID4VCI.resolveCIProviderMetadata(parsedCredOffer)
//        assertEquals(expected = parsedCredOffer.credentialIssuer, actual = providerMetadata.credentialIssuer)
//
//        println("// resolve offered credentials")
//        val offeredCredentials = OpenID4VCI.resolveOfferedCredentials(parsedCredOffer, providerMetadata)
//        println("offeredCredentials: $offeredCredentials")
//        assertEquals(expected = 1, actual = offeredCredentials.size)
//        assertEquals(expected = CredentialFormat.jwt_vc_json, actual = offeredCredentials.first().format)
//        assertEquals(expected = "VerifiableId", actual = offeredCredentials.first().types?.last())
//        val offeredCredential = offeredCredentials.first()
//        println("offeredCredentials[0]: $offeredCredential")
//
//        println("// go through authorization code flow to receive offered credential")
//        println("// auth request (short-cut, without pushed authorization request)")
//        val authReqWalletState = "secured_state_wallet"
//        val authReqWallet = AuthorizationRequest(
//            setOf(ResponseType.Code), testCIClientConfig.clientID,
//            redirectUri = credentialWallet.config.redirectUri,
//            issuerState = parsedCredOffer.grants[GrantType.authorization_code.value]!!.issuerState,
//            state = authReqWalletState,
//            clientMetadata = OpenIDClientMetadata(customParameters=mapOf("authorization_endpoint" to "wallet-api.com/callback".toJsonElement()))
//        )
//        println("authReq: $authReqWallet")
//
//        // Wallet client calls /authorize endpoint
//
//        println("// -------- CREDENTIAL ISSUER ----------")
//        val authReq = validateAuthorizationRequestQueryString(authReqWallet.toHttpQueryString())
//
//        // Issuer client retrieves issuance session based on issuer state and stores the credential request, including authReqWallet state
//        // Issuer client checks the authentication method of the session and find out that authentication method is VP_TOKEN
//        // Issuer client generates presentation definition
//        val requestedCredentialId = "OpenBadgeCredential"
//        val vpProfile = OpenId4VPProfile.EBSIV3
//        val requestCredentialsArr = buildJsonArray { add(requestedCredentialId) }
//        val requestedTypes = requestCredentialsArr.map {
//            when (it) {
//                is JsonPrimitive -> it.contentOrNull
//                is JsonObject -> it["credential"]?.jsonPrimitive?.contentOrNull
//                else -> throw IllegalArgumentException("Invalid JSON type for requested credential: $it")
//            } ?: throw IllegalArgumentException("Invalid VC type for requested credential: $it")
//        }
//
//        val presentationDefinition = PresentationDefinition.primitiveGenerationFromVcTypes(requestedTypes, vpProfile)
//
//        // Issuer client creates state and nonce for the vp token authorization request
//        val authReqIssuerState = "secured_state_issuer"
//        val authReqIssuerNonce = "secured_nonce_issuer"
//
//        val authReqIssuer = generateAuthorizationRequest(authReq, ciTestProvider.baseUrl, ciTestProvider.CI_TOKEN_KEY, ResponseType.VpToken, authReqIssuerState, authReqIssuerNonce, true, presentationDefinition)
//
//        // Redirect uri is located in the client_metadata.authorization_endpoint or "openid://"
//        val redirectUriReq = authReqIssuer.toRedirectUri(authReq.clientMetadata?.customParameters?.get("authorization_endpoint")?.jsonPrimitive?.content ?: "openid://", authReq.responseMode ?: ResponseMode.query)
//        Url(redirectUriReq).let {
//            assertContains(iterable = it.parameters.names(), element = "request")
//            assertContains(iterable = it.parameters.names(), element = "redirect_uri")
//            assertContains(iterable = it.parameters.names(), element = "presentation_definition")
//            assertEquals(expected = ciTestProvider.baseUrl + "/direct_post", actual = it.parameters["redirect_uri"])
//        }
//
//        // Issuer client redirects the request to redirectUri
//        println("// -------- WALLET ----------")
//        // wallet creates vp token
//        val vpToken = "eyJhbGciOiJFZERTQSIsInR5cCI6IkpXVCIsImtpZCI6ImRpZDprZXk6ejZNa3A3QVZ3dld4bnNORHVTU2JmMTlzZ0t6cngyMjNXWTk1QXFaeUFHaWZGVnlWI3o2TWtwN0FWd3ZXeG5zTkR1U1NiZjE5c2dLenJ4MjIzV1k5NUFxWnlBR2lmRlZ5ViJ9.eyJzdWIiOiJkaWQ6a2V5Ono2TWtwN0FWd3ZXeG5zTkR1U1NiZjE5c2dLenJ4MjIzV1k5NUFxWnlBR2lmRlZ5ViIsIm5iZiI6MTcyMDc2NDAxOSwiaWF0IjoxNzIwNzY0MDc5LCJqdGkiOiJ1cm46dXVpZDpiNzE2YThlOC0xNzVlLTRhMTYtODZlMC0xYzU2Zjc4NTFhZDEiLCJpc3MiOiJkaWQ6a2V5Ono2TWtwN0FWd3ZXeG5zTkR1U1NiZjE5c2dLenJ4MjIzV1k5NUFxWnlBR2lmRlZ5ViIsIm5vbmNlIjoiNDY0YTAwMTUtNzQ1OS00Y2Y4LWJmNjgtNDg0ODQyYTE5Y2FmIiwiYXVkIjoiZGlkOmtleTp6Nk1rcDdBVnd2V3huc05EdVNTYmYxOXNnS3pyeDIyM1dZOTVBcVp5QUdpZkZWeVYiLCJ2cCI6eyJAY29udGV4dCI6WyJodHRwczovL3d3dy53My5vcmcvMjAxOC9jcmVkZW50aWFscy92MSJdLCJ0eXBlIjpbIlZlcmlmaWFibGVQcmVzZW50YXRpb24iXSwiaWQiOiJ1cm46dXVpZDpiNzE2YThlOC0xNzVlLTRhMTYtODZlMC0xYzU2Zjc4NTFhZDEiLCJob2xkZXIiOiJkaWQ6a2V5Ono2TWtwN0FWd3ZXeG5zTkR1U1NiZjE5c2dLenJ4MjIzV1k5NUFxWnlBR2lmRlZ5ViIsInZlcmlmaWFibGVDcmVkZW50aWFsIjpbImV5SmhiR2NpT2lKRlpFUlRRU0lzSW5SNWNDSTZJa3BYVkNJc0ltdHBaQ0k2SW1ScFpEcHJaWGs2ZWpaTmEzQTNRVlozZGxkNGJuTk9SSFZUVTJKbU1UbHpaMHQ2Y25neU1qTlhXVGsxUVhGYWVVRkhhV1pHVm5sV0luMC5leUpwYzNNaU9pSmthV1E2YTJWNU9ubzJUV3R3TjBGV2QzWlhlRzV6VGtSMVUxTmlaakU1YzJkTGVuSjRNakl6VjFrNU5VRnhXbmxCUjJsbVJsWjVWaUlzSW5OMVlpSTZJbVJwWkRwclpYazZlalpOYTJwdE1tZGhSM052WkVkamFHWkhOR3M0VURaTGQwTklXbk5XUlZCYWFHODFWblZGWWxrNU5IRnBRa0k1SWl3aWRtTWlPbnNpUUdOdmJuUmxlSFFpT2xzaWFIUjBjSE02THk5M2QzY3Vkek11YjNKbkx6SXdNVGd2WTNKbFpHVnVkR2xoYkhNdmRqRWlMQ0pvZEhSd2N6b3ZMM0IxY213dWFXMXpaMnh2WW1Gc0xtOXlaeTl6Y0dWakwyOWlMM1l6Y0RBdlkyOXVkR1Y0ZEM1cWMyOXVJbDBzSW1sa0lqb2lkWEp1T25WMWFXUTZNVEl6SWl3aWRIbHdaU0k2V3lKV1pYSnBabWxoWW14bFEzSmxaR1Z1ZEdsaGJDSXNJazl3Wlc1Q1lXUm5aVU55WldSbGJuUnBZV3dpWFN3aWJtRnRaU0k2SWtwR1JpQjRJSFpqTFdWa2RTQlFiSFZuUm1WemRDQXpJRWx1ZEdWeWIzQmxjbUZpYVd4cGRIa2lMQ0pwYzNOMVpYSWlPbnNpZEhsd1pTSTZXeUpRY205bWFXeGxJbDBzSW1sa0lqb2laR2xrT21WNFlXMXdiR1U2TVRJeklpd2libUZ0WlNJNklrcHZZbk1nWm05eUlIUm9aU0JHZFhSMWNtVWdLRXBHUmlraUxDSjFjbXdpT2lKb2RIUndjem92TDNkM2R5NXFabVl1YjNKbkx5SXNJbWx0WVdkbElqcDdJbWxrSWpvaWFIUjBjSE02THk5M00yTXRZMk5uTG1kcGRHaDFZaTVwYnk5Mll5MWxaQzl3YkhWblptVnpkQzB4TFRJd01qSXZhVzFoWjJWekwwcEdSbDlNYjJkdlRHOWphM1Z3TG5CdVp5SXNJblI1Y0dVaU9pSkpiV0ZuWlNKOWZTd2lhWE56ZFdGdVkyVkVZWFJsSWpvaU1qQXlNeTB3TnkweU1GUXdOem93TlRvME5Gb2lMQ0psZUhCcGNtRjBhVzl1UkdGMFpTSTZJakl3TXpNdE1EY3RNakJVTURjNk1EVTZORFJhSWl3aVkzSmxaR1Z1ZEdsaGJGTjFZbXBsWTNRaU9uc2lhV1FpT2lKa2FXUTZaWGhoYlhCc1pUb3hNak1pTENKMGVYQmxJanBiSWtGamFHbGxkbVZ0Wlc1MFUzVmlhbVZqZENKZExDSmhZMmhwWlhabGJXVnVkQ0k2ZXlKcFpDSTZJblZ5YmpwMWRXbGtPbUZqTWpVMFltUTFMVGhtWVdRdE5HSmlNUzA1WkRJNUxXVm1aRGt6T0RVek5qa3lOaUlzSW5SNWNHVWlPbHNpUVdOb2FXVjJaVzFsYm5RaVhTd2libUZ0WlNJNklrcEdSaUI0SUhaakxXVmtkU0JRYkhWblJtVnpkQ0F6SUVsdWRHVnliM0JsY21GaWFXeHBkSGtpTENKa1pYTmpjbWx3ZEdsdmJpSTZJbFJvYVhNZ2QyRnNiR1YwSUhOMWNIQnZjblJ6SUhSb1pTQjFjMlVnYjJZZ1Z6TkRJRlpsY21sbWFXRmliR1VnUTNKbFpHVnVkR2xoYkhNZ1lXNWtJR2hoY3lCa1pXMXZibk4wY21GMFpXUWdhVzUwWlhKdmNHVnlZV0pwYkdsMGVTQmtkWEpwYm1jZ2RHaGxJSEJ5WlhObGJuUmhkR2x2YmlCeVpYRjFaWE4wSUhkdmNtdG1iRzkzSUdSMWNtbHVaeUJLUmtZZ2VDQldReTFGUkZVZ1VHeDFaMFpsYzNRZ015NGlMQ0pqY21sMFpYSnBZU0k2ZXlKMGVYQmxJam9pUTNKcGRHVnlhV0VpTENKdVlYSnlZWFJwZG1VaU9pSlhZV3hzWlhRZ2MyOXNkWFJwYjI1eklIQnliM1pwWkdWeWN5QmxZWEp1WldRZ2RHaHBjeUJpWVdSblpTQmllU0JrWlcxdmJuTjBjbUYwYVc1bklHbHVkR1Z5YjNCbGNtRmlhV3hwZEhrZ1pIVnlhVzVuSUhSb1pTQndjbVZ6Wlc1MFlYUnBiMjRnY21WeGRXVnpkQ0IzYjNKclpteHZkeTRnVkdocGN5QnBibU5zZFdSbGN5QnpkV05qWlhOelpuVnNiSGtnY21WalpXbDJhVzVuSUdFZ2NISmxjMlZ1ZEdGMGFXOXVJSEpsY1hWbGMzUXNJR0ZzYkc5M2FXNW5JSFJvWlNCb2IyeGtaWElnZEc4Z2MyVnNaV04wSUdGMElHeGxZWE4wSUhSM2J5QjBlWEJsY3lCdlppQjJaWEpwWm1saFlteGxJR055WldSbGJuUnBZV3h6SUhSdklHTnlaV0YwWlNCaElIWmxjbWxtYVdGaWJHVWdjSEpsYzJWdWRHRjBhVzl1TENCeVpYUjFjbTVwYm1jZ2RHaGxJSEJ5WlhObGJuUmhkR2x2YmlCMGJ5QjBhR1VnY21WeGRXVnpkRzl5TENCaGJtUWdjR0Z6YzJsdVp5QjJaWEpwWm1sallYUnBiMjRnYjJZZ2RHaGxJSEJ5WlhObGJuUmhkR2x2YmlCaGJtUWdkR2hsSUdsdVkyeDFaR1ZrSUdOeVpXUmxiblJwWVd4ekxpSjlMQ0pwYldGblpTSTZleUpwWkNJNkltaDBkSEJ6T2k4dmR6TmpMV05qWnk1bmFYUm9kV0l1YVc4dmRtTXRaV1F2Y0d4MVoyWmxjM1F0TXkweU1ESXpMMmx0WVdkbGN5OUtSa1l0VmtNdFJVUlZMVkJNVlVkR1JWTlVNeTFpWVdSblpTMXBiV0ZuWlM1d2JtY2lMQ0owZVhCbElqb2lTVzFoWjJVaWZYMTlMQ0pqY21Wa1pXNTBhV0ZzVTJOb1pXMWhJanA3SW1sa0lqb2lhSFIwY0hNNkx5OXdkWEpzTG1sdGMyZHNiMkpoYkM1dmNtY3ZjM0JsWXk5dllpOTJNM0F3TDNOamFHVnRZUzlxYzI5dUwyOWlYM1l6Y0RCZllXTm9hV1YyWlcxbGJuUmpjbVZrWlc1MGFXRnNYM05qYUdWdFlTNXFjMjl1SWl3aWRIbHdaU0k2SWtaMWJHeEtjMjl1VTJOb1pXMWhWbUZzYVdSaGRHOXlNakF5TVNKOWZTd2lhblJwSWpvaWRYSnVPblYxYVdRNk1USXpJaXdpWlhod0lqb3lNREExTkRVMU9UUTBMQ0pwWVhRaU9qRTJPRGs0TXpZM05EUXNJbTVpWmlJNk1UWTRPVGd6TmpjME5IMC5PRHZUQXVMN2JrME1pX3hNLVFualg4azByZ3VUeWtiYzJ6bFdFMVU2SGlmVXFjWTdFVU5GcUdUZWFUWHRESkxrODBuZWN6YkNNTGh1YlZseEFkdl9DdyJdfX0.zTXluOVIP0sQzc5GzNvtVvWRiaC-x9qMZg0d-EvCuRIg7QSgY0hmrfVlAzh2IDEvaXZ1ahM3hSVDx_YI74ToAw"
//        // wallet calls POST /direct_post (e.g. redirect_uri of Issuer Auth Req) providing the id_token
//
//        println("// -------- CREDENTIAL ISSUER ----------")
//        val vpTokenPayload = validateAuthorizationRequestToken(vpToken)
//
//        // Issuer Client validates states and nonces based on vpTokenPayload
//
//        // Issuer client generates authorization code
//        val authorizationCode = "secured_code"
//        val authCodeResponse: AuthorizationCodeResponse = AuthorizationCodeResponse.success(authorizationCode,  mapOf("state" to listOf(authReqWallet.state!!)))
//
//        val redirectUri = authCodeResponse.toRedirectUri(authReq.redirectUri ?: TODO(), authReq.responseMode ?: ResponseMode.query)
//        Url(redirectUri).let {
//            assertContains(iterable = it.parameters.names(), element = ResponseType.Code.name.lowercase())
//            assertEquals(
//                expected = authCodeResponse.code,
//                actual = it.parameters[ResponseType.Code.name.lowercase()]
//            )
//        }
//
//        // Issuer client redirects the request to redirectUri
//
//        println("// -------- WALLET ----------")
//        println("// token req")
//        val tokenReq =
//            TokenRequest(
//                GrantType.authorization_code,
//                testCIClientConfig.clientID,
//                code = authCodeResponse.code!!
//            )
//        println("tokenReq: $tokenReq")
//
//
//        println("// -------- CREDENTIAL ISSUER ----------")
//        // Validate token request against authorization code
//        validateTokenRequestRaw(tokenReq.toHttpParameters(), authorizationCode)
//
//        // Generate Access Token
//        val expirationTime = (Clock.System.now().epochSeconds + 864000L) // ten days in milliseconds
//        val accessToken = signToken(
//            privateKey = ciTestProvider.CI_TOKEN_KEY,
//            payload = buildJsonObject {
//                put(JWTClaims.Payload.audience, ciTestProvider.baseUrl)
//                put(JWTClaims.Payload.subject, authReq.clientId)
//                put(JWTClaims.Payload.issuer, ciTestProvider.baseUrl)
//                put(JWTClaims.Payload.expirationTime, expirationTime)
//                put(JWTClaims.Payload.notBeforeTime, Clock.System.now().epochSeconds)
//            }
//        )
//        // Issuer client creates cPoPnonce
//        val cPoPNonce = "secured_cPoPnonce"
//        val tokenResponse: TokenResponse = TokenResponse.success(accessToken, "bearer", cNonce = cPoPNonce, expiresIn = expirationTime)
//
//        // Issuer client sends successful response with tokenResponse
//
//
//        //
//        println("// -------- WALLET ----------")
//        assertTrue(actual = tokenResponse.isSuccess)
//        assertNotNull(actual = tokenResponse.accessToken)
//        assertNotNull(actual = tokenResponse.cNonce)
//
//        println("// receive credential")
//        val nonce = tokenResponse.cNonce!!
//        val holderDid = TEST_WALLET_DID_WEB1
////        val holderKey = runBlocking { JWKKey.importJWK(TEST_WALLET_KEY1) }.getOrThrow()
//        val holderKey = JWKKey.importJWK(TEST_WALLET_KEY1).getOrThrow()
////        val holderKeyId = runBlocking { holderKey.getKeyId() }
//        val holderKeyId = holderKey.getKeyId()
//        val proofKeyId = "$holderDid#$holderKeyId"
//        val proofOfPossession =
//            ProofOfPossession.JWTProofBuilder(ciTestProvider.baseUrl, null, nonce, proofKeyId).build(holderKey)
//
//        val credReq = CredentialRequest.forOfferedCredential(offeredCredential, proofOfPossession)
//        println("credReq: $credReq")
//
//
//        println("// -------- CREDENTIAL ISSUER ----------")
//        // Issuer Client extracts Access Token from header
//        verifyToken(tokenResponse.accessToken.toString(),  ciTestProvider.CI_TOKEN_KEY)
//
//        val parsedHolderKeyId = credReq.proof?.jwt?.let { JwtUtils.parseJWTHeader(it) }?.get("kid")?.jsonPrimitive?.content
//        assertNotNull(actual = parsedHolderKeyId)
//        assertTrue(actual = parsedHolderKeyId.startsWith("did:"))
//        val parsedHolderDid = parsedHolderKeyId.substringBefore("#")
////        val resolvedKeyForHolderDid = runBlocking { DidService.resolveToKey(parsedHolderDid) }.getOrThrow()
//        val resolvedKeyForHolderDid = DidService.resolveToKey(parsedHolderDid).getOrThrow()
//
//        val validPoP = credReq.proof?.validateJwtProof(resolvedKeyForHolderDid, ciTestProvider.baseUrl,null, nonce, parsedHolderKeyId)
//        assertTrue(actual = validPoP!!)
//        val generatedCredential = ciTestProvider.generateCredential(credReq).credential
//        assertNotNull(generatedCredential)
//        val credentialResponse: CredentialResponse = CredentialResponse.success(credReq.format, generatedCredential)
//
//        println("// -------- WALLET ----------")
//        assertTrue(actual = credentialResponse.isSuccess)
//        assertFalse(actual = credentialResponse.isDeferred)
//        assertEquals(expected = CredentialFormat.jwt_vc_json, actual = credentialResponse.format!!)
//        assertTrue(actual = credentialResponse.credential!!.instanceOf(JsonPrimitive::class))
//
//        println("// parse and verify credential")
//        val credential = credentialResponse.credential!!.jsonPrimitive.content
//        println(">>> Issued credential: $credential")
//        verifyIssuerAndSubjectId(
//            SDJwt.parse(credential).fullPayload["vc"]?.jsonObject!!,
//            ciTestProvider.CI_ISSUER_DID, credentialWallet.TEST_DID
//        )
//        assertTrue(actual = JwtSignaturePolicy().verify(credential, null, mapOf()).isSuccess)
//    }

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

        assertNotNull(actual = providerMetadata.credentialConfigurationsSupported)

        println("// resolve offered credentials")
        val offeredCredentials = OpenID4VCI.resolveOfferedCredentials(parsedOfferReq.credentialOffer!!, providerMetadata)
        println("offeredCredentials: $offeredCredentials")

        assertEquals(expected = 1, actual = offeredCredentials.size)
        assertEquals(expected = CredentialFormat.jwt_vc_json, actual = offeredCredentials.first().format)
        assertEquals(expected = "VerifiableId", actual = offeredCredentials.first().credentialDefinition?.type?.last())
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
                .addOfferedCredential(ciTestProvider.metadata.credentialConfigurationsSupported!!.keys.first()),
            5.minutes, allowPreAuthorized = true, txCode = TxCode(TxInputMode.numeric), txCodeValue = "1234"
        )
        println("issuanceSession: $issuanceSession")

        assertNotNull(actual = issuanceSession.credentialOffer)
        assertEquals(
            expected = ciTestProvider.metadata.credentialConfigurationsSupported!!.keys.first(),
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

        assertNotNull(actual = providerMetadata.credentialConfigurationsSupported)

        println("// resolve offered credentials")
        val offeredCredentials = OpenID4VCI.resolveOfferedCredentials(parsedOfferReq.credentialOffer!!, providerMetadata)
        println("offeredCredentials: $offeredCredentials")
        assertEquals(expected = 1, actual = offeredCredentials.size)
        assertEquals(expected = CredentialFormat.jwt_vc_json, actual = offeredCredentials.first().format)
        assertEquals(expected = "VerifiableId", actual = offeredCredentials.first().credentialDefinition?.type?.last())
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
                    credentialDefinition = CredentialDefinition(type = listOf("VerifiableCredential", "VerifiableId"))
                ), AuthorizationDetails(
                    type = OPENID_CREDENTIAL_AUTHORIZATION_TYPE,
                    format = CredentialFormat.jwt_vc_json,
                    credentialDefinition = CredentialDefinition(type = listOf("VerifiableCredential", "VerifiableAttestation", "VerifiableDiploma"))
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
                .addOfferedCredential(ciTestProvider.metadata.credentialConfigurationsSupported!!.keys.first()),
            5.minutes, allowPreAuthorized = true, TxCode(TxInputMode.numeric), txCodeValue = "1234"
        )
        println("issuanceSession: $issuanceSession")

        assertNotNull(actual = issuanceSession.credentialOffer)
        assertEquals(
            expected = ciTestProvider.metadata.credentialConfigurationsSupported!!.keys.first(),
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

        assertNotNull(actual = providerMetadata.credentialConfigurationsSupported)

        println("// resolve offered credentials")
        val offeredCredentials = OpenID4VCI.resolveOfferedCredentials(credentialOffer, providerMetadata)
        println("offeredCredentials: $offeredCredentials")
        assertEquals(expected = 1, actual = offeredCredentials.size)
        assertEquals(expected = CredentialFormat.jwt_vc_json, actual = offeredCredentials.first().format)
        assertEquals(expected = "VerifiableId", actual = offeredCredentials.first().credentialDefinition?.type?.last())
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


    val http = HttpClient {
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

fun testCredentialIssuanceIsolatedFunctionsAuthCodeFlowRedirectWithCode(authCodeResponse: AuthorizationCodeResponse, authReq: AuthorizationRequest): String {
    val redirectUri = authCodeResponse.toRedirectUri(authReq.redirectUri ?: TODO(), authReq.responseMode ?: ResponseMode.query)
    Url(redirectUri).let {
        assertContains(iterable = it.parameters.names(), element = ResponseType.Code.name.lowercase())
        assertEquals(
            expected = authCodeResponse.code,
            actual = it.parameters[ResponseType.Code.name.lowercase()]
        )
    }
    return redirectUri
}

fun testIsolatedFunctionsCreateCredentialOffer(baseUrl: String, issuerState: String, issuedCredentialId: String): String {
    val credOffer = CredentialOffer.Builder(baseUrl)
        .addOfferedCredential(issuedCredentialId)
        .addAuthorizationCodeGrant(issuerState)
        .build()

    return OpenID4VCI.getCredentialOfferRequestUrl(credOffer)
}

suspend fun testIsolatedFunctionsResolveCredentialOffer(credOfferUrl: String): OfferedCredential {
    val parsedCredOffer = OpenID4VCI.parseAndResolveCredentialOfferRequestUrl(credOfferUrl)
    val providerMetadata = OpenID4VCI.resolveCIProviderMetadata(parsedCredOffer)
    assertEquals(expected = parsedCredOffer.credentialIssuer, actual = providerMetadata.credentialIssuer)

    println("// resolve offered credentials")
    val offeredCredentials = OpenID4VCI.resolveOfferedCredentials(parsedCredOffer, providerMetadata)
    println("offeredCredentials: $offeredCredentials")
    assertEquals(expected = 1, actual = offeredCredentials.size)
    assertEquals(expected = CredentialFormat.jwt_vc_json, actual = offeredCredentials.first().format)
    assertEquals(expected = "VerifiableId", actual = offeredCredentials.first().credentialDefinition?.type?.last())
    val offeredCredential = offeredCredentials.first()
    println("offeredCredentials[0]: $offeredCredential")

    return offeredCredential
}
