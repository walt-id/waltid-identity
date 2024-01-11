package id.walt.oid4vc

import id.walt.credentials.verification.policies.JwtSignaturePolicy
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.did.dids.DidService
import id.walt.oid4vc.data.ClientIdScheme
import id.walt.oid4vc.data.OpenIDClientMetadata
import id.walt.oid4vc.data.ResponseMode
import id.walt.oid4vc.data.ResponseType
import id.walt.oid4vc.data.dif.*
import id.walt.oid4vc.providers.CredentialWalletConfig
import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.oid4vc.responses.TokenResponse
import io.kotest.common.runBlocking
import io.kotest.core.spec.style.AnnotationSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlin.time.Duration.Companion.minutes

class VP_JVM_Test : AnnotationSpec() {

    private lateinit var testWallet: TestCredentialWallet
    private lateinit var testVerifier: VPTestVerifier

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

    @BeforeAll
    fun init() {
        runBlocking { DidService.init() }
        DidService.resolverMethods.keys shouldContain "jwk"
        DidService.registrarMethods.keys shouldContain "ion"
        testWallet = TestCredentialWallet(CredentialWalletConfig(WALLET_BASE_URL))
        testWallet.start()

        testVerifier = VPTestVerifier()
        testVerifier.start()
    }

    @Test
    fun testParsePresentationDefinition() {
        // parse example 1
        val pd1 = PresentationDefinition.fromJSONString(presentationDefinitionExample1)
        println("pd1: $pd1")
        pd1.id shouldBe "vp token example"
        pd1.inputDescriptors.size shouldBe 1
        pd1.inputDescriptors.first().id shouldBe "id card credential"
        pd1.inputDescriptors.first().format!![VCFormat.ldp_vc]!!.proof_type!! shouldContainExactly setOf("Ed25519Signature2018")
        pd1.inputDescriptors.first().constraints!!.fields!!.first().path shouldContainExactly listOf("\$.type")
        // parse example 2
        val pd2 = PresentationDefinition.fromJSONString(presentationDefinitionExample2)
        println("pd2: $pd2")
        pd2.id shouldBe "example with selective disclosure"
        pd2.inputDescriptors.first().constraints!!.limitDisclosure shouldBe DisclosureLimitation.required
        pd2.inputDescriptors.first().constraints!!.fields!!.size shouldBe 4
        pd2.inputDescriptors.first().constraints!!.fields!!.flatMap { it.path } shouldContainExactly listOf(
            "\$.type",
            "\$.credentialSubject.given_name",
            "\$.credentialSubject.family_name",
            "\$.credentialSubject.birthdate"
        )
        // parse example 3
        val pd3 = PresentationDefinition.fromJSONString(presentationDefinitionExample3)
        println("pd3: $pd3")
        pd3.id shouldBe "alternative credentials"
        pd3.submissionRequirements shouldNotBe null
        pd3.submissionRequirements!!.size shouldBe 1
        pd3.submissionRequirements!!.first().name shouldBe "Citizenship Information"
        pd3.submissionRequirements!!.first().rule shouldBe SubmissionRequirementRule.pick
        pd3.submissionRequirements!!.first().count shouldBe 1
        pd3.submissionRequirements!!.first().from shouldBe "A"
    }

    @Test
    suspend fun testVPAuthorization() {
        val authReq = AuthorizationRequest(
            responseType = ResponseType.vp_token.name,
            clientId = "test-verifier",
            responseMode = ResponseMode.query,
            redirectUri = "http://blank",
            presentationDefinition = PresentationDefinition(
                inputDescriptors = listOf(
                    InputDescriptor(
                        format = mapOf(VCFormat.jwt_vc_json to VCFormatDefinition(setOf("EdDSA"))),
                        constraints = InputDescriptorConstraints(
                            fields = listOf(
                                InputDescriptorField(listOf("$.type"), filter = buildJsonObject {
                                    put("type", "string")
                                    put("const", "VerifiableId")
                                })
                            )
                        )
                    )
                )
            ),
            clientMetadata = OpenIDClientMetadata(listOf(testWallet.baseUrl))
        )
        println("Auth req: $authReq")
        val authResp = http.get(testWallet.metadata.authorizationEndpoint!!) {
            url { parameters.appendAll(parametersOf(authReq.toHttpParameters())) }
        }
        println("Auth resp: $authReq")
        authResp.status shouldBe HttpStatusCode.Found
        authResp.headers.names() shouldContain HttpHeaders.Location.lowercase()
        val redirectUrl = Url(authResp.headers[HttpHeaders.Location]!!)
        val tokenResponse = TokenResponse.fromHttpParameters(redirectUrl.parameters.toMap())
        tokenResponse.vpToken shouldNotBe null

        // vpToken is NOT a string, but JSON ELEMENT
        // this will break without .content(): (if JsonPrimitive and not JsonArray!)
        JwtSignaturePolicy().verify(tokenResponse.vpToken!!.jsonPrimitive.content, null, mapOf()).isSuccess shouldBe true
    }

