package id.waltid.openid4vp.wallet.request

import id.walt.verifier.openid.models.authorization.RequestUriHttpMethod
import id.walt.webdatafetching.WebDataFetcher
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.URLBuilder
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.http.parseQueryString
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AuthorizationRequestResolverJvmTest {

    @Test
    fun `request uri post wallet metadata declares supported response and client id capabilities`() {
        val metadata = Json.parseToJsonElement(
            AuthorizationRequestResolver.buildRequestUriPostWalletMetadata(
                WalletCapabilities(
                    vpFormatsSupported = jsonObjectOf("dc+sd-jwt" to jsonObjectOf()),
                )
            ),
        ).jsonObject

        assertEquals(
            listOf("vp_token", "vp_token id_token"),
            metadata.getValue("response_types_supported").jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(
            listOf("fragment", "query", "direct_post", "direct_post.jwt", "form_post"),
            metadata.getValue("response_modes_supported").jsonArray.map { it.jsonPrimitive.content },
        )
        val expectedClientIdPrefixes = listOf("redirect_uri", "decentralized_identifier")
        assertFalse("client_id_schemes_supported" in metadata)
        assertEquals(
            expectedClientIdPrefixes,
            metadata.getValue("client_id_prefixes_supported").jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(
            jsonObjectOf("dc+sd-jwt" to jsonObjectOf()),
            metadata.getValue("vp_formats_supported").jsonObject,
        )
    }

    @Test
    fun `request uri post fetch sends wallet metadata and wallet nonce as form fields`() = runBlocking {
        var capturedRequest: HttpRequestData? = null
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    capturedRequest = request
                    respond(
                        content = "signed-request-object",
                        headers = headersOf(HttpHeaders.ContentType, "application/oauth-authz-req+jwt"),
                    )
                }
            }
        }
        val fetcher = WebDataFetcher.wrapping(client, id = "authorization-request-resolver-test")
        val walletMetadata = AuthorizationRequestResolver.buildRequestUriPostWalletMetadata(
            WalletCapabilities(
                vpFormatsSupported = jsonObjectOf("dc+sd-jwt" to jsonObjectOf()),
            )
        )

        val response = AuthorizationRequestResolver.fetchRequestUriWithWebDataFetcher(
            webResolveAuthReq = fetcher,
            requestUri = "https://verifier.example/request.jwt",
            requestUriMethod = RequestUriHttpMethod.POST,
            requestUriPostWalletMetadata = walletMetadata,
        )
        val request = requireNotNull(capturedRequest)
        val form = parseQueryString(request.bodyText())

        assertEquals(HttpMethod.Post, request.method)
        assertEquals(ContentType.Application.FormUrlEncoded, request.body.contentType)
        assertEquals(walletMetadata, form["wallet_metadata"])
        assertEquals(response.walletNonce, form["wallet_nonce"])
        assertFalse(response.walletNonce.isNullOrBlank())
        assertEquals("signed-request-object", response.body)
    }

    @Test
    fun `unsigned request object is rejected when policy requires signed request objects`() {
        val requestObject = unsignedJwt(
            """
            {
              "client_id":"verifier2",
              "aud":"https://self-issued.me/v2",
              "nonce":"nonce-123"
            }
            """.trimIndent(),
        )
        val requestUrl = URLBuilder("openid4vp://authorize").apply {
            parameters.append("client_id", "verifier2")
            parameters.append("request", requestObject)
        }.build()

        assertFailsWith<AuthorizationRequestResolver.UnsignedAuthorizationRequestNotAllowedException> {
            runBlocking {
                AuthorizationRequestResolver.resolve(
                    requestUrl = requestUrl,
                    unsignedRequestObjectPolicy = AuthorizationRequestResolver.UnsignedRequestObjectPolicy.REQUIRE_SIGNED,
                ) { _, _ ->
                    error("request_uri fetch should not be called for inline request objects")
                }
            }
        }
    }

    @Test
    fun `unsigned request object is accepted when policy explicitly allows unsigned request objects`() {
        val requestObject = unsignedJwt(
            """
            {
              "client_id":"verifier2",
              "aud":"https://self-issued.me/v2",
              "nonce":"nonce-123"
            }
            """.trimIndent(),
        )
        val requestUrl = URLBuilder("openid4vp://authorize").apply {
            parameters.append("client_id", "verifier2")
            parameters.append("request", requestObject)
        }.build()

        val resolved = runBlocking {
            AuthorizationRequestResolver.resolve(
                requestUrl = requestUrl,
                unsignedRequestObjectPolicy = AuthorizationRequestResolver.UnsignedRequestObjectPolicy.ALLOW_UNSIGNED,
            ) { _, _ ->
                error("request_uri fetch should not be called for inline request objects")
            }
        }

        assertIs<ResolvedAuthorizationRequest.WithRequestObject>(resolved)
        assertEquals("verifier2", resolved.authorizationRequest.clientId)
        assertEquals(requestObject, resolved.requestObject)
    }

    @Test
    fun `request object common claims are validated before alg none policy`() {
        val cases = listOf(
            "missing typ" to unsignedJwt(validPayload(), headerJson = """{"alg":"none"}"""),
            "wrong typ" to unsignedJwt(validPayload(), headerJson = """{"alg":"none","typ":"JWT"}"""),
            "missing audience" to unsignedJwt(validPayload(audience = null)),
            "wrong audience" to unsignedJwt(validPayload(audience = "https://wrong.example")),
            "missing inner client id" to unsignedJwt(validPayload(clientId = null)),
        )

        cases.forEach { (description, requestObject) ->
            val failure = assertFailsWith<IllegalArgumentException>(description) {
                resolveAllowUnsigned(requestObject, outerClientId = "verifier2")
            }
            assertTrue(failure.message?.isNotBlank() == true, description)
        }
    }

    @Test
    fun `request object rejects invalid temporal claims`() {
        val cases = listOf(
            "expired" to validPayload(expiration = 0),
            "not yet valid" to validPayload(notBefore = 4_102_444_800),
            "invalid expiration" to validPayload(extraClaims = listOf("\"exp\":\"tomorrow\"")),
            "invalid not-before" to validPayload(extraClaims = listOf("\"nbf\":true")),
        )

        cases.forEach { (description, payload) ->
            val failure = assertFailsWith<IllegalArgumentException>(description) {
                resolveAllowUnsigned(unsignedJwt(payload), outerClientId = "verifier2")
            }
            assertTrue(failure.message.orEmpty().contains("exp") || failure.message.orEmpty().contains("nbf"))
        }
    }

    @Test
    fun `request object requires matching outer and inner client ids`() {
        val missingOuter = assertFailsWith<IllegalArgumentException> {
            resolveAllowUnsigned(unsignedJwt(validPayload()), outerClientId = null)
        }
        assertTrue(missingOuter.message.orEmpty().contains("outer Authorization Request"))

        val mismatch = assertFailsWith<IllegalArgumentException> {
            resolveAllowUnsigned(unsignedJwt(validPayload()), outerClientId = "different-client")
        }
        assertTrue(mismatch.message.orEmpty().contains("client_id mismatch"))
    }

    @Test
    fun `x509 client identifier rejects alg none even when generic unsigned policy allows it`() {
        val clientId = "x509_hash:y4ajD4aIXGiLGqiF81nN5HvBFvBEvrZcgFsp5VIJO30"
        val failure = assertFailsWith<AuthorizationRequestResolver.UnsignedAuthorizationRequestNotAllowedException> {
            resolveAllowUnsigned(
                unsignedJwt(validPayload(clientId = clientId)),
                outerClientId = clientId,
            )
        }
        assertTrue(failure.message.orEmpty().contains("not allowed"))
    }

    @Test
    fun `request uri post validates wallet nonce`() {
        val requestObject = unsignedJwt(validPayload(walletNonce = "returned-wallet-nonce"))
        val requestUrl = URLBuilder("openid4vp://authorize").apply {
            parameters.append("client_id", "verifier2")
            parameters.append("request_uri", "https://verifier.example/request.jwt")
            parameters.append("request_uri_method", "post")
        }.build()

        val resolved = runBlocking {
            AuthorizationRequestResolver.resolve(
                requestUrl = requestUrl,
                unsignedRequestObjectPolicy = AuthorizationRequestResolver.UnsignedRequestObjectPolicy.ALLOW_UNSIGNED,
            ) { _, method ->
                assertEquals("post", method?.method)
                AuthorizationRequestResolver.RequestUriFetchResponse(
                    status = HttpStatusCode.OK,
                    contentType = ContentType.parse("application/oauth-authz-req+jwt"),
                    body = requestObject,
                    walletNonce = "returned-wallet-nonce",
                )
            }
        }

        assertIs<ResolvedAuthorizationRequest.WithRequestObject>(resolved)
    }

    @Test
    fun `request uri rejects wallet nonce mismatch and non JWT content type`() {
        val requestUrl = URLBuilder("openid4vp://authorize").apply {
            parameters.append("client_id", "verifier2")
            parameters.append("request_uri", "https://verifier.example/request.jwt")
            parameters.append("request_uri_method", "post")
        }.build()

        val mismatch = assertFailsWith<IllegalArgumentException> {
            runBlocking {
                AuthorizationRequestResolver.resolve(
                    requestUrl,
                    AuthorizationRequestResolver.UnsignedRequestObjectPolicy.ALLOW_UNSIGNED,
                ) { _, _ ->
                    AuthorizationRequestResolver.RequestUriFetchResponse(
                        HttpStatusCode.OK,
                        ContentType.parse("application/oauth-authz-req+jwt"),
                        unsignedJwt(validPayload(walletNonce = "wrong")),
                        walletNonce = "expected",
                    )
                }
            }
        }
        assertTrue(mismatch.message.orEmpty().contains("wallet_nonce mismatch"))

        val wrongContentType = assertFailsWith<IllegalArgumentException> {
            runBlocking {
                AuthorizationRequestResolver.resolve(
                    requestUrl,
                    AuthorizationRequestResolver.UnsignedRequestObjectPolicy.ALLOW_UNSIGNED,
                ) { _, _ ->
                    AuthorizationRequestResolver.RequestUriFetchResponse(
                        HttpStatusCode.OK,
                        ContentType.Application.Json,
                        "{}",
                    )
                }
            }
        }
        assertTrue(wrongContentType.message.orEmpty().contains("Unsupported AuthorizationRequest content type"))
    }

    @Test
    fun `request and request uri parameter combinations are strictly validated`() {
        val requestObject = unsignedJwt(validPayload())
        val both = URLBuilder("openid4vp://authorize").apply {
            parameters.append("client_id", "verifier2")
            parameters.append("request", requestObject)
            parameters.append("request_uri", "https://verifier.example/request.jwt")
        }.build()
        val methodWithoutUri = URLBuilder("openid4vp://authorize").apply {
            parameters.append("client_id", "verifier2")
            parameters.append("request_uri_method", "post")
        }.build()
        val uppercaseMethod = URLBuilder("openid4vp://authorize").apply {
            parameters.append("client_id", "verifier2")
            parameters.append("request_uri", "https://verifier.example/request.jwt")
            parameters.append("request_uri_method", "POST")
        }.build()

        listOf(both, methodWithoutUri, uppercaseMethod).forEach { requestUrl ->
            assertFailsWith<IllegalArgumentException> {
                runBlocking {
                    AuthorizationRequestResolver.resolve(
                        requestUrl,
                        AuthorizationRequestResolver.UnsignedRequestObjectPolicy.ALLOW_UNSIGNED,
                    ) { _, _ -> error("invalid request must fail before fetching") }
                }
            }
        }
    }

    @Test
    fun `plain requests reject prefixes that require signed request objects`() {
        listOf(
            "x509_hash:certificate-thumbprint",
            "x509_san_dns:verifier.example",
            "decentralized_identifier:did:example:verifier",
            "verifier_attestation:verifier.example",
            "openid_federation:https://verifier.example",
        ).forEach { clientId ->
            val requestUrl = URLBuilder("openid4vp://authorize").apply {
                parameters.append("client_id", clientId)
                parameters.append("nonce", "nonce-123")
            }.build()

            val failure = assertFailsWith<IllegalArgumentException>(clientId) {
                runBlocking {
                    AuthorizationRequestResolver.resolve(
                        requestUrl,
                        AuthorizationRequestResolver.UnsignedRequestObjectPolicy.ALLOW_UNSIGNED,
                    ) { _, _ -> error("plain request must not fetch") }
                }
            }
            assertTrue(failure.message.orEmpty().contains("cannot be authenticated as a plain request"))
        }
    }

    @Test
    fun `plain pre-registered request fails closed without a client registry`() {
        val requestUrl = URLBuilder("openid4vp://authorize").apply {
            parameters.append("client_id", "registered-verifier")
            parameters.append("client_metadata", "{}")
            parameters.append("nonce", "nonce-123")
        }.build()

        val failure = assertFailsWith<IllegalArgumentException> {
            runBlocking {
                AuthorizationRequestResolver.resolve(
                    requestUrl,
                    AuthorizationRequestResolver.UnsignedRequestObjectPolicy.ALLOW_UNSIGNED,
                ) { _, _ -> error("plain request must not fetch") }
            }
        }
        assertTrue(failure.message.orEmpty().contains("without a configured registration"))
    }

    @Test
    fun `redirect uri prefix derives and binds direct post response uri`() {
        val deliveryUri = "https://verifier.example/response"
        val requestUrl = URLBuilder("openid4vp://authorize").apply {
            parameters.append("client_id", "redirect_uri:$deliveryUri")
            parameters.append("client_metadata", "{}")
            parameters.append("response_mode", "direct_post")
            parameters.append("nonce", "nonce-123")
        }.build()

        val resolved = runBlocking {
            AuthorizationRequestResolver.resolve(
                requestUrl,
                AuthorizationRequestResolver.UnsignedRequestObjectPolicy.REQUIRE_SIGNED,
            ) { _, _ -> error("plain request must not fetch") }
        }
        assertEquals(deliveryUri, resolved.authorizationRequest.responseUri)

        val mismatch = URLBuilder(requestUrl).apply {
            parameters.append("response_uri", "https://attacker.example/collect")
        }.build()
        val failure = assertFailsWith<IllegalArgumentException> {
            runBlocking {
                AuthorizationRequestResolver.resolve(
                    mismatch,
                    AuthorizationRequestResolver.UnsignedRequestObjectPolicy.REQUIRE_SIGNED,
                ) { _, _ -> error("plain request must not fetch") }
            }
        }
        assertTrue(failure.message.orEmpty().contains("must exactly match"))
    }

    @Test
    fun `redirect uri prefix requires client metadata`() {
        val requestUrl = URLBuilder("openid4vp://authorize").apply {
            parameters.append("client_id", "redirect_uri:https://verifier.example/callback")
            parameters.append("nonce", "nonce-123")
        }.build()

        val failure = assertFailsWith<AuthorizationRequestResolver.SignedAuthorizationRequestValidationException> {
            runBlocking {
                AuthorizationRequestResolver.resolve(
                    requestUrl,
                    AuthorizationRequestResolver.UnsignedRequestObjectPolicy.ALLOW_UNSIGNED,
                ) { _, _ -> error("plain request must not fetch") }
            }
        }
        assertTrue(failure.message.orEmpty().contains("client_metadata"))
    }

    @Test
    fun `request uri post requires HTTPS before fetch and after redirects`() {
        val insecureRequest = URLBuilder("openid4vp://authorize").apply {
            parameters.append("client_id", "verifier2")
            parameters.append("request_uri", "http://verifier.example/request.jwt")
            parameters.append("request_uri_method", "post")
        }.build()
        var fetchCalled = false
        assertFailsWith<IllegalArgumentException> {
            runBlocking {
                AuthorizationRequestResolver.resolve(
                    insecureRequest,
                    AuthorizationRequestResolver.UnsignedRequestObjectPolicy.ALLOW_UNSIGNED,
                ) { _, _ ->
                    fetchCalled = true
                    error("must not fetch")
                }
            }
        }
        assertFalse(fetchCalled)

        val secureRequest = URLBuilder(insecureRequest).apply {
            parameters["request_uri"] = "https://verifier.example/request.jwt"
        }.build()
        val failure = assertFailsWith<IllegalArgumentException> {
            runBlocking {
                AuthorizationRequestResolver.resolve(
                    secureRequest,
                    AuthorizationRequestResolver.UnsignedRequestObjectPolicy.ALLOW_UNSIGNED,
                ) { _, _ ->
                    AuthorizationRequestResolver.RequestUriFetchResponse(
                        status = HttpStatusCode.OK,
                        contentType = ContentType.parse("application/oauth-authz-req+jwt"),
                        body = "not-reached",
                        resolvedRequestUri = "http://verifier.example/request.jwt",
                    )
                }
            }
        }
        assertTrue(failure.message.orEmpty().contains("HTTPS"))
    }

    @Test
    fun `wallet metadata uses OID4VP Final encryption parameter names`() {
        val metadata = AuthorizationRequestResolver.buildRequestUriPostWalletMetadata(WalletCapabilities())
        assertTrue(metadata.contains("authorization_encryption_alg_values_supported"))
        assertTrue(metadata.contains("authorization_encryption_enc_values_supported"))
        assertTrue(!metadata.contains("encrypted_response_enc_values_supported"))
    }

    @Test
    fun `wallet metadata advertises only explicitly configured client id prefixes`() {
        val metadata = Json.parseToJsonElement(
            AuthorizationRequestResolver.buildRequestUriPostWalletMetadata(
                WalletCapabilities(clientIdPrefixesSupported = listOf("redirect_uri", "x509_hash"))
            )
        ).jsonObject

        assertEquals(
            listOf("redirect_uri", "x509_hash"),
            metadata.getValue("client_id_prefixes_supported").jsonArray.map { it.jsonPrimitive.content },
        )
    }

    @Test
    fun `request uri post can omit optional wallet metadata`() {
        val parameters = parseQueryString(
            AuthorizationRequestResolver.buildRequestUriPostBody(
                walletNonce = "nonce",
                walletMetadata = "{\"vp_formats_supported\":{}}",
                sendWalletMetadata = false,
            )
        )

        assertEquals("nonce", parameters["wallet_nonce"])
        assertEquals(null, parameters["wallet_metadata"])
    }

    private fun resolveAllowUnsigned(requestObject: String, outerClientId: String?) = runBlocking {
        val requestUrl = URLBuilder("openid4vp://authorize").apply {
            outerClientId?.let { parameters.append("client_id", it) }
            parameters.append("request", requestObject)
        }.build()
        AuthorizationRequestResolver.resolve(
            requestUrl,
            AuthorizationRequestResolver.UnsignedRequestObjectPolicy.ALLOW_UNSIGNED,
        ) { _, _ -> error("request_uri fetch should not be called") }
    }

    private fun validPayload(
        clientId: String? = "verifier2",
        audience: String? = "https://self-issued.me/v2",
        walletNonce: String? = null,
        expiration: Long? = null,
        notBefore: Long? = null,
        extraClaims: List<String> = emptyList(),
    ): String = buildList {
        clientId?.let { add("\"client_id\":\"$it\"") }
        audience?.let { add("\"aud\":\"$it\"") }
        walletNonce?.let { add("\"wallet_nonce\":\"$it\"") }
        expiration?.let { add("\"exp\":$it") }
        notBefore?.let { add("\"nbf\":$it") }
        addAll(extraClaims)
        add("\"nonce\":\"nonce-123\"")
    }.joinToString(prefix = "{", postfix = "}")

    private fun unsignedJwt(
        payloadJson: String,
        headerJson: String = """{"alg":"none","typ":"oauth-authz-req+jwt"}""",
    ): String {
        return listOf(headerJson, payloadJson)
            .joinToString(".") { segment ->
                Base64.getUrlEncoder().withoutPadding().encodeToString(segment.toByteArray())
            } + "."
    }

    private fun jsonObjectOf(vararg pairs: Pair<String, kotlinx.serialization.json.JsonElement>) =
        kotlinx.serialization.json.JsonObject(mapOf(*pairs))

    private fun HttpRequestData.bodyText(): String =
        (body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
}
