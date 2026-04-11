package id.walt.webwallet.service.exchange

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import id.walt.dcql.DcqlMatcher
import id.walt.dcql.models.ClaimsQuery
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.models.meta.JwtVcJsonMeta
import id.walt.oid4vc.data.CredentialFormat
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import id.walt.webwallet.db.models.WalletCredential
import id.waltid.openid4vp.wallet.request.ResolvedAuthorizationRequest
import io.ktor.client.HttpClient
import io.ktor.http.Url
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.every
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.InputStreamReader
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.InetSocketAddress
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import id.walt.dcql.models.CredentialFormat as DcqlCredentialFormat

@OptIn(ExperimentalUuidApi::class, ExperimentalSerializationApi::class)
class OpenId4VpPresentationServiceTest {
    private val json = Json { encodeDefaults = false }
    private val supportedTransactionDataType = "org.waltid.transaction-data.payment-authorization"

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
    fun `normalized request URL keeps direct OpenID4VP requests intact`() {
        HttpClient().use { http ->
            val service = OpenId4VpPresentationService(mockk(relaxed = true))
            val request = AuthorizationRequest(
                clientId = "verifier2",
                responseMode = OpenID4VPResponseMode.DIRECT_POST,
                responseUri = "https://verifier.example/response",
                nonce = "nonce-123",
                dcqlQuery = query,
            ).toHttpUrl().toString()

            val resolvedRequest = runBlocking { resolveNormalizedRequestUrl(service, request) }
            val resolvedUrl = Url(resolvedRequest)

            assertEquals("verifier2", resolvedUrl.parameters["client_id"])
            assertEquals("https://verifier.example/response", resolvedUrl.parameters["response_uri"])
            assertTrue(resolvedUrl.parameters["dcql_query"]?.contains("UniversityDegreeCredential") == true)
        }
    }

    @Test
    fun `normalized request URL keeps raw scalar query parameters as strings`() {
        HttpClient().use { http ->
            val service = OpenId4VpPresentationService(mockk(relaxed = true))
            val request = AuthorizationRequest(
                clientId = "verifier2",
                responseMode = OpenID4VPResponseMode.DIRECT_POST,
                responseUri = "https://verifier.example/response",
                nonce = "12345",
                state = "true",
                dcqlQuery = query,
            ).toHttpUrl().toString()

            val resolvedRequest = runBlocking { resolveNormalizedRequestUrl(service, request) }
            val resolvedUrl = Url(resolvedRequest)

            assertEquals("12345", resolvedUrl.parameters["nonce"])
            assertEquals("true", resolvedUrl.parameters["state"])
        }
    }

    @Test
    fun `buildWalletPresentationRequest keeps scalars plain and structured values JSON encoded`() {
        HttpClient().use { http ->
            val service = OpenId4VpPresentationService(mockk(relaxed = true))
            val authorizationRequest = AuthorizationRequest(
                clientId = "verifier2",
                responseMode = OpenID4VPResponseMode.DIRECT_POST,
                responseUri = "https://verifier.example/response",
                nonce = "nonce-123",
                dcqlQuery = query,
            )
            val request = authorizationRequest.toHttpUrl().toString()

            val walletRequest = service.buildWalletPresentationRequest(
                request,
                ResolvedAuthorizationRequest.Plain(authorizationRequest),
            )

            assertEquals("vp_token", walletRequest.parameters["response_type"])
            assertEquals("verifier2", walletRequest.parameters["client_id"])
            assertTrue(walletRequest.parameters["dcql_query"]?.startsWith("{") == true)
        }
    }

    @Test
    fun `buildWalletPresentationRequest resolves request_uri inputs to wallet encoded parameters`() {
        HttpClient().use { http ->
            val service = OpenId4VpPresentationService(mockk(relaxed = true))
            val authorizationRequest = AuthorizationRequest(
                clientId = "verifier2",
                responseMode = OpenID4VPResponseMode.DIRECT_POST,
                responseUri = "https://verifier.example/response",
                nonce = "nonce-123",
                dcqlQuery = query,
            )

            val walletRequest = service.buildWalletPresentationRequest(
                request = "openid4vp://authorize?request_uri=https://verifier.example/request-object&request_uri_method=post",
                resolvedRequest = ResolvedAuthorizationRequest.Plain(authorizationRequest),
            )

            assertEquals("verifier2", walletRequest.parameters["client_id"])
            assertTrue(walletRequest.parameters.contains("request_uri").not())
            assertTrue(walletRequest.parameters["dcql_query"]?.startsWith("{") == true)
        }
    }

