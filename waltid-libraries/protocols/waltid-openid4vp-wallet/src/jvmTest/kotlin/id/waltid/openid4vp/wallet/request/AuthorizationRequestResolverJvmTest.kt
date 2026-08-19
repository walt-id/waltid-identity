@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.waltid.openid4vp.wallet.request

import id.walt.certificate.x509.truststore.InMemoryTrustStore
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.openid4vp.clientidprefix.ClientIdError
import id.walt.openid4vp.clientidprefix.ClientIdTrustConfiguration
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.verifier.openid.models.authorization.RequestUriHttpMethod
import id.walt.webdatafetching.WebDataFetcher
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
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
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AuthorizationRequestResolverJvmTest {

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
                    x509TrustAnchors = InMemoryTrustStore(),
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
        }.toString().encodeToByteArray(),
        mapOf("typ" to JsonPrimitive("oauth-authz-req+jwt")),
    )

    private fun jsonObjectOf(vararg pairs: Pair<String, kotlinx.serialization.json.JsonElement>) =
        kotlinx.serialization.json.JsonObject(mapOf(*pairs))

    private fun HttpRequestData.bodyText(): String =
        (body as OutgoingContent.ByteArrayContent).bytes().decodeToString()

    /**
     * A bare `+` in the query must survive as a literal plus, or every Credential Format Identifier
     * containing one (`dc+sd-jwt`, `vc+sd-jwt`) is mangled into a format that matches nothing. Ktor's
     * [io.ktor.http.Url.parameters] form-decodes `+` to a space, which broke every SD-JWT VC
     * presentation over `request_method=url_query` while leaving `mso_mdoc` unaffected.
     * See AuthorizationRequestResolver.authorizationRequestParameters.
     */
    @Test
    fun `plus in the authorization request query is a literal plus, not a space`() {
        val url = URLBuilder(
            "openid4vp://authorize" +
                    "?client_id=redirect_uri%3Ahttps%3A%2F%2Fverifier.example.com%2Fresponse" +
                    "&client_metadata=%7B%22vp_formats_supported%22%3A%7B%22dc+sd-jwt%22%3A%7B%7D%7D%7D" +
                    "&nonce=abc%20def"
        ).build()

        val parameters = AuthorizationRequestResolver.authorizationRequestParameters(url)

        assertEquals(
            """{"vp_formats_supported":{"dc+sd-jwt":{}}}""",
            parameters["client_metadata"],
            "the format identifier must keep its '+'",
        )
        // Ktor's own parsing is what this works around - confirm the difference is real.
        assertEquals(
            """{"vp_formats_supported":{"dc sd-jwt":{}}}""",
            url.parameters["client_metadata"],
        )
        // A genuinely intended space is percent-encoded and still decodes.
        assertEquals("abc def", parameters["nonce"])
    }

    /**
     * The conformance suite emits `{"alg":"none"}` - no `typ` - for
     * `request_method=request_uri_unsigned`, so an unsigned Request Object without `typ` has to be
     * accepted. A `typ` that is present but wrong must still be refused.
     * See AuthorizationRequestResolver.requireRequestObjectType.
     */
    @Test
    fun `unsigned request object may omit typ but not misstate it`() = runBlocking {
        val clientId = "redirect_uri:https://verifier.example.com/response"
        val claims = buildJsonObject {
            put("client_id", JsonPrimitive(clientId))
            put("response_type", JsonPrimitive("vp_token"))
            put("nonce", JsonPrimitive("n-0S6_WzA2Mj"))
            put("response_uri", JsonPrimitive("https://verifier.example.com/response"))
        }
        val b64 = { value: String ->
            Base64.getUrlEncoder().withoutPadding().encodeToString(value.encodeToByteArray())
        }
        fun plainJwt(header: String) = "${b64(header)}.${b64(Json.encodeToString(claims))}."

        suspend fun resolve(header: String) = AuthorizationRequestResolver.resolve(
            requestUrl = URLBuilder("openid4vp://authorize").apply {
                parameters.append("client_id", clientId)
                parameters.append("request", plainJwt(header))
            }.build(),
            unsignedRequestObjectPolicy = AuthorizationRequestResolver.UnsignedRequestObjectPolicy.ALLOW_UNSIGNED,
            fetchRequestUri = { _, _ -> error("request_uri must not be fetched for an inline request") },
        )

        // The suite's shape: alg=none, no typ.
        assertEquals(clientId, resolve("""{"alg":"none"}""").authorizationRequest.clientId)

        val wrongTyp = assertFails { resolve("""{"alg":"none","typ":"JWT"}""") }
        assertTrue(
            "typ must be" in (wrongTyp.message ?: ""),
            "a wrong typ must still be rejected, was: ${wrongTyp.message}",
        )
    }

    /**
     * `redirect_uri` has no key to sign with - OpenID4VP 1.0 Section 5.9.3 forbids pairing it with a
     * signed request - so an unsigned Request Object under that prefix must be accepted even when the
     * policy is REQUIRE_SIGNED. Refusing it made every `request_uri_unsigned` flow impossible, as that
     * is the only prefix the conformance suite pairs with the request method.
     *
     * The second half is the part that must never regress: every other prefix authenticates the
     * Verifier *through* the signature, so `alg: none` there would let anyone claim the identifier.
     */
    @Test
    fun `unsigned request object is allowed only for prefixes that cannot sign`() = runBlocking {
        suspend fun resolveUnsigned(clientId: String) = AuthorizationRequestResolver.resolve(
            requestUrl = URLBuilder("openid4vp://authorize").apply {
                parameters.append("client_id", clientId)
                parameters.append(
                    "request",
                    unsignedJwt("""{"client_id":"$clientId","nonce":"nonce-123"}"""),
                )
            }.build(),
            unsignedRequestObjectPolicy = AuthorizationRequestResolver.UnsignedRequestObjectPolicy.REQUIRE_SIGNED,
        ) { _, _ -> error("request_uri fetch should not be called for inline request objects") }

        val redirectUriClientId = "redirect_uri:https://verifier.example.com/response"
        assertEquals(
            redirectUriClientId,
            resolveUnsigned(redirectUriClientId).authorizationRequest.clientId,
        )

        // An unsigned request must not be able to impersonate a certificate-authenticated verifier.
        assertFailsWith<AuthorizationRequestResolver.UnsignedAuthorizationRequestNotAllowedException> {
            resolveUnsigned("x509_san_dns:bank.example.com")
        }
        assertFailsWith<AuthorizationRequestResolver.UnsignedAuthorizationRequestNotAllowedException> {
            resolveUnsigned("x509_hash:Uvo3HtuIxuhC92rShpgqcT3YXwrqRxWEviRiA0OZszk")
        }
        assertFailsWith<AuthorizationRequestResolver.UnsignedAuthorizationRequestNotAllowedException> {
            resolveUnsigned("decentralized_identifier:did:web:verifier.example.com")
        }
        Unit
    }

    /**
     * OpenID4VP 1.0 Section 5.9.3: under `redirect_uri` the Client Identifier *is* the response
     * destination, and the Verifier MAY omit the parameter. Both directions matter - deriving the
     * omitted value, and refusing a value that contradicts the Client Identifier, which would
     * otherwise have the wallet post a Presentation to a URI the identifier does not authorise.
     */
    @Test
    fun `redirect_uri client id binds the response destination`() = runBlocking {
        val destination = "https://verifier.example.com/response"
        val clientId = "redirect_uri:$destination"

        suspend fun resolvePlain(vararg extra: Pair<String, String>) = AuthorizationRequestResolver.resolve(
            requestUrl = URLBuilder("openid4vp://authorize").apply {
                parameters.append("client_id", clientId)
                parameters.append("response_type", "vp_token")
                parameters.append("response_mode", "direct_post")
                parameters.append("nonce", "nonce-123")
                extra.forEach { (k, value) -> parameters.append(k, value) }
            }.build(),
            unsignedRequestObjectPolicy = AuthorizationRequestResolver.UnsignedRequestObjectPolicy.REQUIRE_SIGNED,
        ) { _, _ -> error("request_uri fetch should not be called") }

        // Omitted: derived from the client_id.
        assertEquals(destination, resolvePlain().authorizationRequest.responseUri)

        // Stated consistently: kept as-is.
        assertEquals(
            destination,
            resolvePlain("response_uri" to destination).authorizationRequest.responseUri,
        )

        // Contradicting the client_id: refused, so the VP Token cannot be posted elsewhere.
        val mismatch = assertFails { resolvePlain("response_uri" to "https://attacker.example.org/collect") }
        assertTrue(
            "does not match the redirect_uri client_id" in (mismatch.message ?: ""),
            "expected a binding failure, was: ${mismatch.message}",
        )
    }
}