    //@Test
    suspend fun testMattrLaunchpadVerificationRequest() {

        val mattrLaunchpadResponse = http.post("https://launchpad.mattrlabs.com/api/vp/create") {
            contentType(ContentType.Application.Json)
            setBody("""{"types":["OpenBadgeCredential"]}""")
        }.body<JsonObject>()

        val mattrLaunchpadUrl = mattrLaunchpadResponse["authorizeRequestUri"]!!.jsonPrimitive.content

        /*-H 'Referer: https://launchpad.mattrlabs.com/credential/OpenBadgeCredential?name=Example+University+Degree&description=JFF+Plugfest+3+OpenBadge+Credential&issuerIconUrl=https%3A%2F%2Fw3c-ccg.github.io%2Fvc-ed%2Fplugfest-1-2022%2Fimages%2FJFF_LogoLockup.png&issuerLogoUrl=undefined&backgroundColor=%23464c49&watermarkImageUrl=undefined&issuerName=Example+University' -H 'Content-Type: application/json'-H 'TE: trailers' *///--data-raw '{"types":["OpenBadgeCredential"]}'


        // parse verification request (QR code)
        val authReq = AuthorizationRequest.fromHttpQueryString(Url(mattrLaunchpadUrl).encodedQuery)
        println("Auth req: $authReq")
        authReq.responseMode shouldBe ResponseMode.direct_post
        authReq.responseType shouldBe ResponseType.vp_token.name
        authReq.responseUri shouldNotBe null
        authReq.presentationDefinition shouldBe null
        authReq.presentationDefinitionUri shouldNotBe null

        val presentationDefinition = PresentationDefinition.fromJSONString(mattrLaunchpadPresentationDefinitionData)
        presentationDefinition.id shouldBe "vp token example"
        presentationDefinition.inputDescriptors.size shouldBe 1
        presentationDefinition.inputDescriptors[0].id shouldBe "OpenBadgeCredential"
        presentationDefinition.inputDescriptors[0].format!!.keys shouldContain VCFormat.jwt_vc_json
        presentationDefinition.inputDescriptors[0].format!![VCFormat.jwt_vc_json]!!.alg!! shouldContain "EdDSA"
        presentationDefinition.inputDescriptors[0].constraints?.fields?.first()?.path?.first() shouldBe "$.type"
        presentationDefinition.inputDescriptors[0].constraints?.fields?.first()?.filter?.get("pattern")?.jsonPrimitive?.content shouldBe "OpenBadgeCredential"

        val siopSession = testWallet.initializeAuthorization(authReq, 5.minutes)
        siopSession.authorizationRequest?.presentationDefinition shouldNotBe null
        val tokenResponse = testWallet.processImplicitFlowAuthorization(siopSession.authorizationRequest!!)
        println("tokenResponse vpToken: ${tokenResponse.vpToken}")
        tokenResponse.vpToken shouldNotBe null
        tokenResponse.presentationSubmission shouldNotBe null

        println("Got token response: $tokenResponse")

        val debugPresentingPresentationSubmission =
            tokenResponse.toHttpParameters()["presentation_submission"]!!.first()
        val decoded = Json.parseToJsonElement(debugPresentingPresentationSubmission).jsonObject
        val encoded = Json { prettyPrint = true }.encodeToString(decoded)
        println(encoded)

        println("Submitting...")
        val resp = http.submitForm(siopSession.authorizationRequest!!.responseUri!!,
            parameters {
                tokenResponse.toHttpParameters().forEach { entry ->
                    println("submitForm param: ${entry.key} -> ${entry.value}")
                    println("first val is: ${entry.value.first()}")
                    appendAll(entry.key, entry.value)
                }
            })
        resp.status shouldBe HttpStatusCode.OK

        val mattrLaunchpadResult = http.post("https://launchpad.mattrlabs.com/api/vp/poll-results") {
            contentType(ContentType.Application.Json)
            setBody("""{"state":"${authReq.state}"}""")
        }.body<JsonObject>()
        mattrLaunchpadResult["vcVerification"]!!.jsonArray[0].jsonObject["verified"]!!.jsonPrimitive.boolean shouldBe true

    }

