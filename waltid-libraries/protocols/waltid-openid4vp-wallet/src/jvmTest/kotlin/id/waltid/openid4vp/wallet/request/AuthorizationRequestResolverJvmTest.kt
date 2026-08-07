@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.waltid.openid4vp.wallet.request

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.openid4vp.clientidprefix.ClientIdError
import id.walt.openid4vp.clientidprefix.ClientIdTrustConfiguration
import id.walt.verifier.openid.models.authorization.RequestUriHttpMethod
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.webdatafetching.WebDataFetcher
import id.walt.x509.CertificateDer
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.URLBuilder
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.http.parseQueryString
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.time.Clock

class AuthorizationRequestResolverJvmTest {

    @Test
    fun `legacy resolver keeps unprefixed plain client ids compatible`() = runBlocking {
        val requestUrl = URLBuilder("openid4vp://authorize").apply {
            parameters.append("client_id", "legacy-verifier")
            parameters.append("response_type", "vp_token")
            parameters.append("response_mode", "fragment")
            parameters.append("redirect_uri", "https://verifier.example/callback")
            parameters.append("nonce", "legacy-nonce")
        }.build()

        val resolved = AuthorizationRequestResolver.resolve(
            requestUrl = requestUrl,
            unsignedRequestObjectPolicy = AuthorizationRequestResolver.UnsignedRequestObjectPolicy.ALLOW_UNSIGNED,
            enforceFinalRequestObject = false,
        ) { _, _ -> error("request_uri fetch should not be called") }

        assertEquals("legacy-verifier", resolved.authorizationRequest.clientId)
    }

    @Test
    fun `strict resolver rejects the legacy unprefixed plain client id`() = runBlocking {
        val requestUrl = URLBuilder("openid4vp://authorize").apply {
            parameters.append("client_id", "legacy-verifier")
            parameters.append("response_type", "vp_token")
            parameters.append("response_mode", "fragment")
            parameters.append("redirect_uri", "https://verifier.example/callback")
            parameters.append("nonce", "strict-nonce")
        }.build()

        assertFailsWith<IllegalArgumentException> {
            AuthorizationRequestResolver.resolve(
                requestUrl = requestUrl,
                unsignedRequestObjectPolicy = AuthorizationRequestResolver.UnsignedRequestObjectPolicy.REQUIRE_SIGNED,
            ) { _, _ -> error("request_uri fetch should not be called") }
        }
    }

    @Test
    fun `legacy resolver preserves unsigned JSON request uri POST behavior`() = runBlocking {
        val requestUrl = URLBuilder("openid4vp://authorize").apply {
            parameters.append("client_id", "legacy-verifier")
            parameters.append("request_uri", "https://verifier.example/request")
            parameters.append("request_uri_method", "post")
        }.build()

        val resolved = AuthorizationRequestResolver.resolve(
            requestUrl = requestUrl,
            unsignedRequestObjectPolicy = AuthorizationRequestResolver.UnsignedRequestObjectPolicy.ALLOW_UNSIGNED,
            enforceFinalRequestObject = false,
        ) { _, _ ->
            AuthorizationRequestResolver.RequestUriFetchResponse(
                status = io.ktor.http.HttpStatusCode.OK,
                contentType = ContentType.Application.Json,
                body = "{\"client_id\":\"legacy-verifier\",\"nonce\":\"legacy-nonce\"}",
                walletNonce = null,
            )
        }

        assertIs<ResolvedAuthorizationRequest.Plain>(resolved)
        assertEquals("legacy-verifier", resolved.authorizationRequest.clientId)
    }

