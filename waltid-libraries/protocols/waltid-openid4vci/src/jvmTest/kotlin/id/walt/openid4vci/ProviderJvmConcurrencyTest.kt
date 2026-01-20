package id.walt.openid4vci

import id.walt.openid4vci.core.OAuth2ProviderConfig
import id.walt.openid4vci.core.OAuth2Provider
import id.walt.openid4vci.core.buildOAuth2Provider
import id.walt.openid4vci.requests.authorization.AuthorizeRequestResult
import id.walt.openid4vci.requests.token.AccessTokenRequestResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import id.walt.openid4vci.responses.token.AccessResponseResult
import id.walt.openid4vci.responses.authorization.AuthorizationResponseResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProviderJvmConcurrencyTest {

    @Test
    fun `authorization code flow handles real dispatcher concurrency`() = runBlocking {
        val provider = buildOAuth2Provider(defaultConcurrencyTestConfig())
// Uncomment to experiment with unsafe repositories and observe race conditions:
//         val provider = buildOAuth2Provider(defaultConcurrencyTestConfig().copy(authorizationCodeRepository = UnsafeAuthorizationCodeRepository()))

        runConcurrentAuthCodeFlows(
            provider = provider,
            iterations = 20,
            parallelism = 500,
            dispatcher = Dispatchers.Default,
        )
    }
}

internal fun defaultConcurrencyTestConfig(): OAuth2ProviderConfig = createTestConfig()

internal suspend fun runConcurrentAuthCodeFlows(
    provider: OAuth2Provider,
    iterations: Int,
    parallelism: Int,
    dispatcher: CoroutineDispatcher? = null,
) {
    repeat(iterations) { iteration ->
        val accessTokens = coroutineScope {
            (0 until parallelism).map { idx ->
                if (dispatcher != null) {
                    async(dispatcher) { executeAuthorizationCodeFlow(provider, iteration, idx) }
                } else {
                    async { executeAuthorizationCodeFlow(provider, iteration, idx) }
                }
            }.awaitAll()
        }

        assertEquals(accessTokens.size, accessTokens.toSet().size)
        assertTrue(accessTokens.all { it.startsWith("access-") })
    }
}

internal suspend fun executeAuthorizationCodeFlow(
    provider: OAuth2Provider,
    iteration: Int,
    idx: Int,
): String {
    val expectedSubject = "subject-$iteration-$idx"
    val clientId = "concurrent-client-$iteration-$idx"
    val state = "state-$iteration-$idx"
    val issuerId = "issuer-$iteration"
    val authorizeParams = mapOf(
        "response_type" to listOf("code"),
        "client_id" to listOf(clientId),
        "redirect_uri" to listOf("https://client.example/callback"),
        "scope" to listOf("my_scope"),
        "state" to listOf(state),
    )

    val authorizeRequest = provider.createAuthorizationRequest(authorizeParams)
    require(authorizeRequest is AuthorizeRequestResult.Success)
    val authorizeReqWithIssuer = authorizeRequest.request.withIssuer(issuerId)

    val authorizeResponse = provider.createAuthorizationResponse(
        authorizeReqWithIssuer,
        DefaultSession(subject = expectedSubject),
    )
    require(authorizeResponse is AuthorizationResponseResult.Success)
    val code = authorizeResponse.response.code

    val AccessTokenRequestResult = provider.createAccessRequest(
        mapOf(
            "grant_type" to listOf(GrantType.AuthorizationCode.value),
            "client_id" to listOf(clientId),
            "code" to listOf(code),
            "redirect_uri" to listOf("https://client.example/callback"),
        ),
    )
    require(AccessTokenRequestResult is AccessTokenRequestResult.Success)

    val accessRequestWithIssuer = AccessTokenRequestResult.request.withIssuer(issuerId)

    val accessResponse = provider.createAccessResponse(accessRequestWithIssuer)
    require(accessResponse is AccessResponseResult.Success)

    val updatedRequest = accessResponse.request
    assertEquals(expectedSubject, updatedRequest.session?.subject)
    assertEquals(clientId, updatedRequest.client.id)
    assertEquals(state, authorizeReqWithIssuer.state)

    val token = accessResponse.response.accessToken
    assertTrue(token.startsWith("access-$clientId"))

    return token
}