    //@Test
    suspend fun testUniresolverVerificationRequest() {

        val uniresUrl =
            "openid4vp://authorize?response_type=vp_token&presentation_definition={\"id\":\"OpenBadgeCredential\",\"input_descriptors\":[{\"id\":\"OpenBadge Credential\",\"format\":{\"jwt_vc\":{\"proof_type\":[\"ES256\",\"ES256\",\"ES256K\",\"PS256\"]},\"jwt_vp\":{\"proof_type\":[\"ES256\",\"ES256\",\"ES256K\",\"PS256\"]},\"ldp_vc\":{\"proof_type\":[\"Ed25519Signature2018\",\"Ed25519Signature2020\",\"JsonWebSignature2020\",\"EcdsaSecp256k1Signature2019\"]},\"ldp_vp\":{\"proof_type\":[\"Ed25519Signature2018\",\"Ed25519Signature2020\",\"JsonWebSignature2020\",\"EcdsaSecp256k1Signature2019\"]}},\"constraints\":{\"fields\":[{\"path\":[\"\$.type\"],\"optional\":false}]}}]}&client_id=https://oidc4vp.univerifier.io/1.0/authorization/direct_post&response_mode=direct_post&response_uri=https://oidc4vp.univerifier.io/1.0/authorization/direct_post&state=DUoqgmKcmoPsUuURKNJV&nonce=d292a622-82ec-4608-a873-356deae18bee"
        /*-H 'Referer: https://launchpad.mattrlabs.com/credential/OpenBadgeCredential?name=Example+University+Degree&description=JFF+Plugfest+3+OpenBadge+Credential&issuerIconUrl=https%3A%2F%2Fw3c-ccg.github.io%2Fvc-ed%2Fplugfest-1-2022%2Fimages%2FJFF_LogoLockup.png&issuerLogoUrl=undefined&backgroundColor=%23464c49&watermarkImageUrl=undefined&issuerName=Example+University' -H 'Content-Type: application/json'-H 'TE: trailers' *///--data-raw '{"types":["OpenBadgeCredential"]}'


        // parse verification request (QR code)
        val authReq = AuthorizationRequest.fromHttpQueryString(Url(uniresUrl).encodedQuery)
        println("Auth req: $authReq")
        authReq.responseMode shouldBe ResponseMode.direct_post
        authReq.responseType shouldBe ResponseType.vp_token.name
        authReq.responseUri shouldNotBe null
        //authReq.presentationDefinition shouldBe null
        //authReq.presentationDefinitionUri shouldNotBe null

        val siopSession = testWallet.initializeAuthorization(authReq, 5.minutes)
        siopSession.authorizationRequest?.presentationDefinition shouldNotBe null
        val tokenResponse = testWallet.processImplicitFlowAuthorization(siopSession.authorizationRequest!!)
        println("tokenResponse vpToken: ${tokenResponse.vpToken}")
        tokenResponse.vpToken shouldNotBe null
        tokenResponse.presentationSubmission shouldNotBe null

        println("Got token response: $tokenResponse")

        val debugPresentingPresentationSubmission =
            tokenResponse.toHttpParameters()["presentation_submission"]!!.first()
        val decoded = Json.parseToJsonElement(debugPresentingPresentationSubmission).jsonObject
        val encoded = Json { prettyPrint = true }.encodeToString(decoded)
        println(encoded)

        println("Submitting...")
        val resp = http.submitForm(siopSession.authorizationRequest!!.responseUri!!,
            parameters {
                tokenResponse.toHttpParameters().forEach { entry ->
                    println("submitForm param: ${entry.key} -> ${entry.value}")
                    println("first val is: ${entry.value.first()}")
                    appendAll(entry.key, entry.value)
                }
            })
        resp.status shouldBe HttpStatusCode.OK
    }

