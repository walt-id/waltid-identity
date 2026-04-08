package id.walt.webwallet.service.exchange

import id.walt.dcql.models.ClaimsQuery
import id.walt.dcql.models.CredentialFormat as DcqlCredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.models.meta.JwtVcJsonMeta
import id.walt.oid4vc.data.CredentialFormat
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.http.Url
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import id.walt.webwallet.db.models.WalletCredential
import java.net.InetSocketAddress
import java.util.Base64

@OptIn(ExperimentalUuidApi::class, ExperimentalSerializationApi::class)
class OpenId4VpPresentationServiceTest {
    private val json = Json { encodeDefaults = false }

    private val query = DcqlQuery(
        credentials = listOf(
            CredentialQuery(
                id = "degree",
                format = DcqlCredentialFormat.JWT_VC_JSON,
                meta = JwtVcJsonMeta(
                    typeValues = listOf(listOf("VerifiableCredential", "UniversityDegreeCredential")),
                ),
                claims = listOf(
                    ClaimsQuery(pathStrings = listOf("credentialSubject", "degree", "type")),
                ),
            ),
        ),
    )

    @Test
    fun `resolvePresentationRequest normalizes direct OpenID4VP requests`() {
        val http = HttpClient()
        try {
            val service = OpenId4VpPresentationService(http, mockk(relaxed = true))
            val request = AuthorizationRequest(
                clientId = "verifier2",
                responseMode = OpenID4VPResponseMode.DIRECT_POST,
                responseUri = "https://verifier.example/response",
                nonce = "nonce-123",
                dcqlQuery = query,
            ).toHttpUrl().toString()

            val resolvedRequest = runBlocking {
                service.resolvePresentationRequest(request)
            }
            val resolvedUrl = Url(resolvedRequest)

            assertEquals("verifier2", resolvedUrl.parameters["client_id"])
            assertEquals("https://verifier.example/response", resolvedUrl.parameters["response_uri"])
            assertTrue(resolvedUrl.parameters["dcql_query"]?.contains("UniversityDegreeCredential") == true)
        } finally {
            http.close()
        }
    }

    @Test
    fun `buildWalletPresentationRequest keeps values JSON encoded for wallet library parsing`() {
        val http = HttpClient()
        try {
            val service = OpenId4VpPresentationService(http, mockk(relaxed = true))
            val authorizationRequest = AuthorizationRequest(
                clientId = "verifier2",
                responseMode = OpenID4VPResponseMode.DIRECT_POST,
                responseUri = "https://verifier.example/response",
                nonce = "nonce-123",
                dcqlQuery = query,
            )
            val request = authorizationRequest.toHttpUrl().toString()

            val walletRequest = service.buildWalletPresentationRequest(request, authorizationRequest)

            assertEquals("\"vp_token\"", walletRequest.parameters["response_type"])
            assertEquals("\"verifier2\"", walletRequest.parameters["client_id"])
            assertTrue(walletRequest.parameters["dcql_query"]?.startsWith("{") == true)
        } finally {
            http.close()
        }
    }

    @Test
    fun `buildWalletPresentationRequest resolves request_uri inputs to wallet encoded parameters`() {
        val http = HttpClient()
        try {
            val service = OpenId4VpPresentationService(http, mockk(relaxed = true))
            val authorizationRequest = AuthorizationRequest(
                clientId = "verifier2",
                responseMode = OpenID4VPResponseMode.DIRECT_POST,
                responseUri = "https://verifier.example/response",
                nonce = "nonce-123",
                dcqlQuery = query,
            )

            val walletRequest = service.buildWalletPresentationRequest(
                request = "openid4vp://authorize?request_uri=https://verifier.example/request-object&request_uri_method=post",
                resolvedRequest = authorizationRequest,
            )

            assertEquals("\"verifier2\"", walletRequest.parameters["client_id"])
            assertTrue(walletRequest.parameters.contains("request_uri").not())
            assertTrue(walletRequest.parameters["dcql_query"]?.startsWith("{") == true)
        } finally {
            http.close()
        }
    }

    @Test
    fun `resolvePresentationRequest parses request object from request parameter`() {
        val http = HttpClient()
        try {
            val service = OpenId4VpPresentationService(http, mockk(relaxed = true))
            val requestObject = unsecuredJwt(
                AuthorizationRequest(
                    clientId = "verifier2",
                    responseMode = OpenID4VPResponseMode.DIRECT_POST,
                    responseUri = "https://verifier.example/response",
                    nonce = "nonce-123",
                    dcqlQuery = query,
                ),
            )

            val resolvedRequest = runBlocking {
                service.resolvePresentationRequest("openid4vp://authorize?request=$requestObject")
            }
            val resolvedUrl = Url(resolvedRequest)

            assertEquals("verifier2", resolvedUrl.parameters["client_id"])
            assertEquals("https://verifier.example/response", resolvedUrl.parameters["response_uri"])
            assertTrue(resolvedUrl.parameters["dcql_query"]?.contains("UniversityDegreeCredential") == true)
        } finally {
            http.close()
        }
    }

