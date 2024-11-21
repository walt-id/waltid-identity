package id.walt.oid4vc

import id.walt.credentials.utils.VCFormat
import id.walt.credentials.utils.randomUUID
import id.walt.policies.policies.JwtSignaturePolicy
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.did.dids.DidService
import id.walt.mdoc.dataretrieval.DeviceResponse
import id.walt.mdoc.doc.MDoc
import id.walt.oid4vc.data.*
import id.walt.oid4vc.data.dif.*
import id.walt.oid4vc.interfaces.PresentationResult
import id.walt.oid4vc.providers.CredentialWalletConfig
import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.oid4vc.responses.TokenResponse
import id.walt.sdjwt.SDJwtVC
import io.kotest.matchers.collections.shouldContain
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.util.*
import io.ktor.util.reflect.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.jupiter.api.BeforeAll
import kotlin.test.*
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class VP_JVM_Test {

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
    companion object {
        private lateinit var testWallet: TestCredentialWallet
        private lateinit var testVerifier: VPTestVerifier
        @BeforeAll
        @JvmStatic
        fun init() = runTest {
            DidService.minimalInit()
            assertContains(DidService.resolverMethods.keys, "jwk")
            assertContains(DidService.registrarMethods.keys, "web")
            testWallet = TestCredentialWallet(CredentialWalletConfig(WALLET_BASE_URL))
            testWallet.start()

            testVerifier = VPTestVerifier()
            testVerifier.start()
        }
    }

    @Test
    fun testParsePresentationDefinition() {
        // parse example 1
        val pd1 = PresentationDefinition.fromJSONString(presentationDefinitionExample1)
        println("pd1: $pd1")
        assertEquals(expected = "vp token example", actual = pd1.id)
        assertEquals(expected = 1, actual = pd1.inputDescriptors.size)
        assertEquals(expected = "id card credential", actual = pd1.inputDescriptors.first().id)
        assertContentEquals(
            expected = setOf("Ed25519Signature2018"),
            actual = pd1.inputDescriptors.first().format!![VCFormat.ldp_vc]!!.proof_type!!.asIterable()
        )
        assertContentEquals(
            expected = listOf("\$.type"),
            actual = pd1.inputDescriptors.first().constraints!!.fields!!.first().path
        )
        // parse example 2
        val pd2 = PresentationDefinition.fromJSONString(presentationDefinitionExample2)
        println("pd2: $pd2")
        assertEquals(expected = "example with selective disclosure", actual = pd2.id)
        assertEquals(expected = DisclosureLimitation.required, actual = pd2.inputDescriptors.first().constraints!!.limitDisclosure)
        assertEquals(expected = 4, actual = pd2.inputDescriptors.first().constraints!!.fields!!.size)
        assertContentEquals(expected = listOf(
            "\$.type",
            "\$.credentialSubject.given_name",
            "\$.credentialSubject.family_name",
            "\$.credentialSubject.birthdate"
        ), actual = pd2.inputDescriptors.first().constraints!!.fields!!.flatMap { it.path })
        // parse example 3
        val pd3 = PresentationDefinition.fromJSONString(presentationDefinitionExample3)
        println("pd3: $pd3")
        assertEquals(expected = "alternative credentials", actual = pd3.id)
        assertNotNull(actual = pd3.submissionRequirements)
        assertEquals(expected = 1, actual = pd3.submissionRequirements!!.size)
        assertEquals(expected = "Citizenship Information", actual = pd3.submissionRequirements!!.first().name)
        assertEquals(expected = SubmissionRequirementRule.pick, actual = pd3.submissionRequirements!!.first().rule)
        assertEquals(expected = 1, actual = pd3.submissionRequirements!!.first().count)
        assertEquals(expected = "A", actual = pd3.submissionRequirements!!.first().from)
    }

    @Test
    fun testVPAuthorization() = runTest {
        val authReq = OpenID4VP.createPresentationRequest(
            PresentationDefinitionParameter.fromPresentationDefinition(
                PresentationDefinition(
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
                )
            ),
            responseMode = ResponseMode.query,
            responseTypes = setOf(ResponseType.VpToken),
            redirectOrResponseUri = "http://blank",
            nonce = null,
            state = null,
            scopes = setOf(),
            clientId = "test-verifier",
            clientIdScheme = ClientIdScheme.PreRegistered,
            clientMetadataParameter = ClientMetadataParameter.fromClientMetadata(
                OpenIDClientMetadata(listOf(testWallet.baseUrl))
            )
        )

        println("Auth req: $authReq")
        val authResp = http.get(testWallet.metadata.authorizationEndpoint!!) {
            url { parameters.appendAll(parametersOf(authReq.toHttpParameters())) }
        }
        println("Auth resp: $authReq")
        assertEquals(expected = HttpStatusCode.Found, actual = authResp.status)
        assertContains(iterable = authResp.headers.names(), element = HttpHeaders.Location)
        val redirectUrl = Url(authResp.headers[HttpHeaders.Location]!!)
        val tokenResponse = TokenResponse.fromHttpParameters(redirectUrl.parameters.toMap())
        assertNotNull(actual = tokenResponse.vpToken)

        // vpToken is NOT a string, but JSON ELEMENT
        // this will break without .content(): (if JsonPrimitive and not JsonArray!)
        assertTrue(actual = JwtSignaturePolicy().verify(tokenResponse.vpToken!!.jsonPrimitive.content, null, mapOf()).isSuccess)
    }

    @Test
    fun testOID4VPFlow() = runTest {
        //------- VERIFIER ---------
        val presentationReq = OpenID4VP.createPresentationRequest(
            PresentationDefinitionParameter.fromPresentationDefinition(
                PresentationDefinition(
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
                )
            ), ResponseMode.query, setOf(ResponseType.VpToken), "http://blank", Uuid.random().toString(),
            "test", setOf(), "test-verifier", ClientIdScheme.PreRegistered, ClientMetadataParameter.fromClientMetadata(
                OpenIDClientMetadata(listOf(testWallet.baseUrl))
            )
        )
        // get authorization URL to be rendered as QR code, or called on wallet authorization endpoint, for same-device flow
        val authUrl = OpenID4VP.getAuthorizationUrl(presentationReq)

        // ----------- WALLET -------------------

        // parse authorization/presentation request
        val parsedPresentationRequest = AuthorizationRequest.fromHttpParametersAuto(Url(authUrl).parameters.toMap())
        assertEquals(
            expected = presentationReq.presentationDefinition?.toJSON(),
            actual = parsedPresentationRequest.presentationDefinition?.toJSON()
        )
        // Determine flow details (implicit flow, with vp_token response type, same-device flow with "query" response mode)
        assertContains(iterable = parsedPresentationRequest.responseType, element = ResponseType.VpToken)
        assertEquals(expected = ResponseMode.query, actual = parsedPresentationRequest.responseMode)
        // optional (code flow): code response (verifier <-- wallet), token endpoint (verifier -> wallet)

        // Generate token response
        val testVP =
            "eyJhbGciOiJFUzI1NksiLCJ0eXAiOiJKV1QiLCJraWQiOiJkaWQ6d2ViOmVudHJhLndhbHQuaWQ6aG9sZGVyIzQ4ZDhhMzQyNjNjZjQ5MmFhN2ZmNjFiNjE4M2U4YmNmIn0.eyJzdWIiOiJkaWQ6d2ViOmVudHJhLndhbHQuaWQ6aG9sZGVyIiwibmJmIjoxNzA4OTUzOTI0LCJpYXQiOjE3MDg5NTM5ODQsImp0aSI6IjEiLCJpc3MiOiJkaWQ6d2ViOmVudHJhLndhbHQuaWQ6aG9sZGVyIiwibm9uY2UiOiIiLCJhdWQiOiJ0ZXN0LXZlcmlmaWVyIiwidnAiOnsiQGNvbnRleHQiOlsiaHR0cHM6Ly93d3cudzMub3JnLzIwMTgvY3JlZGVudGlhbHMvdjEiXSwidHlwZSI6WyJWZXJpZmlhYmxlUHJlc2VudGF0aW9uIl0sImlkIjoiMSIsImhvbGRlciI6ImRpZDp3ZWI6ZW50cmEud2FsdC5pZDpob2xkZXIiLCJ2ZXJpZmlhYmxlQ3JlZGVudGlhbCI6W119fQ.hgGTCeYGGE9qhlSqeBQmY7WnAts6aBSH378-z5WtNDAB8LaQwXKeoOLAURoE5utacYhX-hDZJwBpGg9Zf1ZkgA"
        val tokenResponse = OpenID4VP.generatePresentationResponse(
            PresentationResult(
                listOf(JsonPrimitive(testVP)),
                PresentationSubmission(
                    "presentation_1", "definition_1", listOf(
                        DescriptorMapping("Test descriptor", VCFormat.jwt_vc_json, DescriptorMapping.vpPath(1, 0))
                    )
                )
            )
        )
        // Optional: respond to token request (code-flow, verifier <-- wallet), respond to authorization request (implicit flow, verifier <-- wallet)
        // Optional: post token response to response_uri of verifier (cross-device flow, verifier <-- wallet)
        val responseUri = tokenResponse.toRedirectUri(presentationReq.redirectUri!!, ResponseMode.query)

        // ------------ VERIFIER ---------------------

        // Parse token response
        val parsedTokenResponse = OpenID4VP.parsePresentationResponseFromUrl(responseUri)
        assertEquals(expected = testVP, actual = parsedTokenResponse.vpToken?.jsonPrimitive?.content)
        assertEquals(expected = 1, actual = parsedTokenResponse.presentationSubmission?.descriptorMap?.size)
    }

    @Test
    fun testVpTokenParamSerializationAndDeserialization() = runTest {
        val presObj = Json.parseToJsonElement("{\"test\": \"bla\"}").jsonObject
        val presStr1 = "eyJ.eyJpc3M.ft_Eq4"
        val presStr2 = "eyJ.eyJpc3M.ft_Eq5"
        val presParams = listOf(
            VpTokenParameter(presStr1), VpTokenParameter(presObj), VpTokenParameter(setOf(presStr1), listOf(presObj)),
            VpTokenParameter(setOf(presStr1, presStr2)), VpTokenParameter(listOf(presObj, presObj))
        )
        presParams.forEach { param ->
            val tokenResponse = TokenResponse.success(param, null, null, null)

            if (param.vpTokenObjects.plus(param.vpTokenStrings).size == 1) {
                assertFalse(actual = tokenResponse.vpToken!!.instanceOf(JsonArray::class))
                if (param.vpTokenObjects.size == 1)
                    assertTrue(actual = tokenResponse.vpToken!!.instanceOf(JsonObject::class))
                else {
                    assertTrue(tokenResponse.vpToken!!.instanceOf(JsonPrimitive::class))
                    assertFalse(actual = tokenResponse.vpToken!!.jsonPrimitive.isString) // should be an unquoted string if vp_token is a single string
                }
            } else {
                assertTrue(actual = tokenResponse.vpToken!!.instanceOf(JsonArray::class))
                tokenResponse.vpToken!!.jsonArray.forEach {
                    if (it is JsonPrimitive)
                        assertTrue(actual = it.isString) // string elements in the array must be quoted strings
                    else assertTrue(actual = it.instanceOf(JsonObject::class))
                }
            }

            val url = tokenResponse.toRedirectUri("http://blank", ResponseMode.query)
            println(url)
            val parsedResponse = TokenResponse.fromHttpParameters(Url(url).parameters.toMap())
            assertNotNull(actual = parsedResponse.vpToken)
            val parsedVpTokenParam = VpTokenParameter.fromJsonElement(parsedResponse.vpToken!!)
            assertEquals(expected = param.vpTokenStrings, actual = parsedVpTokenParam.vpTokenStrings)
            assertContentEquals(expected = param.vpTokenObjects, actual = parsedVpTokenParam.vpTokenObjects)
        }
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
        assertEquals(expected = ResponseMode.direct_post, actual = authReq.responseMode)
        assertContains(iterable = authReq.responseType, element = ResponseType.VpToken)
        assertNotNull(actual = authReq.responseUri)
        assertNull(actual = authReq.presentationDefinition)
        assertNotNull(actual = authReq.presentationDefinitionUri)

        val presentationDefinition = PresentationDefinition.fromJSONString(mattrLaunchpadPresentationDefinitionData)
        assertEquals(expected = "vp token example", actual = presentationDefinition.id)
        assertEquals(expected = 1, actual = presentationDefinition.inputDescriptors.size)
        assertEquals(expected = "OpenBadgeCredential", actual = presentationDefinition.inputDescriptors[0].id)
        assertContains(
            iterable = presentationDefinition.inputDescriptors[0].format!!.keys,
            element = VCFormat.jwt_vc_json
        )
        assertContains(
            iterable = presentationDefinition.inputDescriptors[0].format!![VCFormat.jwt_vc_json]!!.alg!!,
            element = "EdDSA"
        )
        assertEquals(
            expected = "$.type",
            actual = presentationDefinition.inputDescriptors[0].constraints?.fields?.first()?.path?.first()
        )
        assertEquals(
            expected = "OpenBadgeCredential",
            actual = presentationDefinition.inputDescriptors[0].constraints?.fields?.first()?.filter?.get("pattern")?.jsonPrimitive?.content
        )

        val siopSession = testWallet.initializeAuthorization(authReq, 5.minutes, null)
        assertNotNull(actual = siopSession.authorizationRequest?.presentationDefinition)
        val tokenResponse = testWallet.processImplicitFlowAuthorization(siopSession.authorizationRequest!!)
        println("tokenResponse vpToken: ${tokenResponse.vpToken}")
        assertNotNull(actual = tokenResponse.vpToken)
        assertNotNull(actual = tokenResponse.presentationSubmission)

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
        assertEquals(expected = HttpStatusCode.OK, actual = resp.status)

        val mattrLaunchpadResult = http.post("https://launchpad.mattrlabs.com/api/vp/poll-results") {
            contentType(ContentType.Application.Json)
            setBody("""{"state":"${authReq.state}"}""")
        }.body<JsonObject>()
        assertTrue(actual = mattrLaunchpadResult["vcVerification"]!!.jsonArray[0].jsonObject["verified"]!!.jsonPrimitive.boolean)

    }

    //@Test
    suspend fun testUniresolverVerificationRequest() {

        val uniresUrl =
            "openid4vp://authorize?response_type=vp_token&presentation_definition={\"id\":\"OpenBadgeCredential\",\"input_descriptors\":[{\"id\":\"OpenBadge Credential\",\"format\":{\"jwt_vc\":{\"proof_type\":[\"ES256\",\"ES256\",\"ES256K\",\"PS256\"]},\"jwt_vp\":{\"proof_type\":[\"ES256\",\"ES256\",\"ES256K\",\"PS256\"]},\"ldp_vc\":{\"proof_type\":[\"Ed25519Signature2018\",\"Ed25519Signature2020\",\"JsonWebSignature2020\",\"EcdsaSecp256k1Signature2019\"]},\"ldp_vp\":{\"proof_type\":[\"Ed25519Signature2018\",\"Ed25519Signature2020\",\"JsonWebSignature2020\",\"EcdsaSecp256k1Signature2019\"]}},\"constraints\":{\"fields\":[{\"path\":[\"\$.type\"],\"optional\":false}]}}]}&client_id=https://oidc4vp.univerifier.io/1.0/authorization/direct_post&response_mode=direct_post&response_uri=https://oidc4vp.univerifier.io/1.0/authorization/direct_post&state=DUoqgmKcmoPsUuURKNJV&nonce=d292a622-82ec-4608-a873-356deae18bee"
        /*-H 'Referer: https://launchpad.mattrlabs.com/credential/OpenBadgeCredential?name=Example+University+Degree&description=JFF+Plugfest+3+OpenBadge+Credential&issuerIconUrl=https%3A%2F%2Fw3c-ccg.github.io%2Fvc-ed%2Fplugfest-1-2022%2Fimages%2FJFF_LogoLockup.png&issuerLogoUrl=undefined&backgroundColor=%23464c49&watermarkImageUrl=undefined&issuerName=Example+University' -H 'Content-Type: application/json'-H 'TE: trailers' *///--data-raw '{"types":["OpenBadgeCredential"]}'


        // parse verification request (QR code)
        val authReq = AuthorizationRequest.fromHttpQueryString(Url(uniresUrl).encodedQuery)
        println("Auth req: $authReq")
        assertEquals(expected = ResponseMode.direct_post, actual = authReq.responseMode)
        assertContains(iterable = authReq.responseType, element = ResponseType.VpToken)
        assertNotNull(actual = authReq.responseUri)
        //authReq.presentationDefinition shouldBe null
        //authReq.presentationDefinitionUri shouldNotBe null

        val siopSession = testWallet.initializeAuthorization(authReq, 5.minutes, null)
        assertNotNull(actual = siopSession.authorizationRequest?.presentationDefinition)
        val tokenResponse = testWallet.processImplicitFlowAuthorization(siopSession.authorizationRequest!!)
        println("tokenResponse vpToken: ${tokenResponse.vpToken}")
        assertNotNull(actual = tokenResponse.vpToken)
        assertNotNull(actual = tokenResponse.presentationSubmission)

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
        assertEquals(expected = HttpStatusCode.OK, actual = resp.status)
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
                responseType = ResponseType.fromResponseTypeString(requestPayload["response_type"]!!.jsonPrimitive.content),
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
        assertEquals(expected = ResponseMode.direct_post, actual = authReq.responseMode)
        //authReq.responseType shouldBe ResponseType.vp_token.name
        assertNotNull(actual = authReq.responseUri)
        //authReq.presentationDefinition shouldBe null
        //authReq.presentationDefinitionUri shouldNotBe null

        val presentationDefinition = PresentationDefinition.fromJSONString(mattrLaunchpadPresentationDefinitionData)
        assertEquals(expected = "vp token example", actual = presentationDefinition.id)
        assertEquals(expected = 1, actual = presentationDefinition.inputDescriptors.size)
        assertEquals(expected = "OpenBadgeCredential", actual = presentationDefinition.inputDescriptors[0].id)
        assertContains(
            iterable = presentationDefinition.inputDescriptors[0].format!!.keys,
            element = VCFormat.jwt_vc_json
        )
        assertContains(
            iterable = presentationDefinition.inputDescriptors[0].format!![VCFormat.jwt_vc_json]!!.alg!!,
            element = "EdDSA"
        )
        assertEquals(
            expected = "$.type",
            actual = presentationDefinition.inputDescriptors[0].constraints?.fields?.first()?.path?.first()
        )
        assertEquals(
            expected = "OpenBadgeCredential",
            actual = presentationDefinition.inputDescriptors[0].constraints?.fields?.first()?.filter?.get("pattern")?.jsonPrimitive?.content
        )

        val siopSession = testWallet.initializeAuthorization(authReq, 5.minutes, null)
        assertNotNull(actual = siopSession.authorizationRequest?.presentationDefinition)
        val tokenResponse = testWallet.processImplicitFlowAuthorization(siopSession.authorizationRequest!!)
        println("tokenResponse vpToken: ${tokenResponse.vpToken}")
        assertNotNull(actual = tokenResponse.vpToken)
        assertNotNull(tokenResponse.presentationSubmission)

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
        assertEquals(expected = HttpStatusCode.OK, actual = resp.status)
    }

    @Test
    fun testInitializeVerifierSession() = runTest {
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
        assertNotNull(actual = verifierSession.authorizationRequest)

        val walletSession = testWallet.initializeAuthorization(verifierSession.authorizationRequest!!, 1.minutes, null)
        println("Wallet session: $walletSession")
        val tokenResponse = testWallet.processImplicitFlowAuthorization(walletSession.authorizationRequest!!)
        assertNotNull(actual = tokenResponse.vpToken)
        assertNotNull(actual = tokenResponse.presentationSubmission)

        val resp = http.submitForm(walletSession.authorizationRequest!!.responseUri!!,
            parameters {
                tokenResponse.toHttpParameters().forEach { entry ->
                    entry.value.forEach { append(entry.key, it) }
                }
            })
        println("Resp: $resp")
        assertEquals(expected = HttpStatusCode.OK, actual = resp.status)
    }

    @Test
    fun testWaltVerifierTestRequest() = runTest {
        val waltVerifierTestRequest = testVerifier.initializeAuthorization(
            PresentationDefinition.fromJSONString(presentationDefinitionExample1), ResponseMode.direct_post
        ).authorizationRequest!!.toHttpQueryString().let {
            "openid4vp://authorize?$it"
        }
        val authReq = AuthorizationRequest.fromHttpQueryString(Url(waltVerifierTestRequest).encodedQuery)
        println("Auth req: $authReq")
        val walletSession = testWallet.initializeAuthorization(authReq, 1.minutes, null)
        assertNotNull(actual = walletSession.authorizationRequest!!.presentationDefinition)
        println("Resolved presentation definition: ${walletSession.authorizationRequest!!.presentationDefinition!!.toJSONString()}")
        val tokenResponse = testWallet.processImplicitFlowAuthorization(walletSession.authorizationRequest!!)
        assertNotNull(actual = tokenResponse.vpToken)
        assertNotNull(actual = tokenResponse.presentationSubmission)
        val resp = http.submitForm(walletSession.authorizationRequest!!.responseUri!!,
            parameters {
                tokenResponse.toHttpParameters().forEach { entry ->
                    entry.value.forEach { append(entry.key, it) }
                }
            })
        println("Resp: $resp")
        assertEquals(expected = HttpStatusCode.OK, actual = resp.status)
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


    val ONLINE_TEST: Boolean = false

    @Test
    fun testRequestByReference() = runTest {
        val reqUri = when (ONLINE_TEST) {
            true -> testCreateEntraPresentationRequest()
            else -> "openid-vc://?request_uri=$VP_VERIFIER_BASE_URL/req/6af1f46f-8d91-4eaa-b0c0-f042b7a621f8"
        }
        assertNotNull(actual = reqUri)


        println("REQUEST URI: $reqUri")

        return@runTest
        //testVerifier.start(wait = true)


        val authReq = AuthorizationRequest.fromHttpParametersAuto(parseQueryString(Url(reqUri).encodedQuery).toMap())
        assertEquals(expected = "did:web:entra.walt.id", actual = authReq.clientId)

        val tokenResponse = testWallet.processImplicitFlowAuthorization(authReq)
        val resp =
            http.submitForm(authReq.responseUri ?: authReq.redirectUri ?: throw Exception("response_uri or redirect_uri must be set"),
                parameters {
                    tokenResponse.toHttpParameters().forEach { entry ->
                        entry.value.forEach { append(entry.key, it) }
                    }
                    //append("id_token", ms_id_token) // <--
                })
        println("Resp: $resp")
        assertEquals(expected = HttpStatusCode.OK, actual = resp.status)
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
                "        \"url\": \"https://httpstat.us/200\"\n" +
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
                "            \"type\": \"MyID\"\n" +
                "        }\n" +
                "    ]\n" +
                "}"
        val response = http.post("https://verifiedid.did.msidentity.com/v1.0/verifiableCredentials/createPresentationRequest") {
            header(HttpHeaders.Authorization, accessToken)
            contentType(ContentType.Application.Json)
            setBody(createPresentationRequestBody)
        }
        assertEquals(expected = HttpStatusCode.Created, actual = response.status)
        val responseObj = response.body<JsonObject>()
        return responseObj["url"]?.jsonPrimitive?.content
    }

    @Test
    fun testPresentationDefinitionSerialization() {
        val presDef = PresentationDefinition.fromJSONString(presentationDefinitionExample1)
        presDef.toJSON().keys shouldContain "id"

        val presDefDefaultId = PresentationDefinition(inputDescriptors = presDef.inputDescriptors)
        presDefDefaultId.toJSON().keys shouldContain "id"
    }

    @Test
    fun testIsolatedFunctionToGeneratePresentationSubmission() {
        val w3cVP = "eyJraWQiOiJkaWQ6andrOmV5SnJkSGtpT2lKUFMxQWlMQ0pqY25ZaU9pSkZaREkxTlRFNUlpd2lhMmxrSWpvaWFUbHdXV3N3TWtJNU1VdEpkbmxRVVRFMWEycEJiVVZDUjIweE5UQnBZamhNUVZkNk9WQlJlbGxET0NJc0luZ2lPaUphUVdoSU9GVnpiakJZVVZjMlIwUjFaMEZDUnpVNVJreFRibHBJTjNCUFYxbFVTM0JZUW1wamNFSmpJbjAjMCIsInR5cCI6IkpXVCIsImFsZyI6IkVkRFNBIn0.eyJzdWIiOiJkaWQ6andrOmV5SnJkSGtpT2lKUFMxQWlMQ0pqY25ZaU9pSkZaREkxTlRFNUlpd2lhMmxrSWpvaWFUbHdXV3N3TWtJNU1VdEpkbmxRVVRFMWEycEJiVVZDUjIweE5UQnBZamhNUVZkNk9WQlJlbGxET0NJc0luZ2lPaUphUVdoSU9GVnpiakJZVVZjMlIwUjFaMEZDUnpVNVJreFRibHBJTjNCUFYxbFVTM0JZUW1wamNFSmpJbjAiLCJuYmYiOjE3MzIxOTkxNTYsImlhdCI6MTczMjE5OTIxNiwianRpIjoidmNoVERVa28yMFJIIiwiaXNzIjoiZGlkOmp3azpleUpyZEhraU9pSlBTMUFpTENKamNuWWlPaUpGWkRJMU5URTVJaXdpYTJsa0lqb2lhVGx3V1dzd01rSTVNVXRKZG5sUVVURTFhMnBCYlVWQ1IyMHhOVEJwWWpoTVFWZDZPVkJSZWxsRE9DSXNJbmdpT2lKYVFXaElPRlZ6YmpCWVVWYzJSMFIxWjBGQ1J6VTVSa3hUYmxwSU4zQlBWMWxVUzNCWVFtcGpjRUpqSW4wIiwibm9uY2UiOiJiOTAwYjk3Zi03NDc0LTQ5MDgtOGRiZC03ZDIxZWI1MDI3MmQiLCJhdWQiOiJodHRwOi8vbG9jYWxob3N0OjIyMjIyL29wZW5pZDR2Yy92ZXJpZnkiLCJ2cCI6eyJAY29udGV4dCI6WyJodHRwczovL3d3dy53My5vcmcvMjAxOC9jcmVkZW50aWFscy92MSJdLCJ0eXBlIjpbIlZlcmlmaWFibGVQcmVzZW50YXRpb24iXSwiaWQiOiJ2Y2hURFVrbzIwUkgiLCJob2xkZXIiOiJkaWQ6andrOmV5SnJkSGtpT2lKUFMxQWlMQ0pqY25ZaU9pSkZaREkxTlRFNUlpd2lhMmxrSWpvaWFUbHdXV3N3TWtJNU1VdEpkbmxRVVRFMWEycEJiVVZDUjIweE5UQnBZamhNUVZkNk9WQlJlbGxET0NJc0luZ2lPaUphUVdoSU9GVnpiakJZVVZjMlIwUjFaMEZDUnpVNVJreFRibHBJTjNCUFYxbFVTM0JZUW1wamNFSmpJbjAiLCJ2ZXJpZmlhYmxlQ3JlZGVudGlhbCI6WyJleUpyYVdRaU9pSmthV1E2YTJWNU9ubzJUV3RxYjFKb2NURnFVMDVLWkV4cGNuVlRXSEpHUm5oaFozRnllblJhWVZoSWNVaEhWVlJMU21KalRubDNjQ042TmsxcmFtOVNhSEV4YWxOT1NtUk1hWEoxVTFoeVJrWjRZV2R4Y25wMFdtRllTSEZJUjFWVVMwcGlZMDU1ZDNBaUxDSjBlWEFpT2lKS1YxUWlMQ0poYkdjaU9pSkZaRVJUUVNKOS5leUpwYzNNaU9pSmthV1E2YTJWNU9ubzJUV3RxYjFKb2NURnFVMDVLWkV4cGNuVlRXSEpHUm5oaFozRnllblJhWVZoSWNVaEhWVlJMU21KalRubDNjQ0lzSW5OMVlpSTZJbVJwWkRwcWQyczZaWGxLY21SSWEybFBhVXBRVXpGQmFVeERTbXBqYmxscFQybEtSbHBFU1RGT1ZFVTFTV2wzYVdFeWJHdEphbTlwWVZSc2QxZFhjM2ROYTBrMVRWVjBTbVJ1YkZGVlZFVXhZVEp3UW1KVlZrTlNNakI0VGxSQ2NGbHFhRTFSVm1RMlQxWkNVbVZzYkVSUFEwbHpTVzVuYVU5cFNtRlJWMmhKVDBaV2VtSnFRbGxWVm1NeVVqQlNNVm93UmtOU2VsVTFVbXQ0VkdKc2NFbE9NMEpRVmpGc1ZWTXpRbGxSYlhCcVkwVktha2x1TUNJc0luWmpJanA3SWtCamIyNTBaWGgwSWpwYkltaDBkSEJ6T2k4dmQzZDNMbmN6TG05eVp5OHlNREU0TDJOeVpXUmxiblJwWVd4ekwzWXhJaXdpYUhSMGNITTZMeTl3ZFhKc0xtbHRjMmRzYjJKaGJDNXZjbWN2YzNCbFl5OXZZaTkyTTNBd0wyTnZiblJsZUhRdWFuTnZiaUpkTENKcFpDSTZJblZ5YmpwMWRXbGtPams0T1RFMk9HSTRMVEpqTW1FdE5ESTVZeTA0WWpFeExUZGpNekl6TlRRNVl6TTFPQ0lzSW5SNWNHVWlPbHNpVm1WeWFXWnBZV0pzWlVOeVpXUmxiblJwWVd3aUxDSlBjR1Z1UW1Ga1oyVkRjbVZrWlc1MGFXRnNJbDBzSW01aGJXVWlPaUpLUmtZZ2VDQjJZeTFsWkhVZ1VHeDFaMFpsYzNRZ015QkpiblJsY205d1pYSmhZbWxzYVhSNUlpd2lhWE56ZFdWeUlqcDdJblI1Y0dVaU9sc2lVSEp2Wm1sc1pTSmRMQ0pwWkNJNkltUnBaRHByWlhrNmVqWk5hMnB2VW1oeE1XcFRUa3BrVEdseWRWTllja1pHZUdGbmNYSjZkRnBoV0VoeFNFZFZWRXRLWW1OT2VYZHdJaXdpYm1GdFpTSTZJa3B2WW5NZ1ptOXlJSFJvWlNCR2RYUjFjbVVnS0VwR1Jpa2lMQ0oxY213aU9pSm9kSFJ3Y3pvdkwzZDNkeTVxWm1ZdWIzSm5MeUlzSW1sdFlXZGxJam9pYUhSMGNITTZMeTkzTTJNdFkyTm5MbWRwZEdoMVlpNXBieTkyWXkxbFpDOXdiSFZuWm1WemRDMHhMVEl3TWpJdmFXMWhaMlZ6TDBwR1JsOU1iMmR2VEc5amEzVndMbkJ1WnlKOUxDSnBjM04xWVc1alpVUmhkR1VpT2lJeU1ESTBMVEV4TFRJeFZERTBPakkyT2pVMUxqazVPVGc1TmpVeU1sb2lMQ0psZUhCcGNtRjBhVzl1UkdGMFpTSTZJakl3TWpVdE1URXRNakZVTVRRNk1qWTZOVFl1TURBd01EQTNPRGcyV2lJc0ltTnlaV1JsYm5ScFlXeFRkV0pxWldOMElqcDdJbWxrSWpvaVpHbGtPbXAzYXpwbGVVcHlaRWhyYVU5cFNsQlRNVUZwVEVOS2FtTnVXV2xQYVVwR1drUkpNVTVVUlRWSmFYZHBZVEpzYTBscWIybGhWR3gzVjFkemQwMXJTVFZOVlhSS1pHNXNVVlZVUlRGaE1uQkNZbFZXUTFJeU1IaE9WRUp3V1dwb1RWRldaRFpQVmtKU1pXeHNSRTlEU1hOSmJtZHBUMmxLWVZGWGFFbFBSbFo2WW1wQ1dWVldZekpTTUZJeFdqQkdRMUo2VlRWU2EzaFVZbXh3U1U0elFsQldNV3hWVXpOQ1dWRnRjR3BqUlVwcVNXNHdJaXdpZEhsd1pTSTZXeUpCWTJocFpYWmxiV1Z1ZEZOMVltcGxZM1FpWFN3aVlXTm9hV1YyWlcxbGJuUWlPbnNpYVdRaU9pSjFjbTQ2ZFhWcFpEcGhZekkxTkdKa05TMDRabUZrTFRSaVlqRXRPV1F5T1MxbFptUTVNemcxTXpZNU1qWWlMQ0owZVhCbElqcGJJa0ZqYUdsbGRtVnRaVzUwSWwwc0ltNWhiV1VpT2lKS1JrWWdlQ0IyWXkxbFpIVWdVR3gxWjBabGMzUWdNeUJKYm5SbGNtOXdaWEpoWW1sc2FYUjVJaXdpWkdWelkzSnBjSFJwYjI0aU9pSlVhR2x6SUhkaGJHeGxkQ0J6ZFhCd2IzSjBjeUIwYUdVZ2RYTmxJRzltSUZjelF5QldaWEpwWm1saFlteGxJRU55WldSbGJuUnBZV3h6SUdGdVpDQm9ZWE1nWkdWdGIyNXpkSEpoZEdWa0lHbHVkR1Z5YjNCbGNtRmlhV3hwZEhrZ1pIVnlhVzVuSUhSb1pTQndjbVZ6Wlc1MFlYUnBiMjRnY21WeGRXVnpkQ0IzYjNKclpteHZkeUJrZFhKcGJtY2dTa1pHSUhnZ1ZrTXRSVVJWSUZCc2RXZEdaWE4wSURNdUlpd2lZM0pwZEdWeWFXRWlPbnNpZEhsd1pTSTZJa055YVhSbGNtbGhJaXdpYm1GeWNtRjBhWFpsSWpvaVYyRnNiR1YwSUhOdmJIVjBhVzl1Y3lCd2NtOTJhV1JsY25NZ1pXRnlibVZrSUhSb2FYTWdZbUZrWjJVZ1lua2daR1Z0YjI1emRISmhkR2x1WnlCcGJuUmxjbTl3WlhKaFltbHNhWFI1SUdSMWNtbHVaeUIwYUdVZ2NISmxjMlZ1ZEdGMGFXOXVJSEpsY1hWbGMzUWdkMjl5YTJac2IzY3VJRlJvYVhNZ2FXNWpiSFZrWlhNZ2MzVmpZMlZ6YzJaMWJHeDVJSEpsWTJWcGRtbHVaeUJoSUhCeVpYTmxiblJoZEdsdmJpQnlaWEYxWlhOMExDQmhiR3h2ZDJsdVp5QjBhR1VnYUc5c1pHVnlJSFJ2SUhObGJHVmpkQ0JoZENCc1pXRnpkQ0IwZDI4Z2RIbHdaWE1nYjJZZ2RtVnlhV1pwWVdKc1pTQmpjbVZrWlc1MGFXRnNjeUIwYnlCamNtVmhkR1VnWVNCMlpYSnBabWxoWW14bElIQnlaWE5sYm5SaGRHbHZiaXdnY21WMGRYSnVhVzVuSUhSb1pTQndjbVZ6Wlc1MFlYUnBiMjRnZEc4Z2RHaGxJSEpsY1hWbGMzUnZjaXdnWVc1a0lIQmhjM05wYm1jZ2RtVnlhV1pwWTJGMGFXOXVJRzltSUhSb1pTQndjbVZ6Wlc1MFlYUnBiMjRnWVc1a0lIUm9aU0JwYm1Oc2RXUmxaQ0JqY21Wa1pXNTBhV0ZzY3k0aWZTd2lhVzFoWjJVaU9uc2lhV1FpT2lKb2RIUndjem92TDNjell5MWpZMmN1WjJsMGFIVmlMbWx2TDNaakxXVmtMM0JzZFdkbVpYTjBMVE10TWpBeU15OXBiV0ZuWlhNdlNrWkdMVlpETFVWRVZTMVFURlZIUmtWVFZETXRZbUZrWjJVdGFXMWhaMlV1Y0c1bklpd2lkSGx3WlNJNklrbHRZV2RsSW4xOWZYMHNJbXAwYVNJNkluVnlianAxZFdsa09qazRPVEUyT0dJNExUSmpNbUV0TkRJNVl5MDRZakV4TFRkak16SXpOVFE1WXpNMU9DSXNJbVY0Y0NJNk1UYzJNemN6TlRJeE5pd2lhV0YwSWpveE56TXlNVGs1TWpFMUxDSnVZbVlpT2pFM016SXhPVGt5TVRWOS5MeFJnVVBCMkVzNi1YX2RzNVhWejQxUmRLUkRGY1pVNzc3c09uVFNxOHM4ZlNOT1U2ZDlmUlY4OW1USGp3WTRfdnAyT2N4ZjRGZW9TWWxSbXFOUTRDQSJdfX0.ML2pN5BUCLRLMR5858eNYZSFlzoEHXEbdksSXyvH4wGXkxdmGM5qe2_3IlNSEEXaVA8HBakL-ZoSjLLUFFmNCQ"
        val mdocVP = "o2d2ZXJzaW9uYzEuMGlkb2N1bWVudHOBo2dkb2NUeXBldW9yZy5pc28uMTgwMTMuNS4xLm1ETGxpc3N1ZXJTaWduZWSiam5hbWVTcGFjZXOhcW9yZy5pc28uMTgwMTMuNS4xgGppc3N1ZXJBdXRohEOhASahGCFZAUswggFHMIHuoAMCAQICCDntyHqaePkqMAoGCCqGSM49BAMCMBcxFTATBgNVBAMMDE1ET0MgUk9PVCBDQTAeFw0yNDA1MDIxMzEzMzBaFw0yNTA1MDIxMzEzMzBaMBsxGTAXBgNVBAMMEE1ET0MgVGVzdCBJc3N1ZXIwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQbREg0GIX6hBQPd3kMad6BC5d6cjb0kNowagy-KgpEE3nd3hRrNqRLa6e7wGewS3G61LaSpGFgE9iT1ECuJTeBoyAwHjAMBgNVHRMBAf8EAjAAMA4GA1UdDwEB_wQEAwIHgDAKBggqhkjOPQQDAgNIADBFAiEAjnAEEADd7CojCyWG7MWfis0Vb12TPZNjvF4iY7sKtpgCIBiFqLU3MnppsCJiDwfFxF1ik7hu7ZJ6PwToLMUcrfhjWQHD2BhZAb6mZ3ZlcnNpb25jMS4wb2RpZ2VzdEFsZ29yaXRobWdTSEEtMjU2bHZhbHVlRGlnZXN0c6Fxb3JnLmlzby4xODAxMy41LjGjAFgged2vVMQr7nGEoG2ff1dz7BZW2Jc4LYS0nHWmLGFVKdABWCBgiCmGKddaShm_JdKcLB5-lQ0B-8Ykm70C9BNq4KGiDQJYIEbHmM1HtVAaKfZX22g8iZOq371rLyCe76M6HMu1klyAbWRldmljZUtleUluZm-haWRldmljZUtleaQBAiABIVggIWaYuGQYQuGUjfr3IjJzypxCnnTj3POVrbOhXIxZDeYiWCAa7PHkaFHL7bwwgpxSro73lVCvcNbLl4saE03hzxLvaWdkb2NUeXBldW9yZy5pc28uMTgwMTMuNS4xLm1ETGx2YWxpZGl0eUluZm-jZnNpZ25lZMB4HjIwMjQtMTEtMjFUMTQ6Mjc6MzcuNjg1OTYzMzIyWml2YWxpZEZyb23AeB4yMDI0LTExLTIxVDE0OjI3OjM3LjY4NTk2Mzg5OVpqdmFsaWRVbnRpbMB4HjIwMjUtMTEtMjFUMTQ6Mjc6MzcuNjg1OTYzOTg5WlhA4i7_wW_v0yUDKdaucai5nYmDiU4nmVgKwDx-lOtmO3EyKsVrtXGqDwJ2mlGToGl2CQlUfJiflZvrtBBKwWahbmxkZXZpY2VTaWduZWSiam5hbWVTcGFjZXPYGEGgamRldmljZUF1dGihb2RldmljZVNpZ25hdHVyZYRDoQEmoRghgPZYQPYjMkYGps2PPtNfQEZjC8IEoGKJHHs40iiWSu1goMyALJvHw5rQlsEX9emOUrQ281hjI3OAtSK1q9edMwZtX91mc3RhdHVzAA=="
        val sdJwtVPs = listOf("eyJ4NWMiOlsiLS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tXG5NSUlCUnpDQjdxQURBZ0VDQWdnNTdjaDZtbmo1S2pBS0JnZ3Foa2pPUFFRREFqQVhNUlV3RXdZRFZRUUREQXhOUkU5RElGSlBUMVFnUTBFd0hoY05NalF3TlRBeU1UTXhNek13V2hjTk1qVXdOVEF5TVRNeE16TXdXakFiTVJrd0Z3WURWUVFEREJCTlJFOURJRlJsYzNRZ1NYTnpkV1Z5TUZrd0V3WUhLb1pJemowQ0FRWUlLb1pJemowREFRY0RRZ0FFRzBSSU5CaUYrb1FVRDNkNURHbmVnUXVYZW5JMjlKRGFNR29NdmlvS1JCTjUzZDRVYXpha1MydW51OEJuc0V0eHV0UzJrcVJoWUJQWWs5UkFyaVUzZ2FNZ01CNHdEQVlEVlIwVEFRSC9CQUl3QURBT0JnTlZIUThCQWY4RUJBTUNCNEF3Q2dZSUtvWkl6ajBFQXdJRFNBQXdSUUloQUk1d0JCQUEzZXdxSXdzbGh1ekZuNHJORlc5ZGt6MlRZN3hlSW1PN0NyYVlBaUFZaGFpMU56SjZhYkFpWWc4SHhjUmRZcE80YnUyU2VqOEU2Q3pGSEszNFl3PT1cbi0tLS0tRU5EIENFUlRJRklDQVRFLS0tLS0iXSwia2lkIjoiOXZ1YUp5VXhSeDRLbUh5b1o5a2pKeE1zX21qcG5uZi1tUE05blBNRzUxQSIsInR5cCI6InZjK3NkLWp3dCIsImFsZyI6IkVTMjU2In0.eyJmYW1pbHlfbmFtZSI6IkRvZSIsImdpdmVuX25hbWUiOiJKb2huIiwiaWQiOiJ1cm46dXVpZDpiZmExNzhmOC1mMWI3LTQ2OTAtYTUzNS0xYzI5MDliMDJlMGEiLCJpYXQiOjE3MzIxOTkyODcsIm5iZiI6MTczMjE5OTI4NywiZXhwIjoxNzYzNzM1Mjg3LCJpc3MiOiJodHRwOi8vbG9jYWxob3N0OjIyMjIyIiwiY25mIjp7Imp3ayI6eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiZEJaVkRmSFpJNXJyYXB0VHg1Y2p6ZE1JMnA3aXRxZzZaUDlGZTliaHllayIsIngiOiJJV2FZdUdRWVF1R1VqZnIzSWpKenlweENublRqM1BPVnJiT2hYSXhaRGVZIiwieSI6Ikd1eng1R2hSeS0yOE1JS2NVcTZPOTVWUXIzRFd5NWVMR2hOTjRjOFM3MmsifX0sInZjdCI6Imh0dHA6Ly9sb2NhbGhvc3Q6MjIyMjIvaWRlbnRpdHlfY3JlZGVudGlhbCIsIl9zZCI6WyIzMHotMDF3U1RkM2NsRl9lMTB4czFPVDJWMDNnWGd0akZpSURTWE5pVmw0Il19.IpTd1Ap2TDSkdAFOkUpjRa87yWQeV1c3FyOYsTCmhETccCQp2RmOErFcadyT5Uk3Q1EjGzyT2k9DeQ0tJNVrrg~eyJraWQiOiJkQlpWRGZIWkk1cnJhcHRUeDVjanpkTUkycDdpdHFnNlpQOUZlOWJoeWVrIiwidHlwIjoia2Irand0IiwiYWxnIjoiRVMyNTYifQ.eyJpYXQiOjE3MzIxOTkyODcsImF1ZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6MjIyMjIvb3BlbmlkNHZjL3ZlcmlmeSIsIm5vbmNlIjoiMzU0NWIzYzQtYmE4MS00YjhhLTk5MGUtMjBmOWY5OTc4ZjVhIiwic2RfaGFzaCI6ImJiTlpIeHppaEI5M3NpZGY5bXNtUDJaaVlmOEpNQloxMnFhckJOaU9ZNmcifQ.HidmwWmPUX3PKjtavfxkJpnS7QPsoUF7FAfNfYD89IF2_lfPf3fk6qBrQ_xcyu2y_1HjR4xJ_7BEq4szCoSMaw",
            "eyJ4NWMiOlsiLS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tXG5NSUlCUnpDQjdxQURBZ0VDQWdnNTdjaDZtbmo1S2pBS0JnZ3Foa2pPUFFRREFqQVhNUlV3RXdZRFZRUUREQXhOUkU5RElGSlBUMVFnUTBFd0hoY05NalF3TlRBeU1UTXhNek13V2hjTk1qVXdOVEF5TVRNeE16TXdXakFiTVJrd0Z3WURWUVFEREJCTlJFOURJRlJsYzNRZ1NYTnpkV1Z5TUZrd0V3WUhLb1pJemowQ0FRWUlLb1pJemowREFRY0RRZ0FFRzBSSU5CaUYrb1FVRDNkNURHbmVnUXVYZW5JMjlKRGFNR29NdmlvS1JCTjUzZDRVYXpha1MydW51OEJuc0V0eHV0UzJrcVJoWUJQWWs5UkFyaVUzZ2FNZ01CNHdEQVlEVlIwVEFRSC9CQUl3QURBT0JnTlZIUThCQWY4RUJBTUNCNEF3Q2dZSUtvWkl6ajBFQXdJRFNBQXdSUUloQUk1d0JCQUEzZXdxSXdzbGh1ekZuNHJORlc5ZGt6MlRZN3hlSW1PN0NyYVlBaUFZaGFpMU56SjZhYkFpWWc4SHhjUmRZcE80YnUyU2VqOEU2Q3pGSEszNFl3PT1cbi0tLS0tRU5EIENFUlRJRklDQVRFLS0tLS0iXSwia2lkIjoiOXZ1YUp5VXhSeDRLbUh5b1o5a2pKeE1zX21qcG5uZi1tUE05blBNRzUxQSIsInR5cCI6InZjK3NkLWp3dCIsImFsZyI6IkVTMjU2In0.eyJmYW1pbHlfbmFtZSI6IkRvZSIsImdpdmVuX25hbWUiOiJKb2huIiwiaWQiOiJ1cm46dXVpZDpiZmExNzhmOC1mMWI3LTQ2OTAtYTUzNS0xYzI5MDliMDJlMGEiLCJpYXQiOjE3MzIxOTkyODcsIm5iZiI6MTczMjE5OTI4NywiZXhwIjoxNzYzNzM1Mjg3LCJpc3MiOiJodHRwOi8vbG9jYWxob3N0OjIyMjIyIiwiY25mIjp7Imp3ayI6eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiZEJaVkRmSFpJNXJyYXB0VHg1Y2p6ZE1JMnA3aXRxZzZaUDlGZTliaHllayIsIngiOiJJV2FZdUdRWVF1R1VqZnIzSWpKenlweENublRqM1BPVnJiT2hYSXhaRGVZIiwieSI6Ikd1eng1R2hSeS0yOE1JS2NVcTZPOTVWUXIzRFd5NWVMR2hOTjRjOFM3MmsifX0sInZjdCI6Imh0dHA6Ly9sb2NhbGhvc3Q6MjIyMjIvaWRlbnRpdHlfY3JlZGVudGlhbCIsIl9zZCI6WyIzMHotMDF3U1RkM2NsRl9lMTB4czFPVDJWMDNnWGd0akZpSURTWE5pVmw0Il19.IpTd1Ap2TDSkdAFOkUpjRa87yWQeV1c3FyOYsTCmhETccCQp2RmOErFcadyT5Uk3Q1EjGzyT2k9DeQ0tJNVrrg~eyJraWQiOiJkQlpWRGZIWkk1cnJhcHRUeDVjanpkTUkycDdpdHFnNlpQOUZlOWJoeWVrIiwidHlwIjoia2Irand0IiwiYWxnIjoiRVMyNTYifQ.eyJpYXQiOjE3MzIxOTkzMTUsImF1ZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6MjIyMjIvb3BlbmlkNHZjL3ZlcmlmeSIsIm5vbmNlIjoiOTBiZmMyNjYtM2QzYi00M2E5LTg3ZTktZjY2ZjkxZDg4MGY3Iiwic2RfaGFzaCI6ImJiTlpIeHppaEI5M3NpZGY5bXNtUDJaaVlmOEpNQloxMnFhckJOaU9ZNmcifQ.azY2a5mnI-zUYYEU2_TPgnusEQsbrXYP5Jfn1XJytKR3NJooarESL92pj6x0p4RegK1qRwJ6DZ50ydCB-Vgsqw")
        val presentationDefinition = PresentationDefinition(randomUUID(), inputDescriptors = listOf(
            InputDescriptor(id = "OpenBadgeCredential", format = mapOf(VCFormat.jwt_vc_json to VCFormatDefinition())),
            InputDescriptor(id = DeviceResponse.fromCBORBase64URL(mdocVP).documents.first().docType.value,
                format = mapOf(VCFormat.mso_mdoc to VCFormatDefinition())),
        ).plus(sdJwtVPs.map { InputDescriptor(id = SDJwtVC.parse(it).vct!!,
            format = mapOf(VCFormat.sd_jwt_vc to VCFormatDefinition())) }))

        val presSubmission = OpenID4VP.generatePresentationSubmission(presentationDefinition,
            w3cVP = w3cVP, null, null)
            //mapOf(VCFormat.jwt_vp to listOf(w3cVPs.first())))
        presSubmission.descriptorMap
    }
}