    //@Test
    suspend fun testSphereonLaunchpadVerificationRequest() {
        /*
        https://wallet.example.com?
            client_id=https%3A%2F%2Fclient.example.org%2Fcb
            &request_uri=https%3A%2F%2Fclient.example.org%2F567545564
         */


        /*
        openid-vc://?request_uri=https%3A%2F%2Fssi.sphereon.com%2Fagent%2Fsiop%2Fdefinitions%2Fsphereon%2Fauth-requests%2F9727e922-e8a3-4b79-a29e-a167062b8ac4

        'request_uri':	https://ssi.sphereon.com/agent/siop/definitions/sphereon/auth-requests/9727e922-e8a3-4b79-a29e-a167062b8ac4


        eyJhbGciOiJFUzI1NiIsImtpZCI6ImRpZDpqd2s6ZXlKaGJHY2lPaUpGVXpJMU5pSXNJblZ6WlNJNkluTnBaeUlzSW10MGVTSTZJa1ZESWl3aVkzSjJJam9pVUMweU5UWWlMQ0o0SWpvaVZFY3lTREo0TW1SWFdFNHpkVU54V25CeFJqRjVjMEZRVVZaRVNrVk9YMGd0UTAxMFltZHFZaTFPWnlJc0lua2lPaUk1VFRoT2VHUXdVRTR5TWswNWJGQkVlR1J3UkhCdlZFeDZNVFYzWm5sYVNuTTJXbWhMU1ZWS016TTRJbjAjMCIsInR5cCI6IkpXVCJ9.eyJpYXQiOjE2OTYyNTI3NTEsImV4cCI6MTY5NjI1Mjg3MSwicmVzcG9uc2VfdHlwZSI6ImlkX3Rva2VuIiwic2NvcGUiOiJvcGVuaWQiLCJjbGllbnRfaWQiOiJkaWQ6andrOmV5SmhiR2NpT2lKRlV6STFOaUlzSW5WelpTSTZJbk5wWnlJc0ltdDBlU0k2SWtWRElpd2lZM0oySWpvaVVDMHlOVFlpTENKNElqb2lWRWN5U0RKNE1tUlhXRTR6ZFVOeFduQnhSakY1YzBGUVVWWkVTa1ZPWDBndFEwMTBZbWRxWWkxT1p5SXNJbmtpT2lJNVRUaE9lR1F3VUU0eU1rMDViRkJFZUdSd1JIQnZWRXg2TVRWM1pubGFTbk0yV21oTFNWVktNek00SW4wIiwicmVkaXJlY3RfdXJpIjoiaHR0cHM6Ly9zc2kuc3BoZXJlb24uY29tL2FnZW50L3Npb3AvZGVmaW5pdGlvbnMvc3BoZXJlb24vYXV0aC1yZXNwb25zZXMvOTcyN2U5MjItZThhMy00Yjc5LWEyOWUtYTE2NzA2MmI4YWM0IiwicmVzcG9uc2VfbW9kZSI6InBvc3QiLCJub25jZSI6IjM2MzczN2YxLTMzNDQtNDgwYy04Nzc1LWUzOGYzMGQzMDYwNyIsInN0YXRlIjoiOTcyN2U5MjItZThhMy00Yjc5LWEyOWUtYTE2NzA2MmI4YWM0IiwicmVnaXN0cmF0aW9uIjp7ImlkX3Rva2VuX3NpZ25pbmdfYWxnX3ZhbHVlc19zdXBwb3J0ZWQiOlsiRWREU0EiLCJFUzI1NiIsIkVTMjU2SyJdLCJyZXF1ZXN0X29iamVjdF9zaWduaW5nX2FsZ192YWx1ZXNfc3VwcG9ydGVkIjpbIkVkRFNBIiwiRVMyNTYiLCJFUzI1NksiXSwicmVzcG9uc2VfdHlwZXNfc3VwcG9ydGVkIjpbImlkX3Rva2VuIl0sInNjb3Blc19zdXBwb3J0ZWQiOlsib3BlbmlkIGRpZF9hdXRobiJdLCJzdWJqZWN0X3R5cGVzX3N1cHBvcnRlZCI6WyJwYWlyd2lzZSJdLCJzdWJqZWN0X3N5bnRheF90eXBlc19zdXBwb3J0ZWQiOlsiZGlkOmV0aHIiLCJkaWQ6a2V5IiwiZGlkOmlvbiIsImRpZDp3ZWIiLCJkaWQ6andrIl0sInZwX2Zvcm1hdHMiOnsiand0X3ZjIjp7ImFsZyI6WyJFZERTQSIsIkVTMjU2SyJdfSwiand0X3ZwIjp7ImFsZyI6WyJFUzI1NksiLCJFZERTQSJdfX19LCJjbGFpbXMiOnsidnBfdG9rZW4iOnsicHJlc2VudGF0aW9uX2RlZmluaXRpb24iOnsiaWQiOiJzcGhlcmVvbiIsInB1cnBvc2UiOiJGb3IgdGhpcyBwb3J0YWwgd2UgbmVlZCB5b3VyIGUtbWFpbCBhZGRyZXNzIGFuZCBuYW1lIGZyb20gYSBTcGhlcmVvbiBndWVzdCBjcmVkZW50aWFsIiwiaW5wdXRfZGVzY3JpcHRvcnMiOlt7ImlkIjoiNGNlN2FmZjEtMDIzNC00ZjM1LTlkMjEtMjUxNjY4YTYwOTUwIiwibmFtZSI6IlNwaGVyZW9uIEd1ZXN0IiwicHVycG9zZSI6IllvdSBuZWVkIHRvIHByb3ZpZGUgYSBHdWVzdCBDcmVkZW50aWFsLiIsInNjaGVtYSI6W3sidXJpIjoiR3Vlc3RDcmVkZW50aWFsIn1dLCJjb25zdHJhaW50cyI6eyJmaWVsZHMiOlt7InBhdGgiOlsiJC5jcmVkZW50aWFsU3ViamVjdC50eXBlIiwiJC52Yy5jcmVkZW50aWFsU3ViamVjdC50eXBlIl0sImZpbHRlciI6eyJ0eXBlIjoic3RyaW5nIiwicGF0dGVybiI6IlNwaGVyZW9uIEd1ZXN0In19XX19XX19fSwibmJmIjoxNjk2MjUyNzUxLCJqdGkiOiJkMTAxOTljZS0xNzI4LTQxYzktYTZiZS01M2NmNzUzN2JjNzMiLCJpc3MiOiJkaWQ6andrOmV5SmhiR2NpT2lKRlV6STFOaUlzSW5WelpTSTZJbk5wWnlJc0ltdDBlU0k2SWtWRElpd2lZM0oySWpvaVVDMHlOVFlpTENKNElqb2lWRWN5U0RKNE1tUlhXRTR6ZFVOeFduQnhSakY1YzBGUVVWWkVTa1ZPWDBndFEwMTBZbWRxWWkxT1p5SXNJbmtpT2lJNVRUaE9lR1F3VUU0eU1rMDViRkJFZUdSd1JIQnZWRXg2TVRWM1pubGFTbk0yV21oTFNWVktNek00SW4wIiwic3ViIjoiZGlkOmp3azpleUpoYkdjaU9pSkZVekkxTmlJc0luVnpaU0k2SW5OcFp5SXNJbXQwZVNJNklrVkRJaXdpWTNKMklqb2lVQzB5TlRZaUxDSjRJam9pVkVjeVNESjRNbVJYV0U0emRVTnhXbkJ4UmpGNWMwRlFVVlpFU2tWT1gwZ3RRMDEwWW1kcVlpMU9aeUlzSW5raU9pSTVUVGhPZUdRd1VFNHlNazA1YkZCRWVHUndSSEJ2VkV4Nk1UVjNabmxhU25NMldtaExTVlZLTXpNNEluMCJ9.aQ4hK0JbElGQQfJxsRxvI2jLSHubcagPcWKoyRgE19d9iO6HFz2brvYdF2tG--38vpXFGxzex5v4gA
         */

        val sphereonVerifierResponse =
            http.post("https://ssi.sphereon.com/agent/webapp/definitions/sphereon/auth-requests") {
                contentType(ContentType.Application.Json)
                bearerAuth("demo")
                setBody("""{}""")
            }.body<JsonObject>()

        val sphereonAuthReqUrl = sphereonVerifierResponse["authRequestURI"]!!.jsonPrimitive.content
// The Verifier may send an Authorization Request as Request Object by value or by reference as defined in JWT-Secured Authorization Request (JAR) [RFC9101].
        // parse verification request (QR code)


        val urlParams = parseQueryString(Url(sphereonAuthReqUrl).encodedQuery).also {
            it.forEach { k, v ->
                println("$k -> $v")
            }
        }

        val authReq = if (urlParams.contains("request_uri")) {
            val requestUri = urlParams["request_uri"]!!
            println("Got request_uri, resolving: $requestUri")
            val requestJwt = http.get(requestUri)
                .bodyAsText()
            val requestPayload = requestJwt.decodeJws().payload

            AuthorizationRequest(
                responseType = requestPayload["response_type"]!!.jsonPrimitive.content,
                clientId = requestPayload["client_id"]!!.jsonPrimitive.content,
                responseMode = requestPayload["response_mode"]!!.jsonPrimitive.content.let {
                    when (it) {
                        "post" -> ResponseMode.direct_post
                        else -> ResponseMode.valueOf(it)
                    }
                },
                redirectUri = requestPayload["redirect_uri"]?.jsonPrimitive?.contentOrNull,
                //scope = requestPayload["scope"]?.flatMap { it.split(" ") }?.toSet() ?: setOf(),
                scope = requestPayload["scope"]?.jsonPrimitive?.content?.split(" ")?.toSet() ?: setOf(),
                state = requestPayload["state"]?.jsonPrimitive?.contentOrNull,
                //authorizationDetails = requestPayload["authorization_details"]?.flatMap { Json.decodeFromString(AuthorizationDetailsListSerializer, it) },
                walletIssuer = requestPayload["wallet_issuer"]?.jsonPrimitive?.contentOrNull,
                userHint = requestPayload["user_hint"]?.jsonPrimitive?.contentOrNull,
                issuerState = requestPayload["issuer_state"]?.jsonPrimitive?.contentOrNull,
                requestUri = null,
                presentationDefinition =
                (requestPayload["presentation_definition"]?.jsonPrimitive?.contentOrNull)?.let {
                    PresentationDefinition.fromJSONString(
                        it
                    )
                }
                    ?: (PresentationDefinition.fromJSON(
                        requestPayload["claims"]?.jsonObject?.get("vp_token")?.jsonObject?.get("presentation_definition")?.jsonObject
                            ?: throw IllegalArgumentException("Could not find a presentation_definition!")
                    )),
                presentationDefinitionUri = requestPayload["presentation_definition_uri"]?.jsonPrimitive?.contentOrNull,
                clientIdScheme = requestPayload["client_id_scheme"]?.jsonPrimitive?.contentOrNull?.let {
                    ClientIdScheme.fromValue(
                        it
                    )
                },
                clientMetadata = requestPayload["client_metadata"]?.jsonPrimitive?.contentOrNull?.let {
                    OpenIDClientMetadata.fromJSONString(
                        it
                    )
                },
                clientMetadataUri = requestPayload["client_metadata_uri"]?.jsonPrimitive?.contentOrNull,
                nonce = requestPayload["nonce"]?.jsonPrimitive?.contentOrNull,
                responseUri = requestPayload["response_uri"]?.jsonPrimitive?.contentOrNull
                    ?: requestPayload["redirect_uri"]?.jsonPrimitive?.contentOrNull,
                //customrequestPayload = requestPayload.filterKeys { !id.walt.oid4vc.requests.AuthorizationRequest.Companion.knownKeys.contains(it) }
            )
        } else AuthorizationRequest.fromHttpQueryString(Url(sphereonAuthReqUrl).encodedQuery)


        println("Auth req: $authReq")
        authReq.responseMode shouldBe ResponseMode.direct_post
        //authReq.responseType shouldBe ResponseType.vp_token.name
        authReq.responseUri shouldNotBe null
        //authReq.presentationDefinition shouldBe null
        //authReq.presentationDefinitionUri shouldNotBe null

        val presentationDefinition = PresentationDefinition.fromJSONString(mattrLaunchpadPresentationDefinitionData)
        presentationDefinition.id shouldBe "vp token example"
        presentationDefinition.inputDescriptors.size shouldBe 1
        presentationDefinition.inputDescriptors[0].id shouldBe "OpenBadgeCredential"
        presentationDefinition.inputDescriptors[0].format!!.keys shouldContain VCFormat.jwt_vc_json
        presentationDefinition.inputDescriptors[0].format!![VCFormat.jwt_vc_json]!!.alg!! shouldContain "EdDSA"
        presentationDefinition.inputDescriptors[0].constraints?.fields?.first()?.path?.first() shouldBe "$.type"
        presentationDefinition.inputDescriptors[0].constraints?.fields?.first()?.filter?.get("pattern")?.jsonPrimitive?.content shouldBe "OpenBadgeCredential"

        val siopSession = testWallet.initializeAuthorization(authReq, 5.minutes)
        siopSession.authorizationRequest?.presentationDefinition shouldNotBe null
        val tokenResponse = testWallet.processImplicitFlowAuthorization(siopSession.authorizationRequest!!)
        println("tokenResponse vpToken: ${tokenResponse.vpToken}")
        tokenResponse.vpToken shouldNotBe null
        tokenResponse.presentationSubmission shouldNotBe null

        println("Got token response: $tokenResponse")

        val debugPresentingPresentationSubmission =
            tokenResponse.toHttpParameters()["presentation_submission"]!!.first()
        val decoded = Json.parseToJsonElement(debugPresentingPresentationSubmission).jsonObject
        val encoded = Json { prettyPrint = true }.encodeToString(decoded)
        println(encoded)

        println("Submitting...")
        val resp = http.submitForm(siopSession.authorizationRequest!!.responseUri!!,
            parameters {
                tokenResponse.toHttpParameters().forEach { entry ->
                    println("submitForm param: ${entry.key} -> ${entry.value}")
                    println("first val is: ${entry.value.first()}")
                    appendAll(entry.key, entry.value)
                }
            })
        resp.status shouldBe HttpStatusCode.OK
    }