    @Test
    fun `resolvePresentationRequest fetches authorization request from request_uri using GET`() {
        withAuthorizationRequestServer { serverUrl, receivedMethod ->
            val http = HttpClient()
            try {
                val service = OpenId4VpPresentationService(http, mockk(relaxed = true))

                val resolvedRequest = runBlocking {
                    service.resolvePresentationRequest("openid4vp://authorize?request_uri=$serverUrl/request-object")
                }
                val resolvedUrl = Url(resolvedRequest)

                assertEquals("GET", receivedMethod())
                assertEquals("verifier2", resolvedUrl.parameters["client_id"])
                assertEquals("https://verifier.example/response", resolvedUrl.parameters["response_uri"])
            } finally {
                http.close()
            }
        }
    }

    @Test
    fun `resolvePresentationRequest fetches authorization request from request_uri using POST`() {
        withAuthorizationRequestServer { serverUrl, receivedMethod ->
            val http = HttpClient()
            try {
                val service = OpenId4VpPresentationService(http, mockk(relaxed = true))

                val resolvedRequest = runBlocking {
                    service.resolvePresentationRequest(
                        "openid4vp://authorize?request_uri=$serverUrl/request-object&request_uri_method=post",
                    )
                }
                val resolvedUrl = Url(resolvedRequest)

                assertEquals("POST", receivedMethod())
                assertEquals("verifier2", resolvedUrl.parameters["client_id"])
                assertEquals("https://verifier.example/response", resolvedUrl.parameters["response_uri"])
            } finally {
                http.close()
            }
        }
    }

    @Test
    fun `resolvePresentationRequest rejects signed request objects for redirect_uri client ids`() {
        val http = HttpClient()
        try {
            val service = OpenId4VpPresentationService(http, mockk(relaxed = true))
            val signedRequestObject = signedLikeJwt(
                """
                {
                  "client_id":"redirect_uri:https://verifier.example/callback",
                  "response_type":"vp_token",
                  "response_mode":"direct_post",
                  "response_uri":"https://verifier.example/response",
                  "nonce":"nonce-123",
                  "client_metadata":{"vp_formats_supported":{}},
                  "dcql_query":${json.encodeToString(DcqlQuery.serializer(), query)}
                }
                """.trimIndent(),
            )

            val error = assertFailsWith<IllegalArgumentException> {
                runBlocking {
                    service.resolvePresentationRequest("openid4vp://authorize?request=$signedRequestObject")
                }
            }

            assertTrue(error.message?.contains("Could not verify signed AuthorizationRequest") == true)
        } finally {
            http.close()
        }
    }

    @Test
    fun `matchCredentials returns wallet credentials satisfying a dcql query`() {
        val http = HttpClient()
        try {
            val service = OpenId4VpPresentationService(http, mockk(relaxed = true))
            val matchingCredential = WalletCredential(
                wallet = Uuid.random(),
                id = "credential-1",
                document = jwt(
                    """
                    {
                      "iss":"did:example:issuer",
                      "sub":"did:example:holder",
                      "vc":{
                        "@context":["https://www.w3.org/2018/credentials/v1"],
                        "type":["VerifiableCredential","UniversityDegreeCredential"],
                        "issuer":{"id":"did:example:issuer"},
                        "credentialSubject":{
                          "id":"did:example:holder",
                          "degree":{"type":"BachelorDegree"}
                        }
                      }
                    }
                    """.trimIndent(),
                ),
                disclosures = null,
                addedOn = Clock.System.now(),
                format = CredentialFormat.jwt_vc_json,
            )

            val matchedCredentials = runBlocking {
                service.matchCredentials(query, listOf(matchingCredential))
            }

            assertEquals(listOf("credential-1"), matchedCredentials.map { it.id })
        } finally {
            http.close()
        }
    }

    private fun jwt(payloadJson: String): String {
        val header = """{"alg":"none","typ":"JWT"}"""
        return listOf(header, payloadJson)
            .joinToString(".") { part ->
                Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(part.toByteArray())
            } + "."
    }

    private fun unsecuredJwt(authorizationRequest: AuthorizationRequest): String =
        jwt(json.encodeToString(AuthorizationRequest.serializer(), authorizationRequest))

    private fun signedLikeJwt(payloadJson: String): String {
        val header = """{"alg":"ES256","typ":"oauth-authz-req+jwt"}"""
        return listOf(header, payloadJson, "signature")
            .joinToString(".") { part ->
                Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(part.toByteArray())
            }
    }

    private fun withAuthorizationRequestServer(
        block: (serverUrl: String, receivedMethod: () -> String?) -> Unit,
    ) {
        val authorizationRequest = AuthorizationRequest(
            clientId = "verifier2",
            responseMode = OpenID4VPResponseMode.DIRECT_POST,
            responseUri = "https://verifier.example/response",
            nonce = "nonce-123",
            dcqlQuery = query,
        )
        var method: String? = null
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/request-object") { exchange: HttpExchange ->
            method = exchange.requestMethod
            val body = json.encodeToString(AuthorizationRequest.serializer(), authorizationRequest)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
            exchange.close()
        }
        server.start()
        try {
            block("http://127.0.0.1:${server.address.port}") { method }
        } finally {
            server.stop(0)
        }
    }
}
