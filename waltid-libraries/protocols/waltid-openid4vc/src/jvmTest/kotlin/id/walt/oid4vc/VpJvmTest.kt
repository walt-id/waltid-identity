package id.walt.oid4vc

import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.crypto.utils.UuidUtils.randomUUIDString
import id.walt.did.dids.DidService
import id.walt.oid4vc.data.*
import id.walt.oid4vc.data.dif.*
import id.walt.oid4vc.interfaces.PresentationResult
import id.walt.oid4vc.providers.CredentialWalletConfig
import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.oid4vc.responses.TokenResponse
import id.walt.policies.policies.JwtSignaturePolicy
import id.walt.w3c.utils.VCFormat
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
import kotlinx.serialization.json.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import kotlin.test.*
import kotlin.time.Duration.Companion.minutes

/*
 * TODO: remove this test might also be a valid solution - it needs to be verified, if this
 *       is already be covered in other tests
 */
@Disabled("entra.walt.id is not available anymore - need a new did store")
class VpJvmTest {

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
        assertEquals(expected = 1, actual = pd3.submissionRequirements.size)
        assertEquals(expected = "Citizenship Information", actual = pd3.submissionRequirements.first().name)
        assertEquals(expected = SubmissionRequirementRule.pick, actual = pd3.submissionRequirements.first().rule)
        assertEquals(expected = 1, actual = pd3.submissionRequirements.first().count)
        assertEquals(expected = "A", actual = pd3.submissionRequirements.first().from)
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

        return@runTest // FAILING TEST DUE TO MISSING DEPLOYMENT AT `https://entra.walt.id/holder/did.json`

        assertEquals(expected = HttpStatusCode.Found, actual = authResp.status)
        assertContains(iterable = authResp.headers.names(), element = HttpHeaders.Location)
        val redirectUrl = Url(authResp.headers[HttpHeaders.Location]!!)
        val tokenResponse = TokenResponse.fromHttpParameters(redirectUrl.parameters.toMap())
        assertNotNull(actual = tokenResponse.vpToken)

        // vpToken is NOT a string, but JSON ELEMENT
        // this will break without .content(): (if JsonPrimitive and not JsonArray!)
        assertTrue(actual = JwtSignaturePolicy().verify(tokenResponse.vpToken.jsonPrimitive.content, null, mapOf()).isSuccess)
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
            ), ResponseMode.query, setOf(ResponseType.VpToken), "http://blank", randomUUIDString(),
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
                    assertTrue(actual = tokenResponse.vpToken.instanceOf(JsonObject::class))
                else {
                    assertTrue(tokenResponse.vpToken.instanceOf(JsonPrimitive::class))
                    assertFalse(actual = tokenResponse.vpToken.jsonPrimitive.isString) // should be an unquoted string if vp_token is a single string
                }
            } else {
                assertTrue(actual = tokenResponse.vpToken!!.instanceOf(JsonArray::class))
                tokenResponse.vpToken.jsonArray.forEach {
                    if (it is JsonPrimitive)
                        assertTrue(actual = it.isString) // string elements in the array must be quoted strings
                    else assertTrue(actual = it.instanceOf(JsonObject::class))
                }
            }

            val url = tokenResponse.toRedirectUri("http://blank", ResponseMode.query)
            println(url)
            val parsedResponse = TokenResponse.fromHttpParameters(Url(url).parameters.toMap())
            assertNotNull(actual = parsedResponse.vpToken)
            val parsedVpTokenParam = VpTokenParameter.fromJsonElement(parsedResponse.vpToken)
            assertEquals(expected = param.vpTokenStrings, actual = parsedVpTokenParam.vpTokenStrings)
            assertContentEquals(expected = param.vpTokenObjects, actual = parsedVpTokenParam.vpTokenObjects)
        }
    }

    private val prettyPrintJson = Json { prettyPrint = true }

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
        val encoded = prettyPrintJson.encodeToString(decoded)
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
            "openid4vp://authorize?response_type=vp_token&presentation_definition={\"id\":\"OpenBadgeCredential\",\"input_descriptors\":[{\"id\":\"OpenBadge Credential\",\"format\":{\"jwt_vc\":{\"proof_type\":[\"ES256\",\"ES256\",\"ES256K\",\"PS256\"]},\"jwt_vp\":{\"proof_type\":[\"ES256\",\"ES256\",\"ES256K\",\"PS256\"]},\"ldp_vc\":{\"proof_type\":[\"Ed25519Signature2018\",\"Ed25519Signature2020\",\"JsonWebSignature2020\",\"EcdsaSecp256k1Signature2019\"]},\"ldp_vp\":{\"proof_type\":[\"Ed25519Signature2018\",\"Ed25519Signature2020\",\"JsonWebSignature2020\",\"EcdsaSecp256k1Signature2019\"]}},\"constraints\":{\"fields\":[{\"path\":[\"$.type\"],\"optional\":false}]}}]}&client_id=https://oidc4vp.univerifier.io/1.0/authorization/direct_post&response_mode=direct_post&response_uri=https://oidc4vp.univerifier.io/1.0/authorization/direct_post&state=DUoqgmKcmoPsUuURKNJV&nonce=d292a622-82ec-4608-a873-356deae18bee"
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
        val encoded = prettyPrintJson.encodeToString(decoded)
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
        val encoded = prettyPrintJson.encodeToString(decoded)
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

        val walletSession = testWallet.initializeAuthorization(verifierSession.authorizationRequest, 1.minutes, null)
        println("Wallet session: $walletSession")

        return@runTest // FAILING TEST DUE TO MISSING DEPLOYMENT AT `https://entra.walt.id/holder/did.json`

        val tokenResponse = testWallet.processImplicitFlowAuthorization(walletSession.authorizationRequest!!)
        println("Token response: $tokenResponse")
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

        return@runTest // FAILING TEST DUE TO MISSING DEPLOYMENT AT `https://entra.walt.id/holder/did.json`

        val tokenResponse = testWallet.processImplicitFlowAuthorization(walletSession.authorizationRequest!!)
        println("Token response: $tokenResponse")
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
        val clientSecret = "<your-client-secret>"
        val response = http.submitForm("https://login.microsoftonline.com/$tenantId/oauth2/v2.0/token", parameters {
            append("client_id", clientId)
            append("scope", "3db474b9-6a0c-4840-96ac-1fceb342124f/.default")
            append("client_secret", clientSecret)
            append("grant_type", "client_credentials")
        }).body<JsonObject>()
        return "${response["token_type"]!!.jsonPrimitive.content} ${response["access_token"]!!.jsonPrimitive.content}"
    }

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
}