    @Test
    suspend fun testInitializeVerifierSession() {
        val verifierSession = testVerifier.initializeAuthorization(
            PresentationDefinition(
                inputDescriptors = listOf(
                    InputDescriptor(
                        format = mapOf(VCFormat.jwt_vc_json to VCFormatDefinition(alg = setOf("EdDSA"))),
                        constraints = InputDescriptorConstraints(
                            fields = listOf(
                                InputDescriptorField(
                                    listOf("$.type"),
                                    filter = buildJsonObject {
                                        put("const", "VerifiableId")
                                    })
                            )
                        )
                    )
                )
            ), responseMode = ResponseMode.direct_post
        )
        println("Verifier session: $verifierSession")
        verifierSession.authorizationRequest shouldNotBe null

        val walletSession = testWallet.initializeAuthorization(verifierSession.authorizationRequest!!, 1.minutes)
        println("Wallet session: $walletSession")
        val tokenResponse = testWallet.processImplicitFlowAuthorization(walletSession.authorizationRequest!!)
        tokenResponse.vpToken shouldNotBe null
        tokenResponse.presentationSubmission shouldNotBe null

        val resp = http.submitForm(walletSession.authorizationRequest!!.responseUri!!,
            parameters {
                tokenResponse.toHttpParameters().forEach { entry ->
                    entry.value.forEach { append(entry.key, it) }
                }
            })
        println("Resp: $resp")
        resp.status shouldBe HttpStatusCode.OK
    }

