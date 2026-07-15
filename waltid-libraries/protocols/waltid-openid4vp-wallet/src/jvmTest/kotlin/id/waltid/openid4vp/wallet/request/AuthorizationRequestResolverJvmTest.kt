package id.waltid.openid4vp.wallet.request

import io.ktor.http.URLBuilder
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AuthorizationRequestResolverJvmTest {

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
    fun `wallet metadata uses OID4VP Final encryption parameter names`() {
        val metadata = AuthorizationRequestResolver.buildRequestUriPostWalletMetadata(WalletCapabilities())
        assertTrue(metadata.contains("authorization_encryption_alg_values_supported"))
        assertTrue(metadata.contains("authorization_encryption_enc_values_supported"))
        assertTrue(!metadata.contains("encrypted_response_enc_values_supported"))
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
    ): String = buildList {
        clientId?.let { add("\"client_id\":\"$it\"") }
        audience?.let { add("\"aud\":\"$it\"") }
        walletNonce?.let { add("\"wallet_nonce\":\"$it\"") }
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
}