    @Test
    fun `normalized request URL rejects unsupported transaction data types`() {
        HttpClient().use { http ->
            val service = OpenId4VpPresentationService(http, mockk(relaxed = true))
            val request = authorizationRequest(
                transactionData = listOf(
                    transactionDataItem(
                        type = "unsupported-type",
                        credentialIds = listOf("degree"),
                        amount = "42.00",
                    ),
                ),
            )

            assertFailsWith<IllegalArgumentException> {
                runBlocking { resolveNormalizedRequestUrl(service, request) }
            }
        }
    }

    @Test
    fun `normalized request URL rejects transaction data for unsupported credential query formats`() {
        HttpClient().use { http ->
            val service = OpenId4VpPresentationService(http, mockk(relaxed = true))
            val request = authorizationRequest(
                transactionData = listOf(
                    transactionDataItem(
                        type = supportedTransactionDataType,
                        credentialIds = listOf("degree"),
                        requireCryptographicHolderBinding = true,
                        amount = "42.00",
                    ),
                ),
            )

            val error = assertFailsWith<IllegalArgumentException> {
                runBlocking { resolveNormalizedRequestUrl(service, request) }
            }

            assertTrue(error.message?.contains("supported transaction_data profile", ignoreCase = true) == true)
        }
    }

    @Test
    fun `normalized request URL preserves signed request objects from request parameter`() {
        HttpClient().use { http ->
            val service = OpenId4VpPresentationService(mockk(relaxed = true))
            val requestObject = unsecuredJwt(
                AuthorizationRequest(
                    clientId = "verifier2",
                    responseMode = OpenID4VPResponseMode.DIRECT_POST,
                    responseUri = "https://verifier.example/response",
                    nonce = "nonce-123",
                    dcqlQuery = query,
                ),
            )

            val resolvedRequest = runBlocking { resolveNormalizedRequestUrl(service, "openid4vp://authorize?request=$requestObject") }
            val resolvedUrl = Url(resolvedRequest)

            assertEquals(requestObject, resolvedUrl.parameters["request"])
            assertTrue(!resolvedUrl.parameters.contains("dcql_query"))
        }
    }

    @Test
    fun `normalized request URL fetches authorization requests from request_uri using GET`() {
        withAuthorizationRequestServer { serverUrl, receivedRequest ->
            HttpClient().use { http ->
                val service = OpenId4VpPresentationService(mockk(relaxed = true))

                val resolvedRequest = runBlocking {
                    resolveNormalizedRequestUrl(service, "openid4vp://authorize?request_uri=$serverUrl/request-object")
                }
                val resolvedUrl = Url(resolvedRequest)

                assertEquals("GET", receivedRequest().method)
                assertEquals("verifier2", resolvedUrl.parameters["client_id"])
                assertEquals("https://verifier.example/response", resolvedUrl.parameters["response_uri"])
            }
        }
    }

    @Test
    fun `normalized request URL fetches authorization requests from request_uri using spec compliant POST`() {
        withAuthorizationRequestServer { serverUrl, receivedRequest ->
            HttpClient().use { http ->
                val service = OpenId4VpPresentationService(mockk(relaxed = true))

                val resolvedRequest = runBlocking {
                    resolveNormalizedRequestUrl(
                        service,
                        "openid4vp://authorize?request_uri=$serverUrl/request-object&request_uri_method=post",
                    )
                }
                val resolvedUrl = Url(resolvedRequest)

                val request = receivedRequest()
                assertEquals("POST", request.method)
                assertEquals("application/x-www-form-urlencoded", request.contentType)
                assertTrue(request.accept?.contains("application/oauth-authz-req+jwt") == true)
                assertEquals("", request.body)
                assertEquals("verifier2", resolvedUrl.parameters["client_id"])
                assertEquals("https://verifier.example/response", resolvedUrl.parameters["response_uri"])
            }
        }
    }