    @Test
    suspend fun testWaltVerifierTestRequest() {
        val waltVerifierTestRequest = testVerifier.initializeAuthorization(
            PresentationDefinition.fromJSONString(presentationDefinitionExample1), ResponseMode.direct_post
        ).authorizationRequest!!.toHttpQueryString().let {
            "openid4vp://authorize?$it"
        }
        val authReq = AuthorizationRequest.fromHttpQueryString(Url(waltVerifierTestRequest).encodedQuery)
        println("Auth req: $authReq")
        val walletSession = testWallet.initializeAuthorization(authReq, 1.minutes)
        walletSession.authorizationRequest!!.presentationDefinition shouldNotBe null
        println("Resolved presentation definition: ${walletSession.authorizationRequest!!.presentationDefinition!!.toJSONString()}")
        val tokenResponse = testWallet.processImplicitFlowAuthorization(walletSession.authorizationRequest!!)
        tokenResponse.vpToken shouldNotBe null
        tokenResponse.presentationSubmission shouldNotBe null
        val resp = http.submitForm(walletSession.authorizationRequest!!.responseUri!!,
            parameters {
                tokenResponse.toHttpParameters().forEach { entry ->
                    entry.value.forEach { append(entry.key, it) }
                }
            })
        println("Resp: $resp")
        resp.status shouldBe HttpStatusCode.OK
    }