    @Test
    fun `strict resolver requires a nonce-bound signed POST response`() = runBlocking {
        val requestUrl = URLBuilder("openid4vp://authorize").apply {
            parameters.append("client_id", "redirect_uri:https://verifier.example/callback")
            parameters.append("request_uri", "https://verifier.example/request")
            parameters.append("request_uri_method", "post")
        }.build()

        val error = assertFailsWith<IllegalArgumentException> {
            AuthorizationRequestResolver.resolve(
                requestUrl = requestUrl,
                unsignedRequestObjectPolicy = AuthorizationRequestResolver.UnsignedRequestObjectPolicy.ALLOW_UNSIGNED,
            ) { _, _ ->
                AuthorizationRequestResolver.RequestUriFetchResponse(
                    status = io.ktor.http.HttpStatusCode.OK,
                    contentType = ContentType.Application.Json,
                    body = "{}",
                    walletNonce = null,
                )
            }
        }

        assertEquals("request_uri_method=post response is missing the wallet_nonce binding", error.message)
    }

    @Test
    fun `strict resolver rejects request and request uri together`() = runBlocking {
        val requestUrl = URLBuilder("openid4vp://authorize").apply {
            parameters.append("client_id", "verifier2")
            parameters.append("request", unsignedJwt("{\"client_id\":\"verifier2\",\"nonce\":\"n\"}"))
            parameters.append("request_uri", "https://verifier.example/request")
        }.build()

        val error = assertFailsWith<IllegalArgumentException> {
            AuthorizationRequestResolver.resolve(
                requestUrl = requestUrl,
                unsignedRequestObjectPolicy = AuthorizationRequestResolver.UnsignedRequestObjectPolicy.ALLOW_UNSIGNED,
            ) { _, _ -> error("request_uri fetch must not be called") }
        }
        assertEquals("Authorization Request must not contain both request and request_uri", error.message)
    }

    @Test
    fun `strict resolver rejects request uri method without request uri`() = runBlocking {
        val requestUrl = URLBuilder("openid4vp://authorize").apply {
            parameters.append("client_id", "redirect_uri:https://verifier.example/callback")
            parameters.append("request_uri_method", "post")
        }.build()

        val error = assertFailsWith<IllegalArgumentException> {
            AuthorizationRequestResolver.resolve(
                requestUrl = requestUrl,
                unsignedRequestObjectPolicy = AuthorizationRequestResolver.UnsignedRequestObjectPolicy.ALLOW_UNSIGNED,
            ) { _, _ -> error("request_uri fetch must not be called") }
        }
        assertEquals("request_uri_method must not be present without request_uri", error.message)
    }

    @Test
    fun `strict plain request binds redirect uri client id`() = runBlocking {
        val requestUrl = URLBuilder("openid4vp://authorize").apply {
            parameters.append("client_id", "redirect_uri:https://verifier.example/callback")
            parameters.append("response_type", "vp_token")
            parameters.append("response_mode", "fragment")
            parameters.append("redirect_uri", "https://verifier.example/callback")
            parameters.append("nonce", "nonce")
        }.build()

        val resolved = AuthorizationRequestResolver.resolve(
            requestUrl = requestUrl,
            unsignedRequestObjectPolicy = AuthorizationRequestResolver.UnsignedRequestObjectPolicy.REQUIRE_SIGNED,
        ) { _, _ -> error("request_uri fetch should not be called") }
        assertEquals("https://verifier.example/callback", resolved.authorizationRequest.redirectUri)
    }

    @Test
    fun `strict plain request rejects redirect uri mismatch`() = runBlocking {
        val requestUrl = URLBuilder("openid4vp://authorize").apply {
            parameters.append("client_id", "redirect_uri:https://verifier.example/callback")
            parameters.append("response_type", "vp_token")
            parameters.append("response_mode", "fragment")
            parameters.append("redirect_uri", "https://attacker.example/callback")
            parameters.append("nonce", "nonce")
        }.build()

        assertFailsWith<IllegalArgumentException> {
            AuthorizationRequestResolver.resolve(
                requestUrl = requestUrl,
                unsignedRequestObjectPolicy = AuthorizationRequestResolver.UnsignedRequestObjectPolicy.REQUIRE_SIGNED,
            ) { _, _ -> error("request_uri fetch should not be called") }
        }
    }

