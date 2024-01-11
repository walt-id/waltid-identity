package id.walt.oid4vc

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.crypto.Ed25519Signer
import com.nimbusds.jose.crypto.Ed25519Verifier
import com.nimbusds.jose.crypto.bc.BouncyCastleProviderSingleton
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.OctetKeyPair
import com.nimbusds.jose.util.Base64
import com.nimbusds.jwt.JWTParser
import id.walt.credentials.vc.vcs.W3CVC
import id.walt.credentials.verification.policies.JwtSignaturePolicy
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.LocalKey
import id.walt.crypto.keys.LocalKeyCreator
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.did.dids.DidService
import id.walt.did.dids.registrar.dids.DidIonCreateOptions
import id.walt.did.dids.registrar.dids.DidJwkCreateOptions
import id.walt.did.dids.registrar.dids.DidKeyCreateOptions
import id.walt.did.dids.registrar.dids.DidWebCreateOptions
import id.walt.oid4vc.data.*
import id.walt.oid4vc.definitions.OPENID_CREDENTIAL_AUTHORIZATION_TYPE
import id.walt.oid4vc.providers.CredentialWalletConfig
import id.walt.oid4vc.providers.OpenIDClientConfig
import id.walt.oid4vc.requests.*
import id.walt.oid4vc.responses.*
import id.walt.sdjwt.*
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.common.runBlocking
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
import io.ktor.utils.io.*
import korlibs.crypto.SHA
import korlibs.crypto.SHA256
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.*
import kotlinx.uuid.UUID
import kotlinx.uuid.generateUUID
import java.io.File
import kotlin.test.Ignore
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
        runBlocking {
            DidService.init()
            DidService.registrarMethods.keys shouldContain "ion"
        }
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
        credentialSupported.toJSONString() shouldEqualJson credentialSupportedJson
    }

    @Test
    fun testOIDProviderMetadata() {
        val metadataJson = testMetadata.toJSONString()
        println("metadataJson: $metadataJson")
        val metadataParsed = OpenIDProviderMetadata.fromJSONString(metadataJson)
        metadataParsed.toJSONString() shouldEqualJson metadataJson
        println("metadataParsed: $metadataParsed")
    }

    @Test
    suspend fun testFetchAndParseMetadata() {
        val response = ktorClient.get("${CI_PROVIDER_BASE_URL}/.well-known/openid-configuration")
        println("response: $response")
        response.status shouldBe HttpStatusCode.OK
        val respText = response.bodyAsText()
        val metadata: OpenIDProviderMetadata = OpenIDProviderMetadata.fromJSONString(respText)
        println("metadata: $metadata")
        metadata.toJSONString() shouldEqualJson ciTestProvider.metadata.toJSONString()
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

    fun verifyIssuerAndSubjectId(credential: JsonObject, issuerId: String, subjectId: String): Boolean {
        return (credential["issuer"]?.jsonPrimitive?.contentOrNull?.equals(issuerId) ?: false) &&
        credential["credentialSubject"]?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull?.equals(subjectId) ?: false
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
        val credential = credentialResp.credential!!.jsonPrimitive.content
        println(">>> Issued credential: $credential")
        //credential.issuer?.id shouldBe ciTestProvider.baseUrl
        //credential.credentialSubject?.id shouldBe credentialWallet.TEST_DID
        JwtSignaturePolicy().verify(credential, null, mapOf()).isSuccess shouldBe true
        //Auditor.getService().verify(credential, listOf(SignaturePolicy())).result shouldBe true

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

        val deferredCredential = deferredCredResp2.credential!!.jsonPrimitive.content
        println(">>> Issued deferred credential: $deferredCredential")

        verifyIssuerAndSubjectId(SDJwt.parse(deferredCredential).fullPayload.get("vc")?.jsonObject!!,
            ciTestProvider.CI_ISSUER_DID, credentialWallet.TEST_DID) shouldBe true
        JwtSignaturePolicy().verify(deferredCredential, null, mapOf()).isSuccess shouldBe true

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
            batchResp.credentialResponses!![0].credential!!.jsonPrimitive.content
        SDJwt.parse(batchCred1).fullPayload.get("vc")?.jsonObject!!["type"]?.jsonArray?.last()?.jsonPrimitive?.contentOrNull shouldBe "VerifiableId"
        JwtSignaturePolicy().verify(batchCred1, null, mapOf()).isSuccess shouldBe true
        println("batchCred1: $batchCred1")

        val batchResp2 = ktorClient.post(providerMetadata.deferredCredentialEndpoint!!) {
            bearerAuth(batchResp.credentialResponses!![1].acceptanceToken!!)
        }.body<JsonObject>().let { CredentialResponse.fromJSON(it) }
        println("batchResp2: $batchResp2")

        batchResp2.isSuccess shouldBe true
        batchResp2.isDeferred shouldBe false
        batchResp2.credential shouldNotBe null
        val batchCred2 = batchResp2.credential!!.jsonPrimitive.content
        SDJwt.parse(batchCred2).fullPayload.get("vc")?.jsonObject!!["type"]?.jsonArray?.last()?.jsonPrimitive?.contentOrNull shouldBe "VerifiableDiploma"
        JwtSignaturePolicy().verify(batchCred2, null, mapOf()).isSuccess shouldBe true
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
        val credential = credentialResp.credential!!.jsonPrimitive.content
        println(">>> Issued credential: $credential")
        verifyIssuerAndSubjectId(SDJwt.parse(credential).fullPayload.get("vc")?.jsonObject!!,
            ciTestProvider.CI_ISSUER_DID, credentialWallet.TEST_DID) shouldBe  true
        JwtSignaturePolicy().verify(credential, null, mapOf()).isSuccess shouldBe true
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
        val credential = credentialResp.credential!!.jsonPrimitive.content
        println(">>> Issued credential: $credential")

        verifyIssuerAndSubjectId(SDJwt.parse(credential).fullPayload.get("vc")?.jsonObject!!,
            ciTestProvider.CI_ISSUER_DID, credentialWallet.TEST_DID) shouldBe  true
        JwtSignaturePolicy().verify(credential, null, mapOf()).isSuccess shouldBe true
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

        val credential = credentialResp.credential!!.jsonPrimitive.content
        println(">>> Issued credential: $credential")

        verifyIssuerAndSubjectId(SDJwt.parse(credential).fullPayload.get("vc")?.jsonObject!!,
            ciTestProvider.CI_ISSUER_DID, credentialWallet.TEST_DID) shouldBe  true
        JwtSignaturePolicy().verify(credential, null, mapOf()).isSuccess shouldBe true
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
        println(SDJwt.parse(credentialResp.credential!!.jsonPrimitive.content).fullPayload.toString())
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
        println(SDJwt.parse(credentialResp.credential!!.jsonPrimitive.content).fullPayload.toString())
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
        println(SDJwt.parse(credentialResp.credential!!.jsonPrimitive.content).fullPayload.toString())
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

        val credentialOffer =
            credentialWallet.resolveCredentialOffer(CredentialOfferRequest.fromHttpParameters(Url(offerUri).parameters.toMap()))

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
        val credential = credentialResp.credential!!.jsonPrimitive.content
        println(">>> Issued credential: $credential")
        verifyIssuerAndSubjectId(SDJwt.parse(credential).fullPayload.get("vc")?.jsonObject!!,
            ciTestProvider.CI_ISSUER_DID, credentialWallet.TEST_DID) shouldBe  true
        JwtSignaturePolicy().verify(credential, null, mapOf()).isSuccess shouldBe true
    }


    val http = HttpClient(Java) {
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
    val ms_id_token = "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsImtpZCI6IlQxU3QtZExUdnlXUmd4Ql82NzZ1OGtyWFMtSSJ9.eyJhdWQiOiJlNTBjZWFhNi04NTU0LTRhZTYtYmZkZi1mZDk1ZTIyNDNhZTAiLCJpc3MiOiJodHRwczovL2xvZ2luLm1pY3Jvc29mdG9ubGluZS5jb20vOGJjOTU1ZDktMzhmZC00YzE1LWE1MjAtMGM2NTY0MDc1MzdhL3YyLjAiLCJpYXQiOjE3MDMwMDExMjQsIm5iZiI6MTcwMzAwMTEyNCwiZXhwIjoxNzAzMDA1MDI0LCJhaW8iOiJBVVFBdS84VkFBQUF0ZTdkTWtZcFN2WWhwaUxpVmluSXF4V1hJREhXb1FoRnpUZVAwc0RLRGxiTWtRT0ZtRzJqckwxQ0dlVXlzTDlyVEg2emhPOTBJenJ3VExFbWc3elBJUT09IiwiY2MiOiJDZ0VBRWlSelpYWmxjbWx1YzNSaGJYQnNaWEpuYldGcGJDNXZibTFwWTNKdmMyOW1kQzVqYjIwYUVnb1FoY0UxZmwvS1lFMmJZT0c5R1FZN2VTSVNDaEJ6TVltQTdXQTFTNFNubmxyY3RXNEFNZ0pGVlRnQSIsIm5vbmNlIjoiNjc4OTEwIiwicmgiOiIwLkFYa0EyVlhKaV8wNEZVeWxJQXhsWkFkVGVxYnFET1ZVaGVaS3Y5XzlsZUlrT3VDVUFGVS4iLCJzdWIiOiI0cDgyb3hySGhiZ2x4V01oTDBIUmpKbDNRTjZ2eDhMS1pQWkVyLW9wako0IiwidGlkIjoiOGJjOTU1ZDktMzhmZC00YzE1LWE1MjAtMGM2NTY0MDc1MzdhIiwidXRpIjoiY3pHSmdPMWdOVXVFcDU1YTNMVnVBQSIsInZlciI6IjIuMCJ9.DE9LEsmzx9BG0z4Q7d-g_CH8ach4-cm7yztGHuHJykdLCjznu131nRsOFc9HdnIIqzHUX8kj1ZtAlPMLRaDYVYasKomRO4Fx7GCLY6kG5szQZJ8t8hkwX4O_zk7IaDHtn4HiyfwfSPwZjknMiQpTyiAqUqt0tR8ojSf5VeKnQmChvmp0w86izNYwTmWx5OOx2FXLsDEmvF42mp96bSsvyQt6hn4FcmhYkE4nf_5nHssb3SsL485ppHjWOvj81nGanK_u4iKVkfY_9KFF98hOwtWEi1UyvlTo5CdyYkehV0ZVs4gFAKiV7L5uasI-MYIlg0kUEK-mtMjHhU9TWIa4SA"
    // "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsImtpZCI6IlQxU3QtZExUdnlXUmd4Ql82NzZ1OGtyWFMtSSJ9.eyJhdWQiOiJlNTBjZWFhNi04NTU0LTRhZTYtYmZkZi1mZDk1ZTIyNDNhZTAiLCJpc3MiOiJodHRwczovL2xvZ2luLm1pY3Jvc29mdG9ubGluZS5jb20vOGJjOTU1ZDktMzhmZC00YzE1LWE1MjAtMGM2NTY0MDc1MzdhL3YyLjAiLCJpYXQiOjE3MDI4OTYzMDIsIm5iZiI6MTcwMjg5NjMwMiwiZXhwIjoxNzAyOTAwMjAyLCJhaW8iOiJBYlFBUy84VkFBQUE2QlpyS1IvZkJpaHlwalFHRy9hcDJpNXJxVzBBSDY1TDVYVFI1K0VZRTRqNGhIL2RUdStheGFrRUFwd2RLU0RJN1ZKck01bXN6SEx4YklYTE5uRS9uaHVxZWZRcHJaeDF5ZGR1Z1JVN0NjcU4wdzFyZzlZTUlHRkFpeGtVV2N4cllhbmhEbmNqTlFLVnloYThKNXlVTHd2MW85MXdYQzFFZS9aQmo3dWtBczBORXdZeTd2K2JRQnpkS250Y3BaY1NWZWhlY2xVV0NkWEJUVnZpZzdqTjVRVHEzc01QSHJGUWtxMERZQjRTU3l3PSIsImlkcCI6Imh0dHBzOi8vc3RzLndpbmRvd3MubmV0LzkxODgwNDBkLTZjNjctNGM1Yi1iMTEyLTM2YTMwNGI2NmRhZC8iLCJub25jZSI6IjY3ODkxMCIsInByb3ZfZGF0YSI6W3siYXQiOnRydWUsInByb3YiOiJzYW1zdW5nLmNvbSIsImFsdHNlY2lkIjoiZDhkN2dTOVVGRUVORU1uUTZ5SmNKZWxLX2IxX3VhN0V4QXR6Z0F6WFdmUSJ9XSwicmgiOiIwLkFYa0EyVlhKaV8wNEZVeWxJQXhsWkFkVGVxYnFET1ZVaGVaS3Y5XzlsZUlrT3VDVUFCTS4iLCJzdWIiOiJobXd2QklDT0p2TEFZU2ktRW1PZ0ZXaF95ODFVRXBuRGNpa3d0dlBVdU5NIiwidGlkIjoiOGJjOTU1ZDktMzhmZC00YzE1LWE1MjAtMGM2NTY0MDc1MzdhIiwidXRpIjoiSFh3YXRnT0tOMHVVRXdGaWpnSjRBQSIsInZlciI6IjIuMCJ9.kXoMA1Y-KdL8Z_Guq5Jzq-6zhrKECdVm6uDOFeRr39oegCeFYA8FJG2fesmQq5q5MWBWcETAp6Ovyx6SmSVQIicWE8PhH2aD40NsIEq-rXkovaqNimhZzkuuwqh0LlIDBbE_l3qtIkfXaUYS2UE029ggmX16Ek0rrs6JunD3MAO_Y7K4kZrSKRjozrbBv_NN1xZPp51RC5PuU9Lb6aacXPgTJaImvA31aNwSbAJohqdZlgX6vwakRaZFQWVtIaTEeedzqOump8wyNqSkSOTJLZLehWgmPF7cSLUZ0hhsiZUH0BPby_X8dvpwVjs6155jBIFo5iFJsBgxFJRu0VO71Q"

    val TEST_WALLET_KEY = "{\"kty\":\"EC\",\"d\":\"uD-uxub011cplvr5Bd6MrIPSEUBsgLk-C1y3tnmfetQ\",\"use\":\"sig\",\"crv\":\"secp256k1\",\"kid\":\"48d8a34263cf492aa7ff61b6183e8bcf\",\"x\":\"TKaQ6sCocTDsmuj9tTR996tFXpEcS2EJN-1gOadaBvk\",\"y\":\"0TrIYHcfC93VpEuvj-HXTnyKt0snayOMwGSJA1XiDX8\"}"
    val TEST_WALLET_DID_ION = "did:ion:EiDh0EL8wg8oF-7rRiRzEZVfsJvh4sQX4Jock2Kp4j_zxg:eyJkZWx0YSI6eyJwYXRjaGVzIjpbeyJhY3Rpb24iOiJyZXBsYWNlIiwiZG9jdW1lbnQiOnsicHVibGljS2V5cyI6W3siaWQiOiI0OGQ4YTM0MjYzY2Y0OTJhYTdmZjYxYjYxODNlOGJjZiIsInB1YmxpY0tleUp3ayI6eyJjcnYiOiJzZWNwMjU2azEiLCJraWQiOiI0OGQ4YTM0MjYzY2Y0OTJhYTdmZjYxYjYxODNlOGJjZiIsImt0eSI6IkVDIiwidXNlIjoic2lnIiwieCI6IlRLYVE2c0NvY1REc211ajl0VFI5OTZ0RlhwRWNTMkVKTi0xZ09hZGFCdmsiLCJ5IjoiMFRySVlIY2ZDOTNWcEV1dmotSFhUbnlLdDBzbmF5T013R1NKQTFYaURYOCJ9LCJwdXJwb3NlcyI6WyJhdXRoZW50aWNhdGlvbiJdLCJ0eXBlIjoiRWNkc2FTZWNwMjU2azFWZXJpZmljYXRpb25LZXkyMDE5In1dfX1dLCJ1cGRhdGVDb21taXRtZW50IjoiRWlCQnlkZ2R5WHZkVERob3ZsWWItQkV2R3ExQnR2TWJSLURmbDctSHdZMUhUZyJ9LCJzdWZmaXhEYXRhIjp7ImRlbHRhSGFzaCI6IkVpRGJxa05ldzdUcDU2cEJET3p6REc5bThPZndxamlXRjI3bTg2d1k3TS11M1EiLCJyZWNvdmVyeUNvbW1pdG1lbnQiOiJFaUFGOXkzcE1lQ2RQSmZRYjk1ZVV5TVlfaUdCRkMwdkQzeDNKVTB6V0VjWUtBIn19"
    val TEST_WALLET_DID_WEB = "did:web:entra.walt.id:holder"
    val TEST_WALLET_DID_JWK = "did:jwk:eyJrdHkiOiJFQyIsInVzZSI6InNpZyIsImNydiI6InNlY3AyNTZrMSIsImtpZCI6IjQ4ZDhhMzQyNjNjZjQ5MmFhN2ZmNjFiNjE4M2U4YmNmIiwieCI6IlRLYVE2c0NvY1REc211ajl0VFI5OTZ0RlhwRWNTMkVKTi0xZ09hZGFCdmsiLCJ5IjoiMFRySVlIY2ZDOTNWcEV1dmotSFhUbnlLdDBzbmF5T013R1NKQTFYaURYOCJ9"
    val TEST_WALLET_DID_KEY = "did:key:zdCru39GRVTj7Y6gKRbT9axbErpR9xAq9GmQkBz1DwnYxpMv4GsjWxZM3anG94V4PsTg12RDWt7Ss7dN2SDBd2A948UKMDxTD8oxRPujyyZ9Fcvv2saXeyp41jst"
    val TEST_WALLET_DID = TEST_WALLET_DID_WEB

    //@Test
    suspend fun createDidWebDoc() {
        val pubKey = LocalKey.importJWK(TEST_WALLET_KEY).getOrThrow().getPublicKey()
        val didWebResult = DidService.registerByKey(
            "web",
            pubKey,
            DidWebCreateOptions("entra.walt.id", "holder", KeyType.secp256k1))
        println(didWebResult.didDocument)
        // NEED TO UPDATE did document, so that verificationMethod id #0 equals ids of other methods!

        val didJwkResult = DidService.registerByKey("jwk", pubKey, DidJwkCreateOptions(KeyType.secp256k1))
        println(didJwkResult.did)
        val didKeyResult = DidService.registerByKey("key", pubKey, DidKeyCreateOptions(KeyType.secp256k1))
        println(didKeyResult.did)
    }

    //@Test
    suspend fun testEntraIssuance() {
        val pin: String? = null //"0288"
        // ============ Issuer ========================
        val accessToken = entraAuthorize()
        val createIssuanceReq = "{\n" +
            "    \"callback\": {\n" +
            "        \"url\": \"https://9ffd-62-178-27-231.ngrok-free.app\",\n" +
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
            "   }," } ?: "") +
            "   \"claims\": { \n" +
            "       \"given_name\": \"Sev\",\n" +
            "       \"family_name\": \"Sta\"\n" +
            "   }\n" +
            "}"

        val response = http.post("https://verifiedid.did.msidentity.com/v1.0/verifiableCredentials/createIssuanceRequest") {
            header(HttpHeaders.Authorization, accessToken)
            contentType(ContentType.Application.Json)
            setBody(createIssuanceReq)
        }
        response.status shouldBe HttpStatusCode.Created
        val responseObj = response.body<JsonObject>()
        val url = responseObj["url"]?.jsonPrimitive?.content
        //val url = "openid-vc://?request_uri=https://verifiedid.did.msidentity.com/v1.0/tenants/37a99dab-212b-44d9-9b49-7756cb4dd915/verifiableCredentials/issuanceRequests/67e271be-be8b-42f8-9cb9-1b57ee010e41"
        println(url)
        return
        //val url = "openid-vc://?request_uri=https://verifiedid.did.msidentity.com/v1.0/tenants/3c32ed40-8a10-465b-8ba4-0b1e86882668/verifiableCredentials/issuanceRequests/a7e5db5b-2fba-4d02-bc0d-21ee82191386"
        // ============ Wallet ========================
        // Load key:
        val testWalletKey = LocalKey.importJWK(TEST_WALLET_KEY).getOrThrow()
        testWalletKey.hasPrivateKey shouldBe true

        // * parse issuance request
        val reqParams = parseQueryString(Url(url!!).encodedQuery).toMap()
        val authReq = AuthorizationRequest.fromHttpParametersAuto(reqParams)

        // * detect that this is an MS Entra issuance request and trigger custom protocol flow!
        val entraIssuanceRequest = EntraIssuanceRequest.fromAuthorizationRequest(authReq)

        // * Create response JWT token, signed by key for holder DID
        //credentialWallet.
        val responseTokenPayload = SDPayload.createSDPayload(
            entraIssuanceRequest.getResponseObject(testWalletKey.getThumbprint(), TEST_WALLET_DID, testWalletKey.getPublicKey().jwk!!, pin),
            SDMap.fromJSON("{}")
        )
        val jwtCryptoProvider = runBlocking {
            val key = ECKey.parse(TEST_WALLET_KEY)
            SimpleJWTCryptoProvider(JWSAlgorithm.ES256K, ECDSASigner(key).apply {
                jcaContext.provider = BouncyCastleProviderSingleton.getInstance()
            }, ECDSAVerifier(key.toPublicJWK()).apply {
                jcaContext.provider = BouncyCastleProviderSingleton.getInstance()
            })
        }
        val responseToken = SDJwt.sign(responseTokenPayload, jwtCryptoProvider, TEST_WALLET_DID + "#${testWalletKey.getKeyId()}").toString()

        // * POST response JWT token to return address found in manifest
//        val issuerReturnAddress = "https://beta.did.msidentity.com/v1.0/tenants/3c32ed40-8a10-465b-8ba4-0b1e86882668/verifiableCredentials/issue"
//        val responseToken = "eyJraWQiOiJkaWQ6aW9uOkVpRGgwRUw4d2c4b0YtN3JSaVJ6RVpWZnNKdmg0c1FYNEpvY2syS3A0al96eGc6ZXlKa1pXeDBZU0k2ZXlKd1lYUmphR1Z6SWpwYmV5SmhZM1JwYjI0aU9pSnlaWEJzWVdObElpd2laRzlqZFcxbGJuUWlPbnNpY0hWaWJHbGpTMlY1Y3lJNlczc2lhV1FpT2lJME9HUTRZVE0wTWpZelkyWTBPVEpoWVRkbVpqWXhZall4T0RObE9HSmpaaUlzSW5CMVlteHBZMHRsZVVwM2F5STZleUpqY25ZaU9pSnpaV053TWpVMmF6RWlMQ0pyYVdRaU9pSTBPR1E0WVRNME1qWXpZMlkwT1RKaFlUZG1aall4WWpZeE9ETmxPR0pqWmlJc0ltdDBlU0k2SWtWRElpd2lkWE5sSWpvaWMybG5JaXdpZUNJNklsUkxZVkUyYzBOdlkxUkVjMjExYWpsMFZGSTVPVFowUmxod1JXTlRNa1ZLVGkweFowOWhaR0ZDZG1zaUxDSjVJam9pTUZSeVNWbElZMlpET1ROV2NFVjFkbW90U0ZoVWJubExkREJ6Ym1GNVQwMTNSMU5LUVRGWWFVUllPQ0o5TENKd2RYSndiM05sY3lJNld5SmhkWFJvWlc1MGFXTmhkR2x2YmlKZExDSjBlWEJsSWpvaVJXTmtjMkZUWldOd01qVTJhekZXWlhKcFptbGpZWFJwYjI1TFpYa3lNREU1SW4xZGZYMWRMQ0oxY0dSaGRHVkRiMjF0YVhSdFpXNTBJam9pUldsQ1FubGtaMlI1V0haa1ZFUm9iM1pzV1dJdFFrVjJSM0V4UW5SMlRXSlNMVVJtYkRjdFNIZFpNVWhVWnlKOUxDSnpkV1ptYVhoRVlYUmhJanA3SW1SbGJIUmhTR0Z6YUNJNklrVnBSR0p4YTA1bGR6ZFVjRFUyY0VKRVQzcDZSRWM1YlRoUFpuZHhhbWxYUmpJM2JUZzJkMWszVFMxMU0xRWlMQ0p5WldOdmRtVnllVU52YlcxcGRHMWxiblFpT2lKRmFVRkdPWGt6Y0UxbFEyUlFTbVpSWWprMVpWVjVUVmxmYVVkQ1JrTXdka1F6ZUROS1ZUQjZWMFZqV1V0QkluMTkjNDhkOGEzNDI2M2NmNDkyYWE3ZmY2MWI2MTgzZThiY2YiLCJ0eXAiOiJKV1QiLCJhbGciOiJFUzI1NksifQ.eyJzdWIiOiJKb1I1T1hicGVldlRpeVM4S0Zma2NSN3NlMGMwSVhLbGU0REk1YlVXTncwIiwiYXVkIjoiaHR0cHM6Ly9iZXRhLmRpZC5tc2lkZW50aXR5LmNvbS92MS4wL3RlbmFudHMvM2MzMmVkNDAtOGExMC00NjViLThiYTQtMGIxZTg2ODgyNjY4L3ZlcmlmaWFibGVDcmVkZW50aWFscy9pc3N1ZSIsInN1Yl9qd2siOnsia3R5IjoiRUMiLCJraWQiOiI0OGQ4YTM0MjYzY2Y0OTJhYTdmZjYxYjYxODNlOGJjZiIsInVzZSI6InNpZyIsImNydiI6InNlY3AyNTZrMSIsIngiOiJUS2FRNnNDb2NURHNtdWo5dFRSOTk2dEZYcEVjUzJFSk4tMWdPYWRhQnZrIiwieSI6IjBUcklZSGNmQzkzVnBFdXZqLUhYVG55S3Qwc25heU9Nd0dTSkExWGlEWDgifSwiaWF0IjoxNzAzMTAzNDg4LCJleHAiOjE3MDMxMDcwNzIsImp0aSI6IjkwYjkzMThlLWVkMGEtNGMzZC1hMGJiLTg2OTYwOGE3OWYzNCIsImlzcyI6Imh0dHBzOi8vc2VsZi1pc3N1ZWQubWUiLCJwaW4iOiJTQ2NVaHo1MThscWZxbVFQVkpIVVdaOU9scTVVeUZPL2dQYmNOaW93cTNNPSIsImNvbnRyYWN0IjoiaHR0cHM6Ly92ZXJpZmllZGlkLmRpZC5tc2lkZW50aXR5LmNvbS92MS4wL3RlbmFudHMvM2MzMmVkNDAtOGExMC00NjViLThiYTQtMGIxZTg2ODgyNjY4L3ZlcmlmaWFibGVDcmVkZW50aWFscy9jb250cmFjdHMvMDVkN2JhNTctZjRlNi0yNjBjLWNjYjYtMGNkNzQxNzk3NzljL21hbmlmZXN0IiwiYXR0ZXN0YXRpb25zIjp7ImlkVG9rZW5zIjp7Imh0dHBzOi8vc2VsZi1pc3N1ZWQubWUiOiJleUpoYkdjaU9pSkZVekkxTmtzaUxDSnJhV1FpT2lKa2FXUTZkMlZpT21ScFpDNTNiMjlrWjNKdmRtVmtaVzF2TG1OdmJTTTVZMlZoTVRVeU5XWmtOV1kwWkRKak9URTROMlF6TXpBMU9HRTNaVFExT1haalUybG5ibWx1WjB0bGVTMDJaR1V3T1NJc0luUjVjQ0k2SWtwWFZDSjkuZXlKemRXSWlPaUozYkVOeGRYQnhaWGt3Y2xoeGNtdHVSbE5oVW1wQk56QkNNMHRUT1dST05WQllXVjluUXkxS2FuWmpJaXdpWVhWa0lqb2lhSFIwY0hNNkx5OWlaWFJoTG1ScFpDNXRjMmxrWlc1MGFYUjVMbU52YlM5Mk1TNHdMM1JsYm1GdWRITXZNMk16TW1Wa05EQXRPR0V4TUMwME5qVmlMVGhpWVRRdE1HSXhaVGcyT0RneU5qWTRMM1psY21sbWFXRmliR1ZEY21Wa1pXNTBhV0ZzY3k5cGMzTjFaU0lzSW01dmJtTmxJam9pU2tSRk5FVktOa1Y0WWpWQ1VFNDVOMm8zTVZWSFFUMDlJaXdpYzNWaVgycDNheUk2ZXlKamNuWWlPaUp6WldOd01qVTJhekVpTENKcmFXUWlPaUprYVdRNmQyVmlPbVJwWkM1M2IyOWtaM0p2ZG1Wa1pXMXZMbU52YlNNNVkyVmhNVFV5Tldaa05XWTBaREpqT1RFNE4yUXpNekExT0dFM1pUUTFPWFpqVTJsbmJtbHVaMHRsZVMwMlpHVXdPU0lzSW10MGVTSTZJa1ZESWl3aWVDSTZJa3RNYmpWRmFuZFlaazlxZVhkaVZIbzFiRU5hVVdGbFdWbDNObmxEUkROMFlURTNkak5ZVDNOaVJVa2lMQ0o1SWpvaVNFRkdjRlJYWDJNMWJGOUhaamhQVlhKelkzbGZabE5KUjFkUGVWbGxSMDFxZVVkU1lrbzJOemhZYXlKOUxDSmthV1FpT2lKa2FXUTZkMlZpT21ScFpDNTNiMjlrWjNKdmRtVmtaVzF2TG1OdmJTSXNJbVpwY25OMFRtRnRaU0k2SWsxaGRIUm9aWGNpTENKc1lYTjBUbUZ0WlNJNklrMXBZMmhoWld3aUxDSnpZMkZ1Ym1Wa1pHOWpJam9pVGxrZ1UzUmhkR1VnUkhKcGRtVnljeUJNYVdObGJuTmxJaXdpYzJWc1ptbGxJam9pVm1WeWFXWnBaV1FnVTJWc1ptbGxJaXdpZG1WeWFXWnBZMkYwYVc5dUlqb2lSblZzYkhrZ1ZtVnlhV1pwWldRaUxDSmhaR1J5WlhOeklqb2lNak0wTlNCQmJubDNhR1Z5WlNCVGRISmxaWFFzSUZsdmRYSWdRMmwwZVN3Z1Rsa2dNVEl6TkRVaUxDSmhaMlYyWlhKcFptbGxaQ0k2SWs5c1pHVnlJSFJvWVc0Z01qRWlMQ0pwYzNNaU9pSm9kSFJ3Y3pvdkwzTmxiR1l0YVhOemRXVmtMbTFsSWl3aWFXRjBJam94TnpBek1UQXpORFV5TENKcWRHa2lPaUpqWlRSaFpqRXpZeTAwWTJVeUxUUTBNbUV0WVROaVlTMDFaR0V3WVdOa056RmpZamNpTENKbGVIQWlPakUzTURNeE1ETTNOVElzSW5CcGJpSTZleUpzWlc1bmRHZ2lPalFzSW5SNWNHVWlPaUp1ZFcxbGNtbGpJaXdpWVd4bklqb2ljMmhoTWpVMklpd2lhWFJsY21GMGFXOXVjeUk2TVN3aWMyRnNkQ0k2SWpBNU1HTTRabVl6TVdKbU1qUTRaamc0T1RSaU5UaGxZemc0TlRnM1l6Z3dJaXdpYUdGemFDSTZJblZWUW5sNVJXWnpNVzFXYlRBMlZtaGhNelIwT1U5eU5tOVJXVEoyV0RWaGJrZFFiMDVuZGxselUwVTlJbjE5Ll9vUTR1TzVnVDJ2WmZRWGdpcm5YX1BEdG9POS1nZGF0dERqQlpSeEZVZklLUGpYbXJYRjU4RmlFdGNseWxpWHhGTHZ2dnNZeDBFeDhiNWQ3YzZ0ZWFBIn19LCJkaWQiOiJkaWQ6aW9uOkVpRGgwRUw4d2c4b0YtN3JSaVJ6RVpWZnNKdmg0c1FYNEpvY2syS3A0al96eGc6ZXlKa1pXeDBZU0k2ZXlKd1lYUmphR1Z6SWpwYmV5SmhZM1JwYjI0aU9pSnlaWEJzWVdObElpd2laRzlqZFcxbGJuUWlPbnNpY0hWaWJHbGpTMlY1Y3lJNlczc2lhV1FpT2lJME9HUTRZVE0wTWpZelkyWTBPVEpoWVRkbVpqWXhZall4T0RObE9HSmpaaUlzSW5CMVlteHBZMHRsZVVwM2F5STZleUpqY25ZaU9pSnpaV053TWpVMmF6RWlMQ0pyYVdRaU9pSTBPR1E0WVRNME1qWXpZMlkwT1RKaFlUZG1aall4WWpZeE9ETmxPR0pqWmlJc0ltdDBlU0k2SWtWRElpd2lkWE5sSWpvaWMybG5JaXdpZUNJNklsUkxZVkUyYzBOdlkxUkVjMjExYWpsMFZGSTVPVFowUmxod1JXTlRNa1ZLVGkweFowOWhaR0ZDZG1zaUxDSjVJam9pTUZSeVNWbElZMlpET1ROV2NFVjFkbW90U0ZoVWJubExkREJ6Ym1GNVQwMTNSMU5LUVRGWWFVUllPQ0o5TENKd2RYSndiM05sY3lJNld5SmhkWFJvWlc1MGFXTmhkR2x2YmlKZExDSjBlWEJsSWpvaVJXTmtjMkZUWldOd01qVTJhekZXWlhKcFptbGpZWFJwYjI1TFpYa3lNREU1SW4xZGZYMWRMQ0oxY0dSaGRHVkRiMjF0YVhSdFpXNTBJam9pUldsQ1FubGtaMlI1V0haa1ZFUm9iM1pzV1dJdFFrVjJSM0V4UW5SMlRXSlNMVVJtYkRjdFNIZFpNVWhVWnlKOUxDSnpkV1ptYVhoRVlYUmhJanA3SW1SbGJIUmhTR0Z6YUNJNklrVnBSR0p4YTA1bGR6ZFVjRFUyY0VKRVQzcDZSRWM1YlRoUFpuZHhhbWxYUmpJM2JUZzJkMWszVFMxMU0xRWlMQ0p5WldOdmRtVnllVU52YlcxcGRHMWxiblFpT2lKRmFVRkdPWGt6Y0UxbFEyUlFTbVpSWWprMVpWVjVUVmxmYVVkQ1JrTXdka1F6ZUROS1ZUQjZWMFZqV1V0QkluMTkifQ.RwwcxrVxu_S5V_tWGbBBq-09o8OeQ92ueA8tGJSPjkG7YsmKq1oXOKsL3-hsq0gl30c9tb7O-P4YyZegNoomOA"
        val resp = http.post(entraIssuanceRequest.issuerReturnAddress, {
            contentType(ContentType.Text.Plain)
            setBody(responseToken)
        })
        println("Resp: $resp")
        println(resp.bodyAsText())
        resp.status shouldBe HttpStatusCode.OK
    }

    //@Test
    suspend fun testCreateDidIon() {
        DidService.registrarMethods.keys shouldContain "ion"
        val didResult = DidService.register(DidIonCreateOptions())
        println(didResult.did)
    }

    //@Test
    suspend fun testCreateKey() {
        val result = LocalKey.Companion.importJWK(File("/home/work/waltid/entra/keys/priv.jwk").readText().trimIndent())
        result.isSuccess shouldBe true
        val key = result.getOrNull()!!
        key.hasPrivateKey shouldBe true

    }
}
