package id.walt.oid4vc

import id.walt.auditor.Auditor
import id.walt.auditor.policies.SignaturePolicy
import id.walt.credentials.w3c.VerifiableCredential
import id.walt.oid4vc.data.*
import id.walt.oid4vc.definitions.OPENID_CREDENTIAL_AUTHORIZATION_TYPE
import id.walt.oid4vc.providers.OpenIDClientConfig
import id.walt.oid4vc.providers.CredentialWalletConfig
import id.walt.oid4vc.requests.*
import id.walt.oid4vc.responses.*
import id.walt.servicematrix.ServiceMatrix
import io.kotest.assertions.json.shouldMatchJson
import io.kotest.core.spec.style.AnnotationSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.beEmpty
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.instanceOf
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.java.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.util.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration.Companion.minutes

class CI_JVM_Test : AnnotationSpec() {

    var testMetadata = OpenIDProviderMetadata(
        authorizationEndpoint = "https://localhost/oidc",
        credentialsSupported = listOf(
            CredentialSupported(
                CredentialFormat.jwt_vc_json, "jwt_vc_json_fmt", setOf("did"), setOf("ES256K"),
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
                CredentialFormat.ldp_vc, "ldp_vc_1", setOf("did"), setOf("ES256K"),
                listOf(DisplayProperties("Verifiable ID")),
                types = listOf("VerifiableCredential", "VerifiableId"),
                context = listOf(
                    JsonPrimitive("https://www.w3.org/2018/credentials/v1"),
                    JsonObject(mapOf("@version" to JsonPrimitive(1.1)))
                )
            )
        )
    )

    val ktorClient = HttpClient(Java) {
        install(ContentNegotiation) {
            json()
        }
        install(Logging) {
            logger = Logger.SIMPLE
            level = LogLevel.ALL
        }
        followRedirects = false
    }

    private lateinit var ciTestProvider: CITestProvider
    private lateinit var credentialWallet: TestCredentialWallet
    private val testCIClientConfig = OpenIDClientConfig("test-client", null, redirectUri = "http://blank")

    @BeforeAll
    fun init() {
        ServiceMatrix("service-matrix.properties")
        ciTestProvider = CITestProvider()
        credentialWallet = TestCredentialWallet(CredentialWalletConfig("http://blank"))
        ciTestProvider.start()
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
        credentialSupported.format shouldBe CredentialFormat.jwt_vc_json
        credentialSupported.toJSONString() shouldMatchJson credentialSupportedJson
    }

    @Test
    fun testOIDProviderMetadata() {
        val metadataJson = testMetadata.toJSONString()
        println("metadataJson: $metadataJson")
        val metadataParsed = OpenIDProviderMetadata.fromJSONString(metadataJson)
        metadataParsed.toJSONString() shouldMatchJson metadataJson
        println("metadataParsed: $metadataParsed")
    }

    @Test
    suspend fun testFetchAndParseMetadata() {
        val response = ktorClient.get("http://localhost:8000/.well-known/openid-configuration")
        println("response: $response")
        response.status shouldBe HttpStatusCode.OK
        val respText = response.bodyAsText()
        val metadata: OpenIDProviderMetadata = OpenIDProviderMetadata.fromJSONString(respText)
        println("metadata: $metadata")
        metadata.toJSONString() shouldMatchJson ciTestProvider.metadata.toJSONString()
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
        parsedReq.clientId shouldBe "s6BhdRkqt3"
        parsedReq.authorizationDetails shouldNotBe null
        parsedReq.authorizationDetails!!.first().type shouldBe "openid_credential"

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

        parsedReq.toHttpQueryString() shouldBe expectedReq.toHttpQueryString()
        parseQueryString(parsedReq.toHttpQueryString()) shouldBe parseQueryString(authorizationReq)
    }