    @Test
    fun `strict plain request rejects non redirect client identifier prefixes`() = runBlocking {
        listOf("did:jwk:verifier", "did:key:z6Mkverifier").forEach { clientId ->
            val requestUrl = URLBuilder("openid4vp://authorize").apply {
                parameters.append("client_id", clientId)
                parameters.append("response_type", "vp_token")
                parameters.append("response_mode", "fragment")
                parameters.append("redirect_uri", "https://verifier.example/callback")
                parameters.append("nonce", "nonce")
            }.build()

            assertFailsWith<IllegalArgumentException>("plain $clientId request must be rejected") {
                AuthorizationRequestResolver.resolve(
                    requestUrl = requestUrl,
                    unsignedRequestObjectPolicy = AuthorizationRequestResolver.UnsignedRequestObjectPolicy.REQUIRE_SIGNED,
                ) { _, _ -> error("request_uri fetch should not be called") }
            }
        }
    }

    @Test
    fun `request object audience and temporal claims are enforced`() = runBlocking {
        val now = Clock.System.now().epochSeconds
        val cases = listOf(
            "wrong audience" to "{\"client_id\":\"verifier2\",\"nonce\":\"n\",\"aud\":\"https://other.example\"}",
            "expired" to "{\"client_id\":\"verifier2\",\"nonce\":\"n\",\"aud\":\"https://self-issued.me/v2\",\"exp\":${now - 3600}}",
            "future nbf" to "{\"client_id\":\"verifier2\",\"nonce\":\"n\",\"aud\":\"https://self-issued.me/v2\",\"nbf\":${now + 3600}}",
            "malformed exp" to "{\"client_id\":\"verifier2\",\"nonce\":\"n\",\"aud\":\"https://self-issued.me/v2\",\"exp\":\"soon\"}",
        )
        cases.forEach { (_, payload) ->
            val requestUrl = URLBuilder("openid4vp://authorize").apply {
                parameters.append("client_id", "verifier2")
                parameters.append("request", unsignedJwt(payload))
            }.build()
            assertFailsWith<IllegalArgumentException> {
                AuthorizationRequestResolver.resolve(
                    requestUrl = requestUrl,
                    unsignedRequestObjectPolicy = AuthorizationRequestResolver.UnsignedRequestObjectPolicy.ALLOW_UNSIGNED,
                ) { _, _ -> error("request_uri fetch should not be called") }
            }
        }
    }

    @Test
    fun `request object accepts a configured audience`() = runBlocking {
        val requestUrl = URLBuilder("openid4vp://authorize").apply {
            parameters.append("client_id", "verifier2")
            parameters.append(
                "request",
                unsignedJwt("{\"client_id\":\"verifier2\",\"nonce\":\"n\",\"aud\":\"https://audience.example\"}"),
            )
        }.build()

        val resolved = AuthorizationRequestResolver.resolve(
            requestUrl = requestUrl,
            unsignedRequestObjectPolicy = AuthorizationRequestResolver.UnsignedRequestObjectPolicy.ALLOW_UNSIGNED,
            expectedRequestObjectAudience = "https://audience.example",
            fetchRequestUri = { _, _ -> error("request_uri fetch should not be called") },
            trustConfiguration = ClientIdTrustConfiguration(),
        )
        assertEquals("verifier2", resolved.authorizationRequest.clientId)
    }

