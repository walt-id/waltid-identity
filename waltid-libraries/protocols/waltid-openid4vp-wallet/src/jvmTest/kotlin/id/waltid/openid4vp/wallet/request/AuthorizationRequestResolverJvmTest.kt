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
}