    @Test
    suspend fun testInvalidAuthorizationRequest() {
        // 0. get issuer metadata
        val providerMetadata =
            ktorClient.get(ciTestProvider.getCIProviderMetadataUrl()).call.body<OpenIDProviderMetadata>()
        providerMetadata.pushedAuthorizationRequestEndpoint shouldNotBe null

        // 1. send pushed authorization request with authorization details, containing info of credentials to be issued, receive session id
        val authReq = AuthorizationRequest(
            responseType = ResponseType.getResponseTypeString(ResponseType.code),
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

        parResp.isSuccess shouldBe false
        parResp.error shouldBe "invalid_request"
    }

    @Test
    suspend fun testFullAuthCodeFlow() {
        println("// 0. get issuer metadata")
        val providerMetadata =
            ktorClient.get(ciTestProvider.getCIProviderMetadataUrl()).call.body<OpenIDProviderMetadata>()
        println("providerMetadata: $providerMetadata")
        providerMetadata.pushedAuthorizationRequestEndpoint shouldNotBe null

        println("// 1. send pushed authorization request with authorization details, containing info of credentials to be issued, receive session id")
        val pushedAuthReq = AuthorizationRequest(
            responseType = ResponseType.getResponseTypeString(ResponseType.code),
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

        pushedAuthResp.isSuccess shouldBe true
        pushedAuthResp.requestUri shouldStartWith "urn:ietf:params:oauth:request_uri:"

        println("// 2. call authorize endpoint with request uri, receive HTTP redirect (302 Found) with Location header")
        providerMetadata.authorizationEndpoint shouldNotBe null
        val authReq = AuthorizationRequest(
            responseType = ResponseType.code.name,
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
        authResp.status shouldBe HttpStatusCode.Found
        authResp.headers.names() shouldContain HttpHeaders.Location.lowercase()
        val location = Url(authResp.headers[HttpHeaders.Location]!!)
        println("location: $location")
        location.toString() shouldStartWith credentialWallet.config.redirectUri!!
        location.parameters.names() shouldContain ResponseType.code.name

        println("// 3. Parse code response parameter from authorization redirect URI")
        providerMetadata.tokenEndpoint shouldNotBe null

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
        tokenResp.isSuccess shouldBe true
        tokenResp.accessToken shouldNotBe null
        tokenResp.cNonce shouldNotBe null

        println("// 5a. Call credential endpoint with access token, to receive credential (synchronous issuance)")
        providerMetadata.credentialEndpoint shouldNotBe null
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

        credentialResp.isSuccess shouldBe true
        credentialResp.isDeferred shouldBe false
        credentialResp.format!! shouldBe CredentialFormat.jwt_vc_json
        credentialResp.credential.shouldBeInstanceOf<JsonPrimitive>()
        val credential = VerifiableCredential.fromString(credentialResp.credential!!.jsonPrimitive.content)
        println(">>> Issued credential: $credential")
        credential.issuer?.id shouldBe ciTestProvider.baseUrl
        credential.credentialSubject?.id shouldBe credentialWallet.TEST_DID
        Auditor.getService().verify(credential, listOf(SignaturePolicy())).result shouldBe true

        nonce = credentialResp.cNonce ?: nonce

        println("// 5b. test deferred (asynchronous) credential issuance")
        providerMetadata.deferredCredentialEndpoint shouldNotBe null
        ciTestProvider.deferIssuance = true

        val deferredCredResp = ktorClient.post(providerMetadata.credentialEndpoint!!) {
            contentType(ContentType.Application.Json)
            bearerAuth(tokenResp.accessToken!!)
            setBody(credReq.toJSON())
        }.body<JsonObject>().let { CredentialResponse.fromJSON(it) }
        println("deferredCredResp: $deferredCredResp")

        deferredCredResp.isSuccess shouldBe true
        deferredCredResp.isDeferred shouldBe true
        deferredCredResp.acceptanceToken shouldNotBe null
        deferredCredResp.credential shouldBe null

        nonce = deferredCredResp.cNonce ?: nonce

        val deferredCredResp2 = ktorClient.post(providerMetadata.deferredCredentialEndpoint!!) {
            bearerAuth(deferredCredResp.acceptanceToken!!)
        }.body<JsonObject>().let { CredentialResponse.fromJSON(it) }
        println("deferredCredResp2: $deferredCredResp2")

        deferredCredResp2.isSuccess shouldBe true
        deferredCredResp2.isDeferred shouldBe false

        val deferredCredential = VerifiableCredential.fromString(deferredCredResp2.credential!!.jsonPrimitive.content)
        println(">>> Issued deferred credential: $deferredCredential")

        deferredCredential.issuer?.id shouldBe ciTestProvider.baseUrl
        deferredCredential.credentialSubject?.id shouldBe credentialWallet.TEST_DID
        Auditor.getService().verify(deferredCredential, listOf(SignaturePolicy())).result shouldBe true

        nonce = deferredCredResp2.cNonce ?: nonce

        println("// 5c. test batch credential issuance (with one synchronous and one deferred credential)")
        providerMetadata.batchCredentialEndpoint shouldNotBe null
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

        batchResp.isSuccess shouldBe true
        batchResp.credentialResponses!!.size shouldBe 2
        batchResp.credentialResponses!![0].isDeferred shouldBe false
        batchResp.credentialResponses!![0].credential shouldNotBe null
        batchResp.credentialResponses!![1].isDeferred shouldBe true
        batchResp.credentialResponses!![1].acceptanceToken shouldNotBe null

        val batchCred1 =
            batchResp.credentialResponses!![0].credential!!.let { VerifiableCredential.fromString(it.jsonPrimitive.content) }
        batchCred1.type.last() shouldBe "VerifiableId"
        Auditor.getService().verify(batchCred1, listOf(SignaturePolicy())).result shouldBe true
        println("batchCred1: $batchCred1")

        val batchResp2 = ktorClient.post(providerMetadata.deferredCredentialEndpoint!!) {
            bearerAuth(batchResp.credentialResponses!![1].acceptanceToken!!)
        }.body<JsonObject>().let { CredentialResponse.fromJSON(it) }
        println("batchResp2: $batchResp2")

        batchResp2.isSuccess shouldBe true
        batchResp2.isDeferred shouldBe false
        batchResp2.credential shouldNotBe null
        val batchCred2 = batchResp2.credential!!.let { VerifiableCredential.fromString(it.jsonPrimitive.content) }
        batchCred2.type.last() shouldBe "VerifiableDiploma"
        Auditor.getService().verify(batchCred2, listOf(SignaturePolicy())).result shouldBe true
    }

    @Test
    suspend fun testCredentialOfferFullAuth() {
        println("// -------- CREDENTIAL ISSUER ----------")
        println("// as CI provider, initialize credential offer for user")
        val issuanceSession = ciTestProvider.initializeCredentialOffer(
            CredentialOffer.Builder(ciTestProvider.baseUrl).addOfferedCredential("VerifiableId"),
            5.minutes, allowPreAuthorized = false
        )
        println("issuanceSession: $issuanceSession")
        issuanceSession.credentialOffer shouldNotBe null
        val offerRequest = CredentialOfferRequest(issuanceSession.credentialOffer!!)
        val offerUri = ciTestProvider.getCredentialOfferRequestUrl(offerRequest)
        println(">>> Offer URI: $offerUri")

        println("// -------- WALLET ----------")
        println("// as WALLET: receive credential offer, either being called via deeplink or by scanning QR code")
        println("// parse credential URI")
        val parsedOfferReq = CredentialOfferRequest.fromHttpParameters(Url(offerUri).parameters.toMap())
        println("parsedOfferReq: $parsedOfferReq")

        parsedOfferReq.credentialOffer shouldNotBe null
        parsedOfferReq.credentialOffer!!.credentialIssuer shouldNotBe null
        parsedOfferReq.credentialOffer!!.grants.keys shouldContainExactly setOf(GrantType.authorization_code.value)

        println("// get issuer metadata")
        val providerMetadataUri =
            credentialWallet.getCIProviderMetadataUrl(parsedOfferReq.credentialOffer!!.credentialIssuer)
        val providerMetadata = ktorClient.get(providerMetadataUri).call.body<OpenIDProviderMetadata>()
        println("providerMetadata: $providerMetadata")

        providerMetadata.credentialsSupported shouldNotBe null

        println("// resolve offered credentials")
        val offeredCredentials = parsedOfferReq.credentialOffer!!.resolveOfferedCredentials(providerMetadata)
        println("offeredCredentials: $offeredCredentials")

        offeredCredentials.size shouldBe 1
        offeredCredentials.first().format shouldBe CredentialFormat.jwt_vc_json
        offeredCredentials.first().types?.last() shouldBe "VerifiableId"
        val offeredCredential = offeredCredentials.first()
        println("offeredCredentials[0]: $offeredCredential")

        println("// go through full authorization code flow to receive offered credential")
        println("// auth request (short-cut, without pushed authorization request)")
        val authReq = AuthorizationRequest(
            ResponseType.code.name, testCIClientConfig.clientID,
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

        authResp.status shouldBe HttpStatusCode.Found
        val location = Url(authResp.headers[HttpHeaders.Location]!!)
        location.parameters.names() shouldContain ResponseType.code.name

        println("// token req")
        val tokenReq =
            TokenRequest(
                GrantType.authorization_code,
                testCIClientConfig.clientID,
                code = location.parameters[ResponseType.code.name]!!
            )
        println("tokenReq: $tokenReq")

        val tokenResp = ktorClient.submitForm(
            providerMetadata.tokenEndpoint!!,
            formParameters = parametersOf(tokenReq.toHttpParameters())
        ).body<JsonObject>().let { TokenResponse.fromJSON(it) }
        println("tokenResp: $tokenResp")

        tokenResp.isSuccess shouldBe true
        tokenResp.accessToken shouldNotBe null
        tokenResp.cNonce shouldNotBe null

        println("// receive credential")
        ciTestProvider.deferIssuance = false
        var nonce = tokenResp.cNonce!!

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

        credentialResp.isSuccess shouldBe true
        credentialResp.isDeferred shouldBe false
        credentialResp.format!! shouldBe CredentialFormat.jwt_vc_json
        credentialResp.credential.shouldBeInstanceOf<JsonPrimitive>()

        println("// parse and verify credential")
        val credential = VerifiableCredential.fromString(credentialResp.credential!!.jsonPrimitive.content)
        println(">>> Issued credential: $credential")
        credential.issuer?.id shouldBe ciTestProvider.baseUrl
        credential.credentialSubject?.id shouldBe credentialWallet.TEST_DID
        Auditor.getService().verify(credential, listOf(SignaturePolicy())).result shouldBe true
    }

    @Test
    suspend fun testPreAuthCodeFlow() {
        println("// -------- CREDENTIAL ISSUER ----------")
        println("// as CI provider, initialize credential offer for user, this time providing full offered credential object, and allowing pre-authorized code flow with user pin")
        val issuanceSession = ciTestProvider.initializeCredentialOffer(
            CredentialOffer.Builder(ciTestProvider.baseUrl)
                .addOfferedCredential(OfferedCredential.fromProviderMetadata(ciTestProvider.metadata.credentialsSupported!!.first())),
            5.minutes, allowPreAuthorized = true, preAuthUserPin = "1234"
        )
        println("issuanceSession: $issuanceSession")

        issuanceSession.credentialOffer shouldNotBe null
        issuanceSession.credentialOffer!!.credentials.first() shouldBe instanceOf<JsonObject>()

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

        parsedOfferReq.credentialOffer shouldNotBe null
        parsedOfferReq.credentialOffer!!.credentialIssuer shouldNotBe null
        parsedOfferReq.credentialOffer!!.grants.keys shouldContain GrantType.pre_authorized_code.value
        parsedOfferReq.credentialOffer!!.grants[GrantType.pre_authorized_code.value]?.preAuthorizedCode shouldNotBe null
        parsedOfferReq.credentialOffer!!.grants[GrantType.pre_authorized_code.value]?.userPinRequired shouldBe true

        println("// get issuer metadata")
        val providerMetadataUri =
            credentialWallet.getCIProviderMetadataUrl(parsedOfferReq.credentialOffer!!.credentialIssuer)
        val providerMetadata = ktorClient.get(providerMetadataUri).call.body<OpenIDProviderMetadata>()
        println("providerMetadata: $providerMetadata")

        providerMetadata.credentialsSupported shouldNotBe null

        println("// resolve offered credentials")
        val offeredCredentials = parsedOfferReq.credentialOffer!!.resolveOfferedCredentials(providerMetadata)
        println("offeredCredentials: $offeredCredentials")
        offeredCredentials.size shouldBe 1
        offeredCredentials.first().format shouldBe CredentialFormat.jwt_vc_json
        offeredCredentials.first().types?.last() shouldBe "VerifiableId"
        val offeredCredential = offeredCredentials.first()
        println("offeredCredentials[0]: $offeredCredential")

        println("// fetch access token using pre-authorized code (skipping authorization step)")
        println("// try without user PIN, should be rejected!")
        var tokenReq = TokenRequest(
            grantType = GrantType.pre_authorized_code,
            //clientId = testCIClientConfig.clientID,
            redirectUri = credentialWallet.config.redirectUri,
            preAuthorizedCode = parsedOfferReq.credentialOffer!!.grants[GrantType.pre_authorized_code.value]!!.preAuthorizedCode,
            userPin = null
        )
        println("tokenReq: $tokenReq")

        var tokenResp = ktorClient.submitForm(
            providerMetadata.tokenEndpoint!!, formParameters = parametersOf(tokenReq.toHttpParameters())
        ).body<JsonObject>().let { TokenResponse.fromJSON(it) }
        println("tokenResp: $tokenResp")

        tokenResp.isSuccess shouldBe false
        tokenResp.error shouldBe TokenErrorCode.invalid_grant.name

        println("// try with user PIN, should work:")
        tokenReq = TokenRequest(
            grantType = GrantType.pre_authorized_code,
            clientId = testCIClientConfig.clientID,
            redirectUri = credentialWallet.config.redirectUri,
            preAuthorizedCode = parsedOfferReq.credentialOffer!!.grants[GrantType.pre_authorized_code.value]!!.preAuthorizedCode,
            userPin = issuanceSession.preAuthUserPin
        )
        println("tokenReq: $tokenReq")

        tokenResp = ktorClient.submitForm(
            providerMetadata.tokenEndpoint!!, formParameters = parametersOf(tokenReq.toHttpParameters())
        ).body<JsonObject>().let { TokenResponse.fromJSON(it) }
        println("tokenResp: $tokenResp")

        tokenResp.isSuccess shouldBe true
        tokenResp.accessToken shouldNotBe null
        tokenResp.cNonce shouldNotBe null

        println("// receive credential")
        ciTestProvider.deferIssuance = false
        var nonce = tokenResp.cNonce!!

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

        credentialResp.isSuccess shouldBe true
        credentialResp.isDeferred shouldBe false
        credentialResp.format!! shouldBe CredentialFormat.jwt_vc_json
        credentialResp.credential.shouldBeInstanceOf<JsonPrimitive>()

        println("// parse and verify credential")
        val credential = VerifiableCredential.fromString(credentialResp.credential!!.jsonPrimitive.content)
        println(">>> Issued credential: $credential")
        credential.issuer?.id shouldBe ciTestProvider.baseUrl
        credential.credentialSubject?.id shouldBe credentialWallet.TEST_DID
        Auditor.getService().verify(credential, listOf(SignaturePolicy())).result shouldBe true
    }

    @Test
    suspend fun testFullAuthImplicitFlow() {
        println("// 0. get issuer metadata")
        val providerMetadata =
            ktorClient.get(ciTestProvider.getCIProviderMetadataUrl()).call.body<OpenIDProviderMetadata>()
        println("providerMetadata: $providerMetadata")

        println("// 1. send pushed authorization request with authorization details, containing info of credentials to be issued, receive session id")
        val implicitAuthReq = AuthorizationRequest(
            responseType = ResponseType.getResponseTypeString(ResponseType.token),
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
        providerMetadata.authorizationEndpoint shouldNotBe null
        val authResp = ktorClient.get(providerMetadata.authorizationEndpoint!!) {
            url {
                parameters.appendAll(parametersOf(implicitAuthReq.toHttpParameters()))
            }
        }
        println("authResp: $authResp")

        authResp.status shouldBe HttpStatusCode.Found
        authResp.headers.names() shouldContain HttpHeaders.Location.lowercase()

        val location = Url(authResp.headers[HttpHeaders.Location]!!)
        println("location: $location")
        location.toString() shouldStartWith credentialWallet.config.redirectUri!!
        location.fragment shouldNot beEmpty()

        val locationWithQueryParams = Url("http://blank?${location.fragment}")
        val tokenResp = TokenResponse.fromHttpParameters(locationWithQueryParams.parameters.toMap())
        println("tokenResp: $tokenResp")

        tokenResp.isSuccess shouldBe true
        tokenResp.accessToken shouldNotBe null
        tokenResp.cNonce shouldNotBe null

        println("// 3a. Call credential endpoint with access token, to receive credential (synchronous issuance)")
        providerMetadata.credentialEndpoint shouldNotBe null
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

        credentialResp.isSuccess shouldBe true
        credentialResp.isDeferred shouldBe false
        credentialResp.format!! shouldBe CredentialFormat.jwt_vc_json
        credentialResp.credential.shouldBeInstanceOf<JsonPrimitive>()

        val credential = VerifiableCredential.fromString(credentialResp.credential!!.jsonPrimitive.content)
        println(">>> Issued credential: $credential")

        credential.issuer?.id shouldBe ciTestProvider.baseUrl
        credential.credentialSubject?.id shouldBe credentialWallet.TEST_DID
        Auditor.getService().verify(credential, listOf(SignaturePolicy())).result shouldBe true
    }

    val issuerPortalRequest =
        "openid-credential-offer://issuer.portal.walt.id/?credential_offer=%7B%22credential_issuer%22%3A%22https%3A%2F%2Fissuer.portal.walt.id%22%2C%22credentials%22%3A%5B%7B%22format%22%3A%22jwt_vc_json%22%2C%22types%22%3A%5B%22VerifiableCredential%22%2C%22OpenBadgeCredential%22%5D%2C%22credential_definition%22%3A%7B%22%40context%22%3A%5B%22https%3A%2F%2Fwww.w3.org%2F2018%2Fcredentials%2Fv1%22%2C%22https%3A%2F%2Fw3c-ccg.github.io%2Fvc-ed%2Fplugfest-1-2022%2Fjff-vc-edu-plugfest-1-context.json%22%2C%22https%3A%2F%2Fw3id.org%2Fsecurity%2Fsuites%2Fed25519-2020%2Fv1%22%5D%2C%22types%22%3A%5B%22VerifiableCredential%22%2C%22OpenBadgeCredential%22%5D%7D%7D%5D%2C%22grants%22%3A%7B%22authorization_code%22%3A%7B%22issuer_state%22%3A%22c7228046-1a8e-4e27-a7b1-cd6479e1455f%22%7D%2C%22urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Apre-authorized_code%22%3A%7B%22pre-authorized_code%22%3A%22eyJhbGciOiJFZERTQSJ9.eyJzdWIiOiJjNzIyODA0Ni0xYThlLTRlMjctYTdiMS1jZDY0NzllMTQ1NWYiLCJpc3MiOiJodHRwczovL2lzc3Vlci5wb3J0YWwud2FsdC5pZCIsImF1ZCI6IlRPS0VOIn0.On2_7P4vr5caTHKbWv2i0a604HQ-FaiuVZHH9kzEKK7mOdVHtNHoAZADpDJtowNCkhMQxruLbnqB7WvRQzufCg%22%2C%22user_pin_required%22%3Afalse%7D%7D%7D"

    //@Test
    suspend fun testIssuerPortalRequest() {
        val credOfferReq = CredentialOfferRequest.fromHttpQueryString(Url(issuerPortalRequest).encodedQuery)
        credOfferReq.credentialOffer?.credentialIssuer shouldNotBe null
        println("// get issuer metadata")
        val providerMetadataUri =
            credentialWallet.getCIProviderMetadataUrl(credOfferReq.credentialOffer!!.credentialIssuer)
        val providerMetadata = ktorClient.get(providerMetadataUri).call.body<OpenIDProviderMetadata>()
        println("providerMetadata: $providerMetadata")
        providerMetadata.authorizationEndpoint shouldNotBe null
        println("// resolve offered credentials")
        val offeredCredentials = credOfferReq.credentialOffer!!.resolveOfferedCredentials(providerMetadata)
        println("offeredCredentials: $offeredCredentials")

        providerMetadata.grantTypesSupported shouldContain GrantType.pre_authorized_code
        credOfferReq.credentialOffer!!.grants shouldContainKey GrantType.pre_authorized_code.value

        // make token request
        var tokenReq = TokenRequest(
            grantType = GrantType.pre_authorized_code,
            //clientId = testCIClientConfig.clientID,
            redirectUri = credentialWallet.config.redirectUri,
            preAuthorizedCode = credOfferReq.credentialOffer!!.grants[GrantType.pre_authorized_code.value]!!.preAuthorizedCode,
            userPin = null
        )
        println("tokenReq: $tokenReq")
        var tokenResp = ktorClient.submitForm(
            providerMetadata.tokenEndpoint!!, formParameters = parametersOf(tokenReq.toHttpParameters())
        ).body<JsonObject>().let { TokenResponse.fromJSON(it) }
        println("tokenResp: $tokenResp")
        tokenResp.accessToken shouldNotBe null

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

        credentialResp.isSuccess shouldBe true
        credentialResp.credential shouldNotBe null
        println(credentialResp.credential!!.let { VerifiableCredential.fromString(it.jsonPrimitive.content) }.toJson())
    }

    val mattrCredentialOffer =
        "openid-credential-offer://?credential_offer=%7B%22credential_issuer%22%3A%22https%3A%2F%2Flaunchpad.vii.electron.mattrlabs.io%22%2C%22credentials%22%3A%5B%7B%22format%22%3A%22jwt_vc_json%22%2C%22types%22%3A%5B%22OpenBadgeCredential%22%5D%7D%5D%2C%22grants%22%3A%7B%22urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Apre-authorized_code%22%3A%7B%22pre-authorized_code%22%3A%22VphetImmqY-iPICjhRzGPk-QV7-TeT0wVD-sTh9rZ9k%22%7D%7D%7D"

    //@Test
    suspend fun testMattrCredentialOffer() {
        val credOfferReq = CredentialOfferRequest.fromHttpQueryString(Url(mattrCredentialOffer).encodedQuery)
        credOfferReq.credentialOffer?.credentialIssuer shouldNotBe null
        println("// get issuer metadata")
        val providerMetadataUri =
            credentialWallet.getCIProviderMetadataUrl(credOfferReq.credentialOffer!!.credentialIssuer)
        val providerMetadata =
            ktorClient.get(providerMetadataUri).call.body<JsonObject>().let { OpenIDProviderMetadata.fromJSON(it) }
        println("providerMetadata: $providerMetadata")
        providerMetadata.authorizationEndpoint shouldNotBe null
        println("// resolve offered credentials")
        val offeredCredentials = credOfferReq.credentialOffer!!.resolveOfferedCredentials(providerMetadata)
        println("offeredCredentials: $offeredCredentials")

        providerMetadata.grantTypesSupported shouldContain GrantType.pre_authorized_code
        credOfferReq.credentialOffer!!.grants shouldContainKey GrantType.pre_authorized_code.value

        // make token request
        var tokenReq = TokenRequest(
            grantType = GrantType.pre_authorized_code,
            clientId = testCIClientConfig.clientID,
            redirectUri = credentialWallet.config.redirectUri,
            preAuthorizedCode = credOfferReq.credentialOffer!!.grants[GrantType.pre_authorized_code.value]!!.preAuthorizedCode,
            userPin = null
        )
        println("tokenReq: ${tokenReq.toHttpQueryString()}")
        var tokenResp = ktorClient.submitForm(
            providerMetadata.tokenEndpoint!!, formParameters = parametersOf(tokenReq.toHttpParameters())
        ).body<JsonObject>().let { TokenResponse.fromJSON(it) }
        println("tokenResp: $tokenResp")
        tokenResp.accessToken shouldNotBe null

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

        credentialResp.isSuccess shouldBe true
        credentialResp.credential shouldNotBe null
        println(credentialResp.credential!!.let { VerifiableCredential.fromString(it.jsonPrimitive.content) }.toJson())
    }

    val spheronCredOffer =
        "openid-credential-offer://?credential_offer=%7B%22grants%22%3A%7B%22urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Apre-authorized_code%22%3A%7B%22pre-authorized_code%22%3A%221P8XydcKW1Gy5y7e1u25mM%22%2C%22user_pin_required%22%3Afalse%7D%7D%2C%22credentials%22%3A%5B%22OpenBadgeCredential%22%5D%2C%22credential_issuer%22%3A%22https%3A%2F%2Fssi.sphereon.com%2Fpf3%22%7D"

    //@Test
    suspend fun parseSpheronCredOffer() {
        val credOfferReq = CredentialOfferRequest.fromHttpQueryString(Url(spheronCredOffer).encodedQuery)
        credOfferReq.credentialOffer shouldNotBe null
        val providerMetadataUri =
            credentialWallet.getCIProviderMetadataUrl(credOfferReq.credentialOffer!!.credentialIssuer)
        val providerMetadata =
            ktorClient.get(providerMetadataUri).call.body<JsonObject>().let { OpenIDProviderMetadata.fromJSON(it) }
        println("providerMetadata: $providerMetadata")
        providerMetadata.tokenEndpoint shouldNotBe null
        providerMetadata.credentialEndpoint shouldNotBe null
        println("// resolve offered credentials")
        val offeredCredentials = credOfferReq.credentialOffer!!.resolveOfferedCredentials(providerMetadata)
        println("offeredCredentials: $offeredCredentials")

        // make token request
        var tokenReq = TokenRequest(
            grantType = GrantType.pre_authorized_code,
            clientId = testCIClientConfig.clientID,
            redirectUri = credentialWallet.config.redirectUri,
            preAuthorizedCode = credOfferReq.credentialOffer!!.grants[GrantType.pre_authorized_code.value]!!.preAuthorizedCode,
            userPin = null
        )
        println("tokenReq: ${tokenReq.toHttpQueryString()}")
        var tokenResp = ktorClient.submitForm(
            providerMetadata.tokenEndpoint!!, formParameters = parametersOf(tokenReq.toHttpParameters())
        ).body<JsonObject>().let { TokenResponse.fromJSON(it) }
        println("tokenResp: $tokenResp")
        tokenResp.accessToken shouldNotBe null

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

        credentialResp.isSuccess shouldBe true
        credentialResp.credential shouldNotBe null
        println(credentialResp.credential!!.let { VerifiableCredential.fromString(it.jsonPrimitive.content) }.toJson())
    }

    // issuance by reference

    @Test
    suspend fun testCredentialOfferByReference() {
        println("// -------- CREDENTIAL ISSUER ----------")
        println("// as CI provider, initialize credential offer for user, this time providing full offered credential object, and allowing pre-authorized code flow with user pin")
        val issuanceSession = ciTestProvider.initializeCredentialOffer(
            CredentialOffer.Builder(ciTestProvider.baseUrl)
                .addOfferedCredential(OfferedCredential.fromProviderMetadata(ciTestProvider.metadata.credentialsSupported!!.first())),
            5.minutes, allowPreAuthorized = true, preAuthUserPin = "1234"
        )
        println("issuanceSession: $issuanceSession")

        issuanceSession.credentialOffer shouldNotBe null
        issuanceSession.credentialOffer!!.credentials.first() shouldBe instanceOf<JsonObject>()

        val offerRequest = ciTestProvider.getCredentialOfferRequest(issuanceSession, byReference = true)
        println("offerRequest: $offerRequest")
        offerRequest.credentialOffer shouldBe null
        offerRequest.credentialOfferUri shouldNotBe null

        println("// create credential offer request url (this time cross-device)")
        val offerUri = ciTestProvider.getCredentialOfferRequestUrl(offerRequest)
        println("Offer URI: $offerUri")

        println("// -------- WALLET ----------")
        println("// as WALLET: receive credential offer, either being called via deeplink or by scanning QR code")
        println("// parse credential URI")

        val credentialOffer = credentialWallet.getCredentialOffer(CredentialOfferRequest.fromHttpParameters(Url(offerUri).parameters.toMap()))

        credentialOffer.credentialIssuer shouldNotBe null
        credentialOffer.grants.keys shouldContain GrantType.pre_authorized_code.value
        credentialOffer.grants[GrantType.pre_authorized_code.value]?.preAuthorizedCode shouldNotBe null
        credentialOffer.grants[GrantType.pre_authorized_code.value]?.userPinRequired shouldBe true

        println("// get issuer metadata")
        val providerMetadataUri =
            credentialWallet.getCIProviderMetadataUrl(credentialOffer.credentialIssuer)
        val providerMetadata = ktorClient.get(providerMetadataUri).call.body<OpenIDProviderMetadata>()
        println("providerMetadata: $providerMetadata")

        providerMetadata.credentialsSupported shouldNotBe null

        println("// resolve offered credentials")
        val offeredCredentials = credentialOffer.resolveOfferedCredentials(providerMetadata)
        println("offeredCredentials: $offeredCredentials")
        offeredCredentials.size shouldBe 1
        offeredCredentials.first().format shouldBe CredentialFormat.jwt_vc_json
        offeredCredentials.first().types?.last() shouldBe "VerifiableId"
        val offeredCredential = offeredCredentials.first()
        println("offeredCredentials[0]: $offeredCredential")

        println("// fetch access token using pre-authorized code (skipping authorization step)")
        println("// try without user PIN, should be rejected!")
        var tokenReq = TokenRequest(
            grantType = GrantType.pre_authorized_code,
            //clientId = testCIClientConfig.clientID,
            redirectUri = credentialWallet.config.redirectUri,
            preAuthorizedCode = credentialOffer.grants[GrantType.pre_authorized_code.value]!!.preAuthorizedCode,
            userPin = null
        )
        println("tokenReq: $tokenReq")

        var tokenResp = ktorClient.submitForm(
            providerMetadata.tokenEndpoint!!, formParameters = parametersOf(tokenReq.toHttpParameters())
        ).body<JsonObject>().let { TokenResponse.fromJSON(it) }
        println("tokenResp: $tokenResp")

        tokenResp.isSuccess shouldBe false
        tokenResp.error shouldBe TokenErrorCode.invalid_grant.name

        println("// try with user PIN, should work:")
        tokenReq = TokenRequest(
            grantType = GrantType.pre_authorized_code,
            clientId = testCIClientConfig.clientID,
            redirectUri = credentialWallet.config.redirectUri,
            preAuthorizedCode = credentialOffer.grants[GrantType.pre_authorized_code.value]!!.preAuthorizedCode,
            userPin = issuanceSession.preAuthUserPin
        )
        println("tokenReq: $tokenReq")

        tokenResp = ktorClient.submitForm(
            providerMetadata.tokenEndpoint!!, formParameters = parametersOf(tokenReq.toHttpParameters())
        ).body<JsonObject>().let { TokenResponse.fromJSON(it) }
        println("tokenResp: $tokenResp")

        tokenResp.isSuccess shouldBe true
        tokenResp.accessToken shouldNotBe null
        tokenResp.cNonce shouldNotBe null

        println("// receive credential")
        ciTestProvider.deferIssuance = false
        var nonce = tokenResp.cNonce!!

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

        credentialResp.isSuccess shouldBe true
        credentialResp.isDeferred shouldBe false
        credentialResp.format!! shouldBe CredentialFormat.jwt_vc_json
        credentialResp.credential.shouldBeInstanceOf<JsonPrimitive>()

        println("// parse and verify credential")
        val credential = VerifiableCredential.fromString(credentialResp.credential!!.jsonPrimitive.content)
        println(">>> Issued credential: $credential")
        credential.issuer?.id shouldBe ciTestProvider.baseUrl
        credential.credentialSubject?.id shouldBe credentialWallet.TEST_DID
        Auditor.getService().verify(credential, listOf(SignaturePolicy())).result shouldBe true
    }
}