    @Test
    fun `strict POST rejects unsigned JSON response and mismatched wallet nonce`() = runBlocking {
        val requestUrl = URLBuilder("openid4vp://authorize").apply {
            parameters.append("client_id", "verifier2")
            parameters.append("request_uri", "https://verifier.example/request")
            parameters.append("request_uri_method", "post")
        }.build()

        val unsignedJsonError = assertFailsWith<IllegalArgumentException> {
            AuthorizationRequestResolver.resolve(
                requestUrl = requestUrl,
                unsignedRequestObjectPolicy = AuthorizationRequestResolver.UnsignedRequestObjectPolicy.ALLOW_UNSIGNED,
            ) { _, _ ->
                AuthorizationRequestResolver.RequestUriFetchResponse(
                    status = io.ktor.http.HttpStatusCode.OK,
                    contentType = ContentType.Application.Json,
                    body = "{\"client_id\":\"verifier2\",\"nonce\":\"n\"}",
                    walletNonce = "wallet-nonce",
                )
            }
        }
        assertEquals(
            "Unsigned authorization request not allowed: received application/json from request_uri " +
                "but wallet policy requires signed requests (application/oauth-authz-req+jwt)",
            unsignedJsonError.message,
        )

        val nonceError = assertFailsWith<IllegalArgumentException> {
            AuthorizationRequestResolver.resolve(
                requestUrl = requestUrl,
                unsignedRequestObjectPolicy = AuthorizationRequestResolver.UnsignedRequestObjectPolicy.ALLOW_UNSIGNED,
            ) { _, _ ->
                AuthorizationRequestResolver.RequestUriFetchResponse(
                    status = io.ktor.http.HttpStatusCode.OK,
                    contentType = ContentType.parse("application/oauth-authz-req+jwt"),
                    body = unsignedJwt("{\"client_id\":\"verifier2\",\"nonce\":\"n\",\"aud\":\"https://self-issued.me/v2\",\"wallet_nonce\":\"other\"}"),
                    walletNonce = "wallet-nonce",
                )
            }
        }
        assertEquals("AuthorizationRequest object wallet_nonce mismatch for request_uri_method=post", nonceError.message)
    }