    val presentationDefinitionExample1 = "{\n" +
            "    \"id\": \"vp token example\",\n" +
            "    \"input_descriptors\": [\n" +
            "        {\n" +
            "            \"id\": \"id card credential\",\n" +
            "            \"format\": {\n" +
            "                \"ldp_vc\": {\n" +
            "                    \"proof_type\": [\n" +
            "                        \"Ed25519Signature2018\"\n" +
            "                    ]\n" +
            "                }\n" +
            "            },\n" +
            "            \"constraints\": {\n" +
            "                \"fields\": [\n" +
            "                    {\n" +
            "                        \"path\": [\n" +
            "                            \"\$.type\"\n" +
            "                        ],\n" +
            "                        \"filter\": {\n" +
            "                            \"type\": \"string\",\n" +
            "                            \"pattern\": \"IDCardCredential\"\n" +
            "                        }\n" +
            "                    }\n" +
            "                ]\n" +
            "            }\n" +
            "        }\n" +
            "    ]\n" +
            "}"

    val presentationDefinitionExample2 = "{\n" +
            "    \"id\": \"example with selective disclosure\",\n" +
            "    \"input_descriptors\": [\n" +
            "        {\n" +
            "            \"id\": \"ID card with constraints\",\n" +
            "            \"format\": {\n" +
            "                \"ldp_vc\": {\n" +
            "                    \"proof_type\": [\n" +
            "                        \"Ed25519Signature2018\"\n" +
            "                    ]\n" +
            "                }\n" +
            "            },\n" +
            "            \"constraints\": {\n" +
            "                \"limit_disclosure\": \"required\",\n" +
            "                \"fields\": [\n" +
            "                    {\n" +
            "                        \"path\": [\n" +
            "                            \"\$.type\"\n" +
            "                        ],\n" +
            "                        \"filter\": {\n" +
            "                            \"type\": \"string\",\n" +
            "                            \"pattern\": \"IDCardCredential\"\n" +
            "                        }\n" +
            "                    },\n" +
            "                    {\n" +
            "                        \"path\": [\n" +
            "                            \"\$.credentialSubject.given_name\"\n" +
            "                        ]\n" +
            "                    },\n" +
            "                    {\n" +
            "                        \"path\": [\n" +
            "                            \"\$.credentialSubject.family_name\"\n" +
            "                        ]\n" +
            "                    },\n" +
            "                    {\n" +
            "                        \"path\": [\n" +
            "                            \"\$.credentialSubject.birthdate\"\n" +
            "                        ]\n" +
            "                    }\n" +
            "                ]\n" +
            "            }\n" +
            "        }\n" +
            "    ]\n" +
            "}\n"

    val presentationDefinitionExample3 = "{\n" +
            "    \"id\": \"alternative credentials\",\n" +
            "    \"submission_requirements\": [\n" +
            "        {\n" +
            "            \"name\": \"Citizenship Information\",\n" +
            "            \"rule\": \"pick\",\n" +
            "            \"count\": 1,\n" +
            "            \"from\": \"A\"\n" +
            "        }\n" +
            "    ],\n" +
            "    \"input_descriptors\": [\n" +
            "        {\n" +
            "            \"id\": \"id card credential\",\n" +
            "            \"group\": [\n" +
            "                \"A\"\n" +
            "            ],\n" +
            "            \"format\": {\n" +
            "                \"ldp_vc\": {\n" +
            "                    \"proof_type\": [\n" +
            "                        \"Ed25519Signature2018\"\n" +
            "                    ]\n" +
            "                }\n" +
            "            },\n" +
            "            \"constraints\": {\n" +
            "                \"fields\": [\n" +
            "                    {\n" +
            "                        \"path\": [\n" +
            "                            \"\$.type\"\n" +
            "                        ],\n" +
            "                        \"filter\": {\n" +
            "                            \"type\": \"string\",\n" +
            "                            \"pattern\": \"IDCardCredential\"\n" +
            "                        }\n" +
            "                    }\n" +
            "                ]\n" +
            "            }\n" +
            "        },\n" +
            "        {\n" +
            "            \"id\": \"passport credential\",\n" +
            "            \"format\": {\n" +
            "                \"jwt_vc_json\": {\n" +
            "                    \"alg\": [\n" +
            "                        \"RS256\"\n" +
            "                    ]\n" +
            "                }\n" +
            "            },\n" +
            "            \"group\": [\n" +
            "                \"A\"\n" +
            "            ],\n" +
            "            \"constraints\": {\n" +
            "                \"fields\": [\n" +
            "                    {\n" +
            "                        \"path\": [\n" +
            "                            \"\$.vc.type\"\n" +
            "                        ],\n" +
            "                        \"filter\": {\n" +
            "                            \"type\": \"string\",\n" +
            "                            \"pattern\": \"PassportCredential\"\n" +
            "                        }\n" +
            "                    }\n" +
            "                ]\n" +
            "            }\n" +
            "        }\n" +
            "    ]\n" +
            "}\n"

