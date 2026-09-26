package id.walt.itb

import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.verifier.openid.models.authorization.RequestUriHttpMethod
import id.waltid.openid4vp.wallet.request.AuthorizationRequestResolver
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class PresentationProfileTest {
    private val strict = AuthorizationRequestResolver.UnsignedRequestObjectPolicy.REQUIRE_SIGNED
    private val trust = VerifierFixture.trust

    @Test
    fun `CS02 accepts a signed request with the configured verifier trust anchor`() = runTest {
        val jwt = VerifierFixture.sign(payload())
        val result = AuthorizationRequestResolver.resolve(
            requestUrl = inlineRequest(WalletFixtures.CLIENT_ID, jwt),
            unsignedRequestObjectPolicy = strict,
            trustConfiguration = trust,
            fetchRequestUri = { _, _ -> error("Inline request must not use HTTP") },
        )
        assertEquals(WalletFixtures.CLIENT_ID, result.authorizationRequest.clientId)
        assertEquals("nonce", result.authorizationRequest.nonce)
    }

    @Test
    fun `CS02 rejects a signed request without its trust anchor`() = runTest {
        val jwt = VerifierFixture.sign(payload())
        assertFailsWith<AuthorizationRequestResolver.SignedAuthorizationRequestValidationException> {
            AuthorizationRequestResolver.resolve(inlineRequest(WalletFixtures.CLIENT_ID, jwt), strict) { _, _ ->
                error("Inline request must not use HTTP")
            }
        }
    }

    @Test
    fun `CS02 rejects an altered signed request`() = runTest {
        val original = VerifierFixture.sign(payload())
        val parts = original.split('.')
        val alteredPayload = JsonObject(payload() + ("nonce" to JsonPrimitive("altered")))
            .toString().encodeToByteArray().encodeToBase64Url()
        val altered = "${parts[0]}.$alteredPayload.${parts[2]}"
        assertFailsWith<AuthorizationRequestResolver.SignedAuthorizationRequestValidationException> {
            AuthorizationRequestResolver.resolve(
                requestUrl = inlineRequest(WalletFixtures.CLIENT_ID, altered),
                unsignedRequestObjectPolicy = strict,
                trustConfiguration = trust,
                fetchRequestUri = { _, _ -> error("Inline request must not use HTTP") },
            )
        }
    }

    @Test
    fun `CS02 strict profile rejects unsigned redirect-uri request objects`() = runTest {
        val clientId = "redirect_uri:https://verifier.example.com/response"
        val header = """{"alg":"none","typ":"oauth-authz-req+jwt"}""".encodeToByteArray().encodeToBase64Url()
        val body = JsonObject(payload() + ("client_id" to JsonPrimitive(clientId)))
            .toString().encodeToByteArray().encodeToBase64Url()
        // The strict WE BUILD profile rejects unsigned redirect_uri requests.
        assertFailsWith<IllegalArgumentException>("WE BUILD does not permit unsigned requests") {
            AuthorizationRequestResolver.resolve(inlineRequest(clientId, "$header.$body."), strict) { _, _ ->
                error("Inline request must not use HTTP")
            }
        }
    }

    @Test
    fun `CS02 strict profile rejects unsigned JSON returned by request-uri GET`() = runTest {
        val requestUrl = URLBuilder("openid4vp://authorize").apply {
            parameters.append("client_id", WalletFixtures.CLIENT_ID)
            parameters.append("request_uri", "${WalletFixtures.VERIFIER}/request")
        }.build()
        assertFailsWith<IllegalArgumentException>("Unsigned JSON must not bypass the strict policy") {
            AuthorizationRequestResolver.resolve(requestUrl, strict) { _, method ->
                assertTrue(method == null || method == RequestUriHttpMethod.GET)
                AuthorizationRequestResolver.RequestUriFetchResponse(
                    body = payload().toString(), status = HttpStatusCode.OK,
                    contentType = ContentType.Application.Json,
                )
            }
        }
    }

    private fun payload() = buildJsonObject {
        put("client_id", WalletFixtures.CLIENT_ID)
        put("nonce", "nonce")
        put("aud", "https://self-issued.me/v2")
        put("response_type", "vp_token")
        put("response_mode", "direct_post")
        put("response_uri", "${WalletFixtures.VERIFIER}/response")
        put("client_metadata", buildJsonObject {
            put("vp_formats_supported", buildJsonObject {
                put("dc+sd-jwt", buildJsonObject { put("sd-jwt_alg_values", JsonArray(listOf(JsonPrimitive("ES256")))) })
            })
        })
        put("dcql_query", WalletFixtures.query())
    }

    private fun inlineRequest(clientId: String, jwt: String) = URLBuilder("openid4vp://authorize").apply {
        parameters.append("client_id", clientId)
        parameters.append("request", jwt)
    }.build()
}