    @Test
    fun `request uri post wallet metadata declares supported response and client id capabilities`() {
        val metadata = Json.parseToJsonElement(
            AuthorizationRequestResolver.buildRequestUriPostWalletMetadata(
                vpFormatsSupported = jsonObjectOf("dc+sd-jwt" to jsonObjectOf()),
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
        val expectedClientIdPrefixes =
            listOf(
                "redirect_uri",
                "decentralized_identifier",
            )
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
    fun `wallet metadata advertises trust-dependent client id schemes only when configured`() {
        val metadata = Json.parseToJsonElement(
            AuthorizationRequestResolver.buildRequestUriPostWalletMetadata(
                vpFormatsSupported = jsonObjectOf(),
                trustConfiguration = ClientIdTrustConfiguration(
                    x509TrustAnchors = listOf(CertificateDer(byteArrayOf(1))),
                    trustedVerifierAttestationIssuers = setOf("did:example:attester"),
                ),
            )
        ).jsonObject

        assertEquals(
            listOf("redirect_uri", "x509_san_dns", "x509_hash", "decentralized_identifier", "verifier_attestation"),
            metadata.getValue("client_id_prefixes_supported").jsonArray.map { it.jsonPrimitive.content },
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
            vpFormatsSupported = jsonObjectOf("dc+sd-jwt" to jsonObjectOf()),
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
              "nonce":"nonce-123",
              "aud":"https://self-issued.me/v2"
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
              "nonce":"nonce-123",
              "aud":"https://self-issued.me/v2"
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
    fun `request object with wrong typ is rejected`() {
        val requestObject = unsignedJwt(
            payloadJson = """{"client_id":"verifier2","nonce":"nonce-123"}""",
            type = "JWT",
        )
        val requestUrl = URLBuilder("openid4vp://authorize").apply {
            parameters.append("client_id", "verifier2")
            parameters.append("request", requestObject)
        }.build()

        assertFailsWith<IllegalArgumentException> {
            runBlocking {
                AuthorizationRequestResolver.resolve(
                    requestUrl,
                    AuthorizationRequestResolver.UnsignedRequestObjectPolicy.ALLOW_UNSIGNED,
                ) { _, _ -> error("request_uri fetch should not be called") }
            }
        }
    }

    @Test
    fun `outer and request object client ids must match`() {
        val requestObject = unsignedJwt("""{"client_id":"inner","nonce":"nonce-123"}""")
        val requestUrl = URLBuilder("openid4vp://authorize").apply {
            parameters.append("client_id", "outer")
            parameters.append("request", requestObject)
        }.build()

        assertFailsWith<IllegalArgumentException> {
            runBlocking {
                AuthorizationRequestResolver.resolve(
                    requestUrl,
                    AuthorizationRequestResolver.UnsignedRequestObjectPolicy.ALLOW_UNSIGNED,
                ) { _, _ -> error("request_uri fetch should not be called") }
            }
        }
    }

    @Test
    fun `pre-registered request object verifies against trusted metadata JWK`() = runBlocking {
        val trustedKey = JWKKey.generate(KeyType.Ed25519)
        val requestObject = signedRequestObject(trustedKey)
        val requestUrl = URLBuilder("openid4vp://authorize").apply {
            parameters.append("client_id", "verifier2")
            parameters.append("request", requestObject)
        }.build()

        val resolved = AuthorizationRequestResolver.resolve(
            requestUrl = requestUrl,
            unsignedRequestObjectPolicy = AuthorizationRequestResolver.UnsignedRequestObjectPolicy.REQUIRE_SIGNED,
            fetchRequestUri = { _, _ -> error("request_uri fetch should not be called") },
            trustConfiguration = ClientIdTrustConfiguration(
                preRegisteredClients = mapOf(
                    "verifier2" to ClientMetadata(
                        jwks = ClientMetadata.Jwks(listOf(trustedKey.getPublicKey().exportJWKObject())),
                    )
                ),
            ),
        )

        assertIs<ResolvedAuthorizationRequest.WithRequestObject>(resolved)
    }

    @Test
    fun `pre-registered request object rejects invalid signature`() = runBlocking {
        val trustedKey = JWKKey.generate(KeyType.Ed25519)
        val attackerKey = JWKKey.generate(KeyType.Ed25519)
        val requestUrl = URLBuilder("openid4vp://authorize").apply {
            parameters.append("client_id", "verifier2")
            parameters.append("request", signedRequestObject(attackerKey))
        }.build()

        val error = assertFailsWith<AuthorizationRequestResolver.SignedAuthorizationRequestValidationException> {
            AuthorizationRequestResolver.resolve(
                requestUrl = requestUrl,
                unsignedRequestObjectPolicy = AuthorizationRequestResolver.UnsignedRequestObjectPolicy.REQUIRE_SIGNED,
                fetchRequestUri = { _, _ -> error("request_uri fetch should not be called") },
                trustConfiguration = ClientIdTrustConfiguration(
                    preRegisteredClients = mapOf(
                        "verifier2" to ClientMetadata(
                            jwks = ClientMetadata.Jwks(listOf(trustedKey.getPublicKey().exportJWKObject())),
                        )
                    ),
                ),
            )
        }

        assertEquals(ClientIdError.InvalidSignature, error.clientIdError)
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

    private fun unsignedJwt(payloadJson: String, type: String = "oauth-authz-req+jwt"): String {
        val header = """{"alg":"none","typ":"$type"}"""
        return listOf(header, payloadJson)
            .joinToString(".") { segment ->
                Base64.getUrlEncoder().withoutPadding().encodeToString(segment.toByteArray())
            } + "."
    }

    private suspend fun signedRequestObject(key: Key): String = key.signJws(
        buildJsonObject {
            put("client_id", "verifier2")
            put("nonce", "nonce-123")
            put("aud", "https://self-issued.me/v2")
        }.toString().encodeToByteArray(),
        mapOf("typ" to JsonPrimitive("oauth-authz-req+jwt")),
    )

    private fun jsonObjectOf(vararg pairs: Pair<String, kotlinx.serialization.json.JsonElement>) =
        kotlinx.serialization.json.JsonObject(mapOf(*pairs))

    private fun HttpRequestData.bodyText(): String =
        (body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
}