    @Test
    fun `normalized request URL rejects unsupported request_uri_method values`() {
        withAuthorizationRequestServer { serverUrl, _ ->
            HttpClient().use { http ->
                val service = OpenId4VpPresentationService(mockk(relaxed = true))

                val error = assertFailsWith<IllegalArgumentException> {
                    runBlocking {
                        resolveNormalizedRequestUrl(
                            service,
                            "openid4vp://authorize?request_uri=$serverUrl/request-object&request_uri_method=patch",
                        )
                    }
                }

                assertEquals(
                    "invalid_request_uri_method: patch is neither 'get' nor 'post'",
                    error.message,
                )
            }
        }
    }

    @Test
    fun `normalized request URL rejects request_uri_method values with incorrect casing`() {
        withAuthorizationRequestServer { serverUrl, _ ->
            HttpClient().use { http ->
                val service = OpenId4VpPresentationService(mockk(relaxed = true))

                val error = assertFailsWith<IllegalArgumentException> {
                    runBlocking {
                        resolveNormalizedRequestUrl(
                            service,
                            "openid4vp://authorize?request_uri=$serverUrl/request-object&request_uri_method=POST",
                        )
                    }
                }

                assertEquals(
                    "invalid_request_uri_method: POST is neither 'get' nor 'post'",
                    error.message,
                )
            }
        }
    }

    @Test
    fun `normalized request URL preserves signed request objects fetched from request_uri`() {
        val requestObject = unsecuredJwt(
            AuthorizationRequest(
                clientId = "verifier2",
                responseMode = OpenID4VPResponseMode.DIRECT_POST,
                responseUri = "https://verifier.example/response",
                nonce = "nonce-123",
                dcqlQuery = query,
            ),
        )

        withAuthorizationRequestServer(
            responseBody = requestObject,
            responseContentType = "application/oauth-authz-req+jwt",
        ) { serverUrl, _ ->
            HttpClient().use { http ->
                val service = OpenId4VpPresentationService(mockk(relaxed = true))

                val resolvedRequest = runBlocking {
                    resolveNormalizedRequestUrl(service, "openid4vp://authorize?request_uri=$serverUrl/request-object")
                }
                val resolvedUrl = Url(resolvedRequest)

                assertEquals(requestObject, resolvedUrl.parameters["request"])
                assertTrue(!resolvedUrl.parameters.contains("request_uri"))
                assertTrue(!resolvedUrl.parameters.contains("dcql_query"))
            }
        }
    }

    @Test
    fun `normalized request URL rejects signed request objects for redirect_uri client ids`() {
        HttpClient().use { http ->
            val service = OpenId4VpPresentationService(mockk(relaxed = true))
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
                    resolveNormalizedRequestUrl(service, "openid4vp://authorize?request=$signedRequestObject")
                }
            }