    //val mattrLaunchpadVerificationRequest =
    //    "openid4vp://authorize?client_id=https%3A%2F%2Flaunchpad.mattrlabs.com%2Fapi%2Fvp%2Fcallback&client_id_scheme=redirect_uri&response_uri=https%3A%2F%2Flaunchpad.mattrlabs.com%2Fapi%2Fvp%2Fcallback&response_type=vp_token&response_mode=direct_post&presentation_definition_uri=https%3A%2F%2Flaunchpad.mattrlabs.com%2Fapi%2Fvp%2Frequest%3Fstate%3D6-obA38Nu9qMPn6GT26flQ&nonce=ddZ6JA75YljfoOBj-9I-nA&state=6-obA38Nu9qMPn6GT26flQ"
    val mattrLaunchpadPresentationDefinitionData =
        "{\"id\":\"vp token example\",\"input_descriptors\":[{\"id\":\"OpenBadgeCredential\",\"format\":{\"jwt_vc_json\":{\"alg\":[\"EdDSA\"]}},\"constraints\":{\"fields\":[{\"path\":[\"\$.type\"],\"filter\":{\"type\":\"string\",\"pattern\":\"OpenBadgeCredential\"}}]}}]}"


    val ONLINE_TEST: Boolean = true

    @Ignore
    @Test
    suspend fun testRequestByReference() {
        val reqUri = when (ONLINE_TEST) {
            true -> testCreateEntraPresentationRequest()
            else -> "openid-vc://?request_uri=$VP_VERIFIER_BASE_URL/req/6af1f46f-8d91-4eaa-b0c0-f042b7a621f8"
        }
        reqUri shouldNotBe null
        val authReq = AuthorizationRequest.fromHttpParametersAuto(parseQueryString(Url(reqUri!!).encodedQuery).toMap())
        authReq.clientId shouldBe "did:web:entra.walt.id"

        val tokenResponse = testWallet.processImplicitFlowAuthorization(authReq)
        val resp =
            http.submitForm(authReq.responseUri ?: authReq.redirectUri ?: throw Exception("response_uri or redirect_uri must be set"),
                parameters {
                    tokenResponse.toHttpParameters().forEach { entry ->
                        entry.value.forEach { append(entry.key, it) }
                    }
                    append("id_token", ms_id_token)
                })
        println("Resp: $resp")
        resp.status shouldBe HttpStatusCode.OK
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

    suspend fun testCreateEntraPresentationRequest(): String? {
        val accessToken = entraAuthorize()
        val createPresentationRequestBody = "{\n" +
                "    \"authority\": \"did:web:entra.walt.id\",\n" +
                "    \"callback\": {\n" +
                "        \"headers\": {\n" +
                "            \"api-key\": \"1234\"\n" +
                "        },\n" +
                "        \"state\": \"1234\",\n" +
                "        \"url\": \"https://0406-62-178-27-231.ngrok-free.app\"\n" +
                "    },\n" +
                "    \"registration\": {\n" +
                "        \"clientName\": \"verifiable-credentials-app\"\n" +
                "    },\n" +
                "    \"requestedCredentials\": [\n" +
                "        {\n" +
                "            \"acceptedIssuers\": [\n" +
                "                \"did:web:entra.walt.id\"\n" +
                "            ],\n" +
                "            \"purpose\": \"TEST\",\n" +
                "            \"type\": \"VerifiedEmployee\"\n" +
                "        }\n" +
                "    ]\n" +
                "}"
        val response = http.post("https://verifiedid.did.msidentity.com/v1.0/verifiableCredentials/createPresentationRequest") {
            header(HttpHeaders.Authorization, accessToken)
            contentType(ContentType.Application.Json)
            setBody(createPresentationRequestBody)
        }
        response.status shouldBe HttpStatusCode.Created
        val responseObj = response.body<JsonObject>()
        return responseObj["url"]?.jsonPrimitive?.content
    }
}
