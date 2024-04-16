import id.walt.credentials.PresentationBuilder
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.MultiBaseUtils
import id.walt.did.dids.document.DidEbsiBaseDocument
import id.walt.did.dids.registrar.dids.DidKeyCreateOptions
import id.walt.did.dids.registrar.dids.DidWebCreateOptions
import id.walt.did.dids.registrar.local.key.DidKeyRegistrar
import id.walt.did.dids.registrar.local.web.DidWebRegistrar
import id.walt.did.dids.resolver.DidResolver
import id.walt.did.dids.resolver.local.DidEbsiResolver
import id.walt.did.utils.randomUUID
import id.walt.ebsi.eth.TransactionService
import id.walt.ebsi.rpc.EbsiRpcRequests
import id.walt.ebsi.rpc.SignedTransactionResponse
import id.walt.ebsi.rpc.UnsignedTransactionResponse
import id.walt.oid4vc.OpenID4VCI
import id.walt.oid4vc.OpenID4VP
import id.walt.oid4vc.data.*
import id.walt.oid4vc.data.dif.DescriptorMapping
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.oid4vc.data.dif.PresentationSubmission
import id.walt.oid4vc.interfaces.PresentationResult
import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.oid4vc.requests.CredentialRequest
import id.walt.oid4vc.requests.TokenRequest
import id.walt.oid4vc.responses.AuthorizationCodeResponse
import id.walt.oid4vc.responses.CredentialResponse
import id.walt.oid4vc.responses.TokenResponse
import id.walt.oid4vc.util.http
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.*
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.util.UUID
import kotlin.test.*
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class DidCreationTest {

    private val registrar = DidWebRegistrar()

    @Test
    fun checkDidDocumentIsValidJson() = runTest {
        val result = registrar.register(DidWebCreateOptions("localhost", "/xyz/abc").also { println(it.config) })

        val didDoc1 = result.didDocument.toJsonObject()
        val didDoc2 = Json.parseToJsonElement(
            result.didDocument.toString()
        ).jsonObject

        assertEquals(didDoc1, didDoc2)
    }

    @Test
    fun checkDidDocumentNotWrappedInContent() = runTest {
        val result = registrar.register(DidWebCreateOptions("localhost", "/xyz/abc"))

        val didDoc1 = result.didDocument.toJsonObject()
        assertNull(didDoc1["content"])

        val didDoc2 = Json.parseToJsonElement(
            result.didDocument.toString()
        ).jsonObject
        assertNull(didDoc2["content"])
    }

    @Test
    fun checkContextExistsInDid() = runTest {
        val result = registrar
            .register(DidWebCreateOptions("localhost", "/abc/xyz"))

        val didDoc1 = result.didDocument.toString()
        println("DID doc: $didDoc1")
        assertContains(didDoc1, "\"@context\":")

        val didDoc2 = result.didDocument.toJsonObject()
        assertNotNull(didDoc2["@context"])
        assertNotNull(didDoc2["@context"]?.jsonArray)
        assertTrue { didDoc2["@context"]!!.jsonArray.isNotEmpty() }
    }

    @Test
    fun checkReferencedDidMethods() = runTest {
        val resultWeb = registrar
            .register(DidWebCreateOptions("localhost", "/abc/xyz"))
        val resultKey = DidKeyRegistrar().register(DidKeyCreateOptions())

        arrayOf(resultKey, resultWeb).forEach { result ->
            val didDoc1 = result.didDocument.toString()
            println("DID doc: $didDoc1")
            val id =
                result.didDocument["verificationMethod"]?.jsonArray?.get(0)?.jsonObject?.get("id")?.jsonPrimitive?.content
            val refId = result.didDocument["assertionMethod"]?.jsonArray?.get(0)?.jsonPrimitive?.content
            assertEquals(id, refId)
        }
    }

    val CLIENT_MOCK_PORT = 5000
    val CLIENT_MOCK_URL = "https://39a9-62-178-27-231.ngrok-free.app/client-mock"//"http://192.168.0.122:5000/client-mock"
    val CLIENT_MAIN_KEY = runBlocking { JWKKey.generate(KeyType.secp256k1) }
    val CLIENT_VCSIGN_KEY = runBlocking { JWKKey.generate(KeyType.secp256r1) }
    fun startClientMockServer() {
        embeddedServer(Netty, port = CLIENT_MOCK_PORT) {
            install(ContentNegotiation) {
                json()
            }
            routing {
                get("/client-mock") {
                    println("CLIENT MOCK called")
                    println(call.parameters.toString())
                }
                get("/client-mock/jwks") {
                    call.respond(buildJsonObject {
                        put("keys", buildJsonArray {
                            add(CLIENT_MAIN_KEY.exportJWKObject())
                            add(CLIENT_VCSIGN_KEY.exportJWKObject().also { println(it.toString()) })
                        })
                    })
                }
            }
        }.start()
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun ebsiDidOnboarding() = runTest {
        startClientMockServer()
        val didMSI = UUID.randomUUID().let { ByteBuffer.allocate(17)
            .put(0x01)
            .putLong(1, it.mostSignificantBits)
            .putLong(9, it.leastSignificantBits).array()
        }.let { MultiBaseUtils.encodeMultiBase58Btc(it) }
        assertEquals('z', didMSI.first())
        val did = "did:ebsi:$didMSI"
        val taoIssuer = "https://api-conformance.ebsi.eu/conformance/v3/issuer-mock"
        //val taoIssuer = "http://localhost:3000/conformance/v3/issuer-mock"
        val issuerMetadata = OpenID4VCI.resolveCIProviderMetadata(taoIssuer)
        assertEquals(taoIssuer, issuerMetadata.credentialIssuer)
        val authMetadata = OpenID4VCI.resolveAuthProviderMetadata(issuerMetadata.authorizationServer!!)
        assertEquals(issuerMetadata.authorizationServer, authMetadata.issuer)

        val clientId = CLIENT_MOCK_URL
        val codeChallenge = randomUUID()
        val authReq = AuthorizationRequest(
            responseType = setOf(ResponseType.Code), clientId,
            scope = setOf("openid"),
            redirectUri = CLIENT_MOCK_URL,
            authorizationDetails = listOf(
                AuthorizationDetails.fromLegacyCredentialParameters(
                    CredentialFormat.jwt_vc,
                    issuerMetadata.credentialsSupported!!.first { it.types?.contains("VerifiableAuthorisationToOnboard") == true }.types!!,
                    locations = listOf(issuerMetadata.credentialIssuer!!)
                    )
            ),
            clientMetadata = OpenIDClientMetadata(customParameters = mapOf(
                "jwks_uri" to JsonPrimitive("$CLIENT_MOCK_URL/jwks"),
                "authorization_endpoint" to JsonPrimitive("openid:")
            )),
            codeChallenge = codeChallenge, codeChallengeMethod = "plain"
        )

        val signedRequestObject = CLIENT_VCSIGN_KEY.signJws(
            authReq.toRequestObjectPayload(clientId, authMetadata.issuer!!).toString().toByteArray(),
            mapOf("kid" to CLIENT_VCSIGN_KEY.getKeyId())
        )

        val httpResp = http.get(authMetadata.authorizationEndpoint!!) {
            url { parameters.appendAll(parametersOf(authReq.toHttpParametersWithRequestObject(signedRequestObject)))
            println(buildString())
            }
        }
        val idTokenReqUrl = httpResp.headers["location"]!!
        val idTokenReq = AuthorizationRequest.fromHttpParametersAuto(Url(idTokenReqUrl).parameters.toMap())
        assertEquals(ResponseMode.direct_post, idTokenReq.responseMode)
        assertContains(idTokenReq.scope, "openid")
        assertContains(idTokenReq.responseType, ResponseType.IdToken)
        assertNotNull(idTokenReq.redirectUri)

        val idToken = CLIENT_VCSIGN_KEY.signJws(
            buildJsonObject {
                put("iss", did)
                put("sub", did)
                put("aud", authMetadata.issuer)
                put("exp", Instant.DISTANT_FUTURE.epochSeconds)
                put("iat", Clock.System.now().epochSeconds)
                put("state", idTokenReq.state)
                put("nonce", idTokenReq.nonce)
            }.toString().toByteArray(),
            mapOf("kid" to "$did#${CLIENT_VCSIGN_KEY.getKeyId()}", "typ" to "JWT")
        )

        val idTokenResp = TokenResponse.success(idToken = idToken, state = idTokenReq.state)
        val authResp = http.submitForm(idTokenReq.redirectUri!!, parametersOf(idTokenResp.toHttpParameters()))
        assertEquals(302, authResp.status.value)
        val codeResp = AuthorizationCodeResponse.fromHttpQueryString(Url(authResp.headers["Location"]!!).encodedQuery)
        assertNotNull(codeResp.code)

        val tokenReq = TokenRequest(
            GrantType.authorization_code,
            CLIENT_MOCK_URL, CLIENT_MOCK_URL,
            codeResp.code,
            codeVerifier = codeChallenge,
            customParameters = mapOf(
                "client_assertion" to listOf(CLIENT_VCSIGN_KEY.signJws(
                    buildJsonObject {
                        put("iss", CLIENT_MOCK_URL)
                        put("sub", CLIENT_MOCK_URL)
                        put("aud", authMetadata.issuer)
                        put("jti", randomUUID())
                        put("exp", Instant.DISTANT_FUTURE.epochSeconds)
                        put("iat", Clock.System.now().epochSeconds)
                    }.toString().toByteArray(),
                    mapOf("kid" to CLIENT_VCSIGN_KEY.getKeyId(), "typ" to "JWT")),
                ),
                "client_assertion_type" to listOf("urn:ietf:params:oauth:client-assertion-type:jwt-bearer")
            )
        )
        val tokenRespRaw = http.submitForm(authMetadata.tokenEndpoint!!, parametersOf(tokenReq.toHttpParameters()))
        assertEquals(HttpStatusCode.OK, tokenRespRaw.status)
        val tokenResp = TokenResponse.fromJSONString(tokenRespRaw.bodyAsText())
        assertNotNull(tokenResp.accessToken)
        println(tokenResp.accessToken)

        val jwtProof = ProofOfPossession.JWTProofBuilder(
            issuerMetadata.credentialIssuer!!,
            clientId, tokenResp.cNonce,
            "$did#${CLIENT_VCSIGN_KEY.getKeyId()}"
        ).build(CLIENT_VCSIGN_KEY)
        println(jwtProof.jwt)

        val credReq = CredentialRequest(
            CredentialFormat.jwt_vc,
            proof = jwtProof,
            types = issuerMetadata.credentialsSupported!!.first { it.types?.contains("VerifiableAuthorisationToOnboard") == true }.types!!
        )
        val credRespRaw = http.post(issuerMetadata.credentialEndpoint!!) {
            bearerAuth(tokenResp.accessToken!!)
            contentType(ContentType.Application.Json)
            setBody(credReq.toJSONString())
        }
        println(credRespRaw.bodyAsText())
        assertEquals(HttpStatusCode.OK, credRespRaw.status)
        val credResp = CredentialResponse.fromJSONString(credRespRaw.bodyAsText())
        println(credResp.credential)

        val presDefResp = http.get("https://api-conformance.ebsi.eu/authorisation/v4/presentation-definitions") {
            parameter("scope", "openid didr_invite")
        }
        println(presDefResp.bodyAsText())
        val presDef = PresentationDefinition.fromJSONString(presDefResp.bodyAsText())

        //OpenID4VP.createPresentationRequest(PresentationDefinitionParameter.fromPresentationDefinitionScope("openid didr_invite"),
        //    clientId = CLIENT_MOCK_URL, clientIdScheme = ClientIdScheme.RedirectUri, )

        val tokenResponse = OpenID4VP.generatePresentationResponse(
            PresentationResult(listOf(JsonPrimitive(
                PresentationBuilder().also {
                    it.did = did
                    it.addCredential(credResp.credential!!)
                    it.nonce = tokenResp.cNonce
                    it.audience = "https://api-conformance.ebsi.eu/authorisation/v4"
                    it.jwtExpiration = Clock.System.now().plus(1.toDuration(DurationUnit.MINUTES))
                }.buildPresentationJsonString().let {
                    CLIENT_VCSIGN_KEY.signJws(
                        it.encodeToByteArray(),
                        headers = mapOf("kid" to "$did#${CLIENT_VCSIGN_KEY.getKeyId()}", "typ" to "JWT")
                    )
                }
            )), PresentationSubmission(
                presDef.id, presDef.id, presDef.inputDescriptors.mapIndexed { index, inputDescriptor ->
                    DescriptorMapping(inputDescriptor.id, presDef.format!!.keys.first(), DescriptorMapping.vpPath(1,0),
                        null) //DescriptorMapping("0", inputDescriptor.format!!.keys.first(), "${DescriptorMapping.vpPath(1,0)}.verifiableCredential[$index]"))
                }
            ).also { println(it.toJSONString()) }), grantType = GrantType.vp_token, scope = "openid didr_invite"
        )
        println(tokenResponse.vpToken.toString())
        val accessTokenResponse = http.submitForm("https://api-conformance.ebsi.eu/authorisation/v4/token",
            formParameters = parametersOf(tokenResponse.toHttpParameters())) {}.bodyAsText().let { TokenResponse.fromJSONString(it) }
        assertNotNull(accessTokenResponse.accessToken)
        println(accessTokenResponse.accessToken)

        // insert DID document:
        val insertDidRpcRequest = EbsiRpcRequests.generateInsertDidDocumentRequest(1, did, CLIENT_MAIN_KEY,
            DidEbsiBaseDocument().let { Json.encodeToJsonElement(it) })
        val insertDidHttpResponse = http.post("https://api-conformance.ebsi.eu/did-registry/v5/jsonrpc") {
            bearerAuth(accessTokenResponse.accessToken!!)
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToJsonElement(insertDidRpcRequest).also {
                    println(it)
            })
        }
        println(insertDidHttpResponse.bodyAsText())
        assertEquals(HttpStatusCode.OK, insertDidHttpResponse.status)
        val insertDidRpcResponse = Json.decodeFromString<UnsignedTransactionResponse>(insertDidHttpResponse.bodyAsText())
        assertEquals(1, insertDidRpcResponse.id)

        // sign transaction
        val signedTransaction = TransactionService.signTransaction(CLIENT_MAIN_KEY, insertDidRpcResponse.result)
        val sendSignedTransactionRequest = EbsiRpcRequests.generateSendSignedTransactionRequest(
            1, insertDidRpcResponse.result, signedTransaction)
        val sendSignedTransactionHttpResponse = http.post("https://api-conformance.ebsi.eu/did-registry/v5/jsonrpc") {
            bearerAuth(accessTokenResponse.accessToken!!)
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToJsonElement(sendSignedTransactionRequest).also {
                println(it)
            })
        }
        println(sendSignedTransactionHttpResponse.bodyAsText())
        assertEquals(HttpStatusCode.OK, sendSignedTransactionHttpResponse.status)
        val sendSignedTransactionRpcResponse = Json.decodeFromString<SignedTransactionResponse>(sendSignedTransactionHttpResponse.bodyAsText())
        println(sendSignedTransactionRpcResponse.result)

        Thread.sleep(5000)
        val resolveDidHttpResponse = http.get("https://api-conformance.ebsi.eu/did-registry/v5/identifiers/${URLEncoder.encode(did)}")
        assertEquals(HttpStatusCode.OK, resolveDidHttpResponse.status)
        println(resolveDidHttpResponse.bodyAsText())
        val resolverResult = DidEbsiResolver().resolve(did)
        assertEquals(true, resolverResult.isSuccess)
        println(resolverResult.getOrNull()!!.toString())
        // TODO:
        //      register other key types for did?
        //      VerifiableAccreditationToAttest
        //      more steps from https://hub.ebsi.eu/conformance/build-solutions/accredit-and-authorise-functional-flows
        //      Reorganize test code into production code!!

    }
}
