package id.waltid.openid4vp.wallet.request

import io.ktor.http.URLBuilder
import kotlinx.coroutines.runBlocking
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class AuthorizationRequestResolverJvmTest {

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

    private fun unsignedJwt(payloadJson: String, type: String = "oauth-authz-req+jwt"): String {
        val header = """{"alg":"none","typ":"$type"}"""
        return listOf(header, payloadJson)
            .joinToString(".") { segment ->
                Base64.getUrlEncoder().withoutPadding().encodeToString(segment.toByteArray())
            } + "."
    }
}