            assertTrue(error.message?.contains("Could not verify signed AuthorizationRequest") == true)
        }
    }

    @Test
    fun `isOpenId4VpRequestCandidate only treats explicit v1 requests as strict v1 candidates`() {
        val v1RequestObject = signedLikeJwt(
            """
            {
              "client_id":"verifier2",
              "response_type":"vp_token",
              "response_mode":"direct_post",
              "response_uri":"https://verifier.example/response",
              "nonce":"nonce-123",
              "dcql_query":${json.encodeToString(DcqlQuery.serializer(), query)}
            }
            """.trimIndent(),
        )
        val draftRequestObject = signedLikeJwt(
            """
            {
              "client_id":"https://verifier.example/callback",
              "client_id_scheme":"redirect_uri",
              "response_type":"vp_token",
              "response_mode":"direct_post",
              "response_uri":"https://verifier.example/response",
              "nonce":"nonce-123",
              "presentation_definition":{"id":"presentation-definition"}
            }
            """.trimIndent(),
        )

        assertTrue(OpenId4VpPresentationService.isOpenId4VpRequestCandidate("openid4vp://authorize?request=$v1RequestObject"))
        assertTrue(OpenId4VpPresentationService.isOpenId4VpRequestCandidate("openid4vp://authorize?dcql_query=%7B%7D"))
        assertTrue(OpenId4VpPresentationService.isOpenId4VpRequestCandidate("openid4vp://authorize?request_uri=https://verifier.example/request"))
        assertTrue(!OpenId4VpPresentationService.isOpenId4VpRequestCandidate("openid4vp://authorize?request=$draftRequestObject"))
        assertTrue(!OpenId4VpPresentationService.isOpenId4VpRequestCandidate("openid4vp://authorize?transaction_data=%5B%22eyJ0eXBlIjoicGF5bWVudCJ9%22%5D"))
    }

    @Test
    fun `matchCredentials returns wallet credentials satisfying a dcql query`() {
        HttpClient().use { http ->
            val service = OpenId4VpPresentationService(mockk(relaxed = true))
            val matchingCredential = matchingCredential()

            val matchedCredentials = runBlocking {
                service.matchCredentials(query, listOf(matchingCredential))
            }

            assertEquals(listOf("credential-1"), matchedCredentials.map { it.id })
        }
    }

    @Test
    fun `matchCredentialResults propagates matcher failures`() {
        HttpClient().use { http ->
            val service = OpenId4VpPresentationService(mockk(relaxed = true))
            mockkObject(DcqlMatcher)
            try {
                every { DcqlMatcher.match(any(), any()) } returns Result.failure(IllegalArgumentException("boom"))

                val error = assertFailsWith<IllegalArgumentException> {
                    runBlocking {
                        service.matchCredentialResults(query, listOf(matchingCredential()))
                    }
                }

                assertEquals("boom", error.message)
            } finally {
                unmockkObject(DcqlMatcher)
            }
        }
    }

    private fun matchingCredential() = WalletCredential(
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

    private suspend fun resolveNormalizedRequestUrl(
        service: OpenId4VpPresentationService,
        request: String,
    ): String {
        val resolvedRequest = service.tryResolveAuthorizationRequest(request).getOrThrow()
        return service.buildWalletPresentationRequest(
            request = request,
            resolvedRequest = resolvedRequest,
        ).toString()
    }

    private data class RecordedRequest(
        val method: String?,
        val contentType: String?,
        val accept: String?,
        val body: String,
    )

    private fun authorizationRequest(
        transactionData: List<String>? = null,
    ): String = AuthorizationRequest(
        clientId = "verifier2",
        responseMode = OpenID4VPResponseMode.DIRECT_POST,
        responseUri = "https://verifier.example/response",
        nonce = "nonce-123",
        dcqlQuery = query,
        transactionData = transactionData,
    ).toHttpUrl().toString()

    private fun transactionDataItem(
        type: String,
        credentialIds: List<String>,
        requireCryptographicHolderBinding: Boolean? = null,
        amount: String,
    ): String =
        encodeBase64Url(
            buildJsonObject {
                put("type", type)
                put("credential_ids", buildJsonArray {
                    credentialIds.forEach { add(JsonPrimitive(it)) }
                })
                requireCryptographicHolderBinding?.let { put("require_cryptographic_holder_binding", it) }
                put("amount", amount)
            }.toString(),
        )

    private fun encodeBase64Url(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray())

    private fun withAuthorizationRequestServer(
        responseBody: String? = null,
        responseContentType: String = "application/json",
        block: (serverUrl: String, receivedRequest: () -> RecordedRequest) -> Unit,
    ) {
        val authorizationRequest = AuthorizationRequest(
            clientId = "verifier2",
            responseMode = OpenID4VPResponseMode.DIRECT_POST,
            responseUri = "https://verifier.example/response",
            nonce = "nonce-123",
            dcqlQuery = query,
        )
        var method: String? = null
        var contentType: String? = null
        var accept: String? = null
        var body = ""
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/request-object") { exchange: HttpExchange ->
            method = exchange.requestMethod
            contentType = exchange.requestHeaders.getFirst("Content-Type")
            accept = exchange.requestHeaders.getFirst("Accept")
            body = InputStreamReader(exchange.requestBody).readText()
            val response = responseBody ?: json.encodeToString(AuthorizationRequest.serializer(), authorizationRequest)
            exchange.responseHeaders.add("Content-Type", responseContentType)
            exchange.sendResponseHeaders(200, response.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(response.toByteArray()) }
            exchange.close()
        }
        server.start()
        try {
            block("http://127.0.0.1:${server.address.port}") { RecordedRequest(method, contentType, accept, body) }
        } finally {
            server.stop(0)
        }
    }
}
