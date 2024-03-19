import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.LocalKey
import id.walt.crypto.utils.MultiBaseUtils
import id.walt.did.dids.registrar.dids.DidKeyCreateOptions
import id.walt.did.dids.registrar.dids.DidWebCreateOptions
import id.walt.did.dids.registrar.local.key.DidKeyRegistrar
import id.walt.did.dids.registrar.local.web.DidWebRegistrar
import id.walt.did.utils.randomUUID
import id.walt.oid4vc.OpenID4VCI
import id.walt.oid4vc.data.*
import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.oid4vc.requests.CredentialRequest
import id.walt.oid4vc.requests.TokenRequest
import id.walt.oid4vc.responses.AuthorizationCodeResponse
import id.walt.oid4vc.responses.AuthorizationDirectPostResponse
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
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.*
import java.nio.ByteBuffer
import java.util.UUID
import kotlin.test.*

class DidCreationTest {

    private val registrar = DidWebRegistrar()

    @Test
    fun checkDidDocumentIsValidJson() = runTest {
        val result = registrar.register(DidWebCreateOptions("localhost", "/xyz/abc"))

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
    val CLIENT_MOCK_URL = "http://localhost:5000/client-mock"
    val CLIENT_MAIN_KEY = runBlocking { LocalKey.generate(KeyType.secp256k1) }
    val CLIENT_VCSIGN_KEY = runBlocking { LocalKey.generate(KeyType.secp256r1) }
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
                            add(CLIENT_VCSIGN_KEY.exportJWKObject())
                        })
                    })
                }
            }
        }.start()
    }

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
        //val taoIssuer = "https://api-conformance.ebsi.eu/conformance/v3/issuer-mock"
        val taoIssuer = "http://localhost:3000/conformance/v3/issuer-mock"
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
        assertEquals(ResponseMode.DirectPost, idTokenReq.responseMode)
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
    }
}
